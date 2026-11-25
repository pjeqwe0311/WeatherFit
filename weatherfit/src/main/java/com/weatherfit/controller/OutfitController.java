
package com.weatherfit.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientResponseException;

import com.weatherfit.entity.Clothing;
import com.weatherfit.entity.OutfitRecommendation;
import com.weatherfit.service.OutfitRecommendationService;

@Controller
public class OutfitController {

    private final OutfitRecommendationService outfitService;

    public OutfitController(OutfitRecommendationService outfitService) {
        this.outfitService = outfitService;
    }

    // 외출 정보 입력 화면
    @GetMapping("/outfit/form")
    public String showOutfitForm() {
        return "outfit-form";
    }

    // 외출 정보 입력 및 AI 코디 추천 실행
    @PostMapping("/outfit/recommend")
    public String receiveOutingInfo(
            @RequestParam("outingDate") String outingDate,
            @RequestParam("startTime") String startTime,
            @RequestParam("endTime") String endTime,
            @RequestParam("activityType") String activityType,
            Model model) {

        try {

            // 1. 입력한 날짜와 시간 변환
            LocalDate date = LocalDate.parse(outingDate);
            LocalTime start = LocalTime.parse(startTime);
            LocalTime end = LocalTime.parse(endTime);

            // 2. 서버 측 날짜 유효성 검사
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            LocalDate latestDate = today.plusDays(15);

            if (date.isBefore(today) || date.isAfter(latestDate)) {

                model.addAttribute(
                        "errorMessage",
                        "외출 날짜는 오늘부터 15일 이내로 선택해 주세요."
                );

                return "outfit-form";
            }

            // 3. 귀가 시간이 외출 시간보다 늦은지 확인
            if (!end.isAfter(start)) {

                model.addAttribute(
                        "errorMessage",
                        "귀가 예정 시간은 외출 시간보다 늦게 설정해 주세요."
                );

                return "outfit-form";
            }

            // 4. AI 코디 추천 실행
            OutfitRecommendation recommendation =
                    outfitService.recommendOutfit(
                            date,
                            start,
                            end,
                            activityType
                    );

            // 5. 추천된 옷의 실제 정보 가져오기
            Clothing top =
                    outfitService.getClothingById(recommendation.getTopId());

            Clothing bottom =
                    outfitService.getClothingById(recommendation.getBottomId());

            Clothing outer = recommendation.getOuterId() != null
                    ? outfitService.getClothingById(recommendation.getOuterId())
                    : null;

            Clothing shoes = recommendation.getShoesId() != null
                    ? outfitService.getClothingById(recommendation.getShoesId())
                    : null;

            // 6. 추천 결과를 화면에 전달
            model.addAttribute("top", top);
            model.addAttribute("bottom", bottom);
            model.addAttribute("outer", outer);
            model.addAttribute("shoes", shoes);
            model.addAttribute("reason", recommendation.getReason());

            model.addAttribute("outingDate", date);
            model.addAttribute("startTime", start);
            model.addAttribute("endTime", end);
            model.addAttribute("activityType", activityType);

            return "outfit-result";

        } catch (IllegalStateException e) {

            // 옷장에 필요한 의류가 없거나 추천 결과에 문제가 있는 경우
            model.addAttribute(
                    "errorMessage",
                    e.getMessage() != null
                            ? e.getMessage()
                            : "코디 추천에 필요한 옷을 확인해 주세요."
            );

            return "outfit-error";

        } catch (RestClientResponseException e) {

            // 외부 API 오류
            System.err.println(
                    "외부 API 오류: " + e.getStatusCode()
            );

            model.addAttribute(
                    "errorMessage",
                    "날씨 또는 AI 서비스가 일시적으로 원활하지 않습니다. 잠시 후 다시 시도해 주세요."
            );

            return "outfit-error";

        } catch (Exception e) {

            // 그 외 오류
            e.printStackTrace();

            model.addAttribute(
                    "errorMessage",
                    "코디 추천 중 문제가 발생했습니다. 날짜와 시간을 확인하거나 잠시 후 다시 시도해 주세요."
            );

            return "outfit-error";
        }
    }

    // 기존 GET 방식 추천 기능 유지
    @GetMapping("/outfit/recommend")
    public String recommendOutfit(Model model) {

        try {

            OutfitRecommendation recommendation =
                    outfitService.recommendOutfit();

            // 추천된 옷의 실제 정보 가져오기
            Clothing top =
                    outfitService.getClothingById(recommendation.getTopId());

            Clothing bottom =
                    outfitService.getClothingById(recommendation.getBottomId());

            Clothing outer = recommendation.getOuterId() != null
                    ? outfitService.getClothingById(recommendation.getOuterId())
                    : null;

            Clothing shoes = recommendation.getShoesId() != null
                    ? outfitService.getClothingById(recommendation.getShoesId())
                    : null;

            // 추천 결과를 화면에 전달
            model.addAttribute("top", top);
            model.addAttribute("bottom", bottom);
            model.addAttribute("outer", outer);
            model.addAttribute("shoes", shoes);
            model.addAttribute("reason", recommendation.getReason());

            return "outfit-result";

        } catch (IllegalStateException e) {

            model.addAttribute(
                    "errorMessage",
                    e.getMessage() != null
                            ? e.getMessage()
                            : "코디 추천에 필요한 옷을 확인해 주세요."
            );

            return "outfit-error";

        } catch (RestClientResponseException e) {

            System.err.println(
                    "Gemini API 오류: " + e.getStatusCode()
            );

            model.addAttribute(
                    "errorMessage",
                    "현재 AI 코디 추천 서비스가 일시적으로 원활하지 않습니다. 잠시 후 다시 시도해 주세요."
            );

            return "outfit-error";

        } catch (Exception e) {

            e.printStackTrace();

            model.addAttribute(
                    "errorMessage",
                    "코디 추천 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요."
            );

            return "outfit-error";
        }
    }

    // 여름 코디 테스트용
    @GetMapping("/outfit/test-summer")
    public String testSummerOutfit(Model model) {

        try {

            OutfitRecommendation recommendation =
                    outfitService.recommendSummerOutfitForTest("야외 활동");

            Clothing top =
                    outfitService.getClothingById(recommendation.getTopId());

            Clothing bottom =
                    outfitService.getClothingById(recommendation.getBottomId());

            Clothing outer = recommendation.getOuterId() != null
                    ? outfitService.getClothingById(recommendation.getOuterId())
                    : null;

            Clothing shoes = recommendation.getShoesId() != null
                    ? outfitService.getClothingById(recommendation.getShoesId())
                    : null;

            model.addAttribute("top", top);
            model.addAttribute("bottom", bottom);
            model.addAttribute("outer", outer);
            model.addAttribute("shoes", shoes);
            model.addAttribute("reason", recommendation.getReason());

            return "outfit-result";

        } catch (IllegalStateException e) {
            model.addAttribute(
                    "errorMessage",
                    e.getMessage() != null
                            ? e.getMessage()
                            : "코디 추천에 필요한 옷을 확인해 주세요."
            );

            return "outfit-error";

        } catch (Exception e) {

            e.printStackTrace();

            model.addAttribute(
                    "errorMessage",
                    "여름 코디 테스트 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요."
            );

            return "outfit-error";
        }
    }
}
