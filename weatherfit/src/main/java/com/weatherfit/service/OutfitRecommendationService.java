
package com.weatherfit.service;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.weatherfit.entity.Clothing;
import com.weatherfit.entity.OutfitRecommendation;
import com.weatherfit.repository.ClothingRepository;

@Service
public class OutfitRecommendationService {

    private final ClothingRepository clothingRepository;
    private final WeatherService weatherService;
    private final GeminiService geminiService;

    public OutfitRecommendationService(
            ClothingRepository clothingRepository,
            WeatherService weatherService,
            GeminiService geminiService) {

        this.clothingRepository = clothingRepository;
        this.weatherService = weatherService;
        this.geminiService = geminiService;
    }

    // 날씨와 옷장 정보를 이용해 AI 코디 추천
    public OutfitRecommendation recommendOutfit() {

        // 1. 옷장에 등록된 옷 가져오기
        List<Clothing> clothes = clothingRepository.findAll();

        ensureRequiredCategoriesExist(clothes);

        // 2. 현재 날씨 가져오기
        Map<String, Object> response = weatherService.getSeoulWeather();

        Map<String, Object> current =
                (Map<String, Object>) response.get("current");

        // 3. AI에게 전달할 날씨 및 옷장 정보 만들기
        StringBuilder prompt = new StringBuilder();

        prompt.append("너는 패션 코디 전문가야.\n");
        prompt.append("아래 날씨와 옷장 정보를 분석해서 가장 적합한 코디를 추천해 줘.\n");
        prompt.append("반드시 옷장에 실제로 존재하는 ID만 선택해.\n");
        prompt.append("상의와 하의는 각각 하나씩 선택해.\n");
        prompt.append("아우터와 신발은 날씨에 필요할 때만 선택해.\n");
        prompt.append("아우터나 신발이 필요하지 않거나 적절한 옷이 없으면 해당 ID는 null로 반환해.\n");
        prompt.append("각 ID는 옷장에 등록된 실제 옷의 ID만 사용해.\n");
        prompt.append("옷의 종류에 맞는 항목을 선택해.\n");
        prompt.append("결과는 JSON 형식으로만 반환해.\n");
        prompt.append("JSON 형식: {\"topId\": 1, \"bottomId\": 2, \"outerId\": null, \"shoesId\": 4, \"reason\": \"추천 이유\"}\n\n");

        prompt.append("[현재 날씨]\n");
        prompt.append("기온: ").append(current.get("temperature_2m")).append("도\n");
        prompt.append("체감온도: ").append(current.get("apparent_temperature")).append("도\n");
        prompt.append("습도: ").append(current.get("relative_humidity_2m")).append("%\n");
        prompt.append("풍속: ").append(current.get("wind_speed_10m")).append("km/h\n\n");

        prompt.append("[사용자 옷장]\n");

        for (Clothing clothing : clothes) {
            prompt.append("ID: ").append(clothing.getId())
                    .append(", 이름: ").append(clothing.getName())
                    .append(", 종류: ").append(clothing.getCategory())
                    .append(", 색상: ").append(clothing.getColor())
                    .append(", 두께: ").append(clothing.getThickness())
                    .append("\n");
        }

        // 4. Gemini에게 코디 추천 요청
        String aiResponse =
                geminiService.askGeminiForOutfit(prompt.toString());

        // 5. AI의 JSON 답변을 Java 객체로 변환
        OutfitRecommendation recommendation =
                geminiService.convertToRecommendation(aiResponse);

        // 6. AI 추천 결과 검증
        validateRecommendation(recommendation, clothes);

        // 7. 추천 결과 반환
        return recommendation;
    }

    // 사용자가 입력한 외출 시간대의 날씨를 기준으로 AI 코디 추천
    public OutfitRecommendation recommendOutfit(
            LocalDate outingDate,
            LocalTime startTime,
            LocalTime endTime,
            String activityType) {

        // 1. 옷장에 등록된 옷 가져오기
        List<Clothing> clothes = clothingRepository.findAll();

        ensureRequiredCategoriesExist(clothes);

        // 2. 선택한 날짜의 시간별 날씨 가져오기
        Map<String, Object> response =
                weatherService.getSeoulHourlyWeather(outingDate);

        Map<String, Object> hourly =
                (Map<String, Object>) response.get("hourly");

        if (hourly == null) {
            throw new IllegalStateException(
                    "시간별 날씨 정보를 가져오지 못했습니다.");
        }

        List<String> times = (List<String>) hourly.get("time");
        List<Double> temperatures =
                (List<Double>) hourly.get("temperature_2m");
        List<Double> apparentTemperatures =
                (List<Double>) hourly.get("apparent_temperature");
        List<Double> humidities =
                (List<Double>) hourly.get("relative_humidity_2m");
        List<Double> precipitationProbabilities =
                (List<Double>) hourly.get("precipitation_probability");
        List<Double> precipitations =
                (List<Double>) hourly.get("precipitation");
        List<Integer> weatherCodes =
                (List<Integer>) hourly.get("weather_code");
        List<Double> windSpeeds =
                (List<Double>) hourly.get("wind_speed_10m");

        // 3. 외출 시간에 해당하는 날씨만 추출
        LocalDateTime outingStart =
                LocalDateTime.of(outingDate, startTime);

        LocalDateTime outingEnd =
                LocalDateTime.of(outingDate, endTime);

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

        StringBuilder weatherInfo = new StringBuilder();

        boolean foundWeather = false;

        // 외출 시간대와 겹치는 날씨 예보 추출
        for (int i = 0; i < times.size(); i++) {

            LocalDateTime forecastTime =
                    LocalDateTime.parse(times.get(i), formatter);

            if (!forecastTime.isBefore(outingStart)
                    && !forecastTime.isAfter(outingEnd)) {

                foundWeather = true;

                appendWeatherInfo(
                        weatherInfo,
                        i,
                        times,
                        temperatures,
                        apparentTemperatures,
                        humidities,
                        precipitationProbabilities,
                        precipitations,
                        weatherCodes,
                        windSpeeds
                );
            }
        }

        // 외출 시간대에 해당하는 예보가 하나도 없다면
        // 외출 시작 시간과 가장 가까운 예보를 가져온다.
        if (!foundWeather) {

            int closestIndex = -1;
            long minDifference = Long.MAX_VALUE;

            for (int i = 0; i < times.size(); i++) {

                LocalDateTime forecastTime =
                        LocalDateTime.parse(times.get(i), formatter);

                long difference = Math.abs(
                        java.time.Duration.between(
                                outingStart,
                                forecastTime
                        ).toMinutes()
                );

                if (difference < minDifference) {
                    minDifference = difference;
                    closestIndex = i;
                }
            }

            if (closestIndex == -1) {
                throw new IllegalStateException(
                        "날씨 예보를 가져오지 못했습니다."
                );
            }

            appendWeatherInfo(
                    weatherInfo,
                    closestIndex,
                    times,
                    temperatures,
                    apparentTemperatures,
                    humidities,
                    precipitationProbabilities,
                    precipitations,
                    weatherCodes,
                    windSpeeds
            );
        }

        // 4. AI에게 전달할 프롬프트 만들기
        StringBuilder prompt = new StringBuilder();

        prompt.append("너는 패션 코디 전문가야.\n");
        prompt.append("사용자의 외출 시간대 날씨와 옷장을 분석해서 코디를 추천해 줘.\n");
        prompt.append("반드시 옷장에 실제로 존재하는 ID만 선택해.\n");
        prompt.append("상의와 하의는 각각 하나씩 선택해.\n");
        prompt.append("아우터와 신발은 외출 시간대의 날씨와 활동 유형을 고려해 선택해.\n");
        prompt.append("아우터나 신발이 필요하지 않거나 적절한 옷이 없으면 해당 ID는 null로 반환해.\n");
        prompt.append("각 ID는 옷장에 등록된 실제 옷의 ID만 사용해.\n");
        prompt.append("옷의 종류에 맞는 항목을 선택해.\n");
        prompt.append("결과는 JSON 형식으로만 반환해.\n");
        prompt.append("JSON 형식: {\"topId\": 1, \"bottomId\": 2, \"outerId\": null, \"shoesId\": 4, \"reason\": \"추천 이유\"}\n\n");

        prompt.append("[외출 정보]\n");
        prompt.append("날짜: ").append(outingDate).append("\n");
        prompt.append("외출 시작: ").append(startTime).append("\n");
        prompt.append("귀가 예정: ").append(endTime).append("\n");
        prompt.append("활동 유형: ").append(activityType).append("\n\n");

        prompt.append("[외출 시간대 날씨 예보]\n");
        prompt.append(weatherInfo).append("\n\n");

        prompt.append("[사용자 옷장]\n");

        for (Clothing clothing : clothes) {
            prompt.append("ID: ").append(clothing.getId())
                    .append(", 이름: ").append(clothing.getName())
                    .append(", 종류: ").append(clothing.getCategory())
                    .append(", 색상: ").append(clothing.getColor())
                    .append(", 두께: ").append(clothing.getThickness())
                    .append("\n");
        }

        // 5. Gemini에게 코디 추천 요청
        String aiResponse =
                geminiService.askGeminiForOutfit(prompt.toString());

        // 6. AI 응답을 객체로 변환
        OutfitRecommendation recommendation =
                geminiService.convertToRecommendation(aiResponse);

        // 7. AI 추천 결과 검증
        validateRecommendation(recommendation, clothes);

        return recommendation;
    }

    // 날씨 정보를 문자열에 추가하는 공통 메서드
    private void appendWeatherInfo(
            StringBuilder weatherInfo,
            int i,
            List<String> times,
            List<Double> temperatures,
            List<Double> apparentTemperatures,
            List<Double> humidities,
            List<Double> precipitationProbabilities,
            List<Double> precipitations,
            List<Integer> weatherCodes,
            List<Double> windSpeeds) {

        weatherInfo.append("\n시간: ").append(times.get(i));
        weatherInfo.append(", 기온: ").append(temperatures.get(i)).append("도");
        weatherInfo.append(", 체감온도: ").append(apparentTemperatures.get(i)).append("도");
        weatherInfo.append(", 습도: ").append(humidities.get(i)).append("%");
        weatherInfo.append(", 강수확률: ").append(precipitationProbabilities.get(i)).append("%");
        weatherInfo.append(", 강수량: ").append(precipitations.get(i)).append("mm");
        weatherInfo.append(", 날씨: ")
                .append(weatherService.getWeatherDescription(weatherCodes.get(i)));
        weatherInfo.append(", 풍속: ").append(windSpeeds.get(i)).append("km/h");
    }

    // 여름 날씨 테스트용 코디 추천
    public OutfitRecommendation recommendSummerOutfitForTest(String activityType) {

        List<Clothing> clothes = clothingRepository.findAll();

        ensureRequiredCategoriesExist(clothes);

        StringBuilder prompt = new StringBuilder();

        prompt.append("너는 패션 코디 전문가야.\n");
        prompt.append("아래 여름 날씨와 옷장 정보를 분석해서 코디를 추천해 줘.\n");
        prompt.append("반드시 옷장에 실제로 존재하는 ID만 선택해.\n");
        prompt.append("상의와 하의는 각각 하나씩 선택해.\n");
        prompt.append("아우터와 신발은 날씨와 활동 유형을 고려해 선택해.\n");
        prompt.append("더운 날씨에는 아우터가 필요하지 않다면 outerId를 null로 반환해.\n");
        prompt.append("적절한 신발이 없다면 shoesId를 null로 반환해.\n");
        prompt.append("각 ID는 옷장에 등록된 실제 옷의 ID만 사용해.\n");
        prompt.append("결과는 JSON 형식으로만 반환해.\n");
        prompt.append("JSON 형식: {\"topId\": 1, \"bottomId\": 2, \"outerId\": null, \"shoesId\": 4, \"reason\": \"추천 이유\"}\n\n");

        prompt.append("[테스트용 여름 날씨]\n");
        prompt.append("기온: 32도\n");
        prompt.append("체감온도: 36도\n");
        prompt.append("습도: 75%\n");
        prompt.append("강수확률: 10%\n");
        prompt.append("강수량: 0mm\n");
        prompt.append("날씨: 맑음\n");
        prompt.append("풍속: 8km/h\n\n");

        prompt.append("[활동 유형]\n");
        prompt.append(activityType).append("\n\n");

        prompt.append("[사용자 옷장]\n");

        for (Clothing clothing : clothes) {
            prompt.append("ID: ").append(clothing.getId())
                    .append(", 이름: ").append(clothing.getName())
                    .append(", 종류: ").append(clothing.getCategory())
                    .append(", 색상: ").append(clothing.getColor())
                    .append(", 두께: ").append(clothing.getThickness())
                    .append("\n");
        }

        String aiResponse =
                geminiService.askGeminiForOutfit(prompt.toString());

        OutfitRecommendation recommendation =
                geminiService.convertToRecommendation(aiResponse);

        validateRecommendation(recommendation, clothes);

        return recommendation;
    }

    // 상의와 하의가 옷장에 등록되어 있는지 사전 확인
    private void ensureRequiredCategoriesExist(List<Clothing> clothes) {

        if (clothes == null || clothes.isEmpty()) {
            throw new IllegalStateException(
                    "옷장에 등록된 옷이 없습니다. 먼저 옷을 등록해 주세요."
            );
        }

        boolean hasTop = clothes.stream()
                .anyMatch(c -> "상의".equals(c.getCategory()));

        boolean hasBottom = clothes.stream()
                .anyMatch(c -> "하의".equals(c.getCategory()));

        if (!hasTop && !hasBottom) {
            throw new IllegalStateException(
                    "상의와 하의가 등록되어 있지 않습니다. 코디 추천을 위해 상의와 하의를 먼저 등록해 주세요."
            );
        }

        if (!hasTop) {
            throw new IllegalStateException(
                    "등록된 상의가 없습니다. 상의를 먼저 등록해 주세요."
            );
        }

        if (!hasBottom) {
            throw new IllegalStateException(
                    "등록된 하의가 없습니다. 하의를 먼저 등록해 주세요."
            );
        }
    }

    // AI가 반환한 옷의 ID와 카테고리가 올바른지 검증
    private void validateRecommendation(
            OutfitRecommendation recommendation,
            List<Clothing> clothes) {

        if (recommendation == null) {
            throw new IllegalStateException(
                    "AI 추천 결과를 확인할 수 없습니다. 다시 시도해 주세요."
            );
        }

        // 상의 검증
        Clothing top = findClothingById(
                clothes,
                recommendation.getTopId()
        );

        if (top == null || !"상의".equals(top.getCategory())) {
            throw new IllegalStateException(
                    "AI가 올바르지 않은 상의를 선택했습니다. 다시 시도해 주세요."
            );
        }

        // 하의 검증
        Clothing bottom = findClothingById(
                clothes,
                recommendation.getBottomId()
        );

        if (bottom == null || !"하의".equals(bottom.getCategory())) {
            throw new IllegalStateException(
                    "AI가 올바르지 않은 하의를 선택했습니다. 다시 시도해 주세요."
            );
        }

        // 아우터는 선택된 경우에만 검증
        if (recommendation.getOuterId() != null) {

            Clothing outer = findClothingById(
                    clothes,
                    recommendation.getOuterId()
            );

            if (outer == null || !"아우터".equals(outer.getCategory())) {
                throw new IllegalStateException(
                        "AI가 올바르지 않은 아우터를 선택했습니다. 다시 시도해 주세요."
                );
            }
        }

        // 신발은 선택된 경우에만 검증
        if (recommendation.getShoesId() != null) {

            Clothing shoes = findClothingById(
                    clothes,
                    recommendation.getShoesId()
            );

            if (shoes == null || !"신발".equals(shoes.getCategory())) {
                throw new IllegalStateException(
                        "AI가 올바르지 않은 신발을 선택했습니다. 다시 시도해 주세요."
                );
            }
        }
    }

    // ID로 옷장에 등록된 옷 찾기
    private Clothing findClothingById(
            List<Clothing> clothes,
            Long id) {

        if (id == null) {
            return null;
        }

        return clothes.stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    // 추천된 옷의 실제 정보 가져오기
    public Clothing getClothingById(Long id) {

        return clothingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "옷장에 해당하는 옷이 없습니다. ID: " + id));
    }
}
