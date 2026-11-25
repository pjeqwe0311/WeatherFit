
package com.weatherfit.service;

import java.net.URI;
import java.time.LocalDate;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class WeatherService {

    private final RestClient restClient;

    public WeatherService() {
        this.restClient = RestClient.create();
    }

    // 서울의 현재 날씨 정보 가져오기
    public Map<String, Object> getSeoulWeather() {

        String url = "https://api.open-meteo.com/v1/forecast"
                + "?latitude=37.5665"
                + "&longitude=126.9780"
                + "&current=temperature_2m%2Crelative_humidity_2m%2Capparent_temperature%2Cweather_code%2Cwind_speed_10m"
                + "&timezone=auto";

        Map<String, Object> response = restClient.get()
                .uri(URI.create(url))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        return response;
    }
 // 서울의 시간별 날씨 예보 가져오기
    public Map<String, Object> getSeoulHourlyWeather(LocalDate date) {

        String url = "https://api.open-meteo.com/v1/forecast"
                + "?latitude=37.5665"
                + "&longitude=126.9780"
                + "&hourly=temperature_2m"
                + "%2Capparent_temperature"
                + "%2Crelative_humidity_2m"
                + "%2Cprecipitation_probability"
                + "%2Cprecipitation"
                + "%2Cweather_code"
                + "%2Cwind_speed_10m"
                + "&timezone=Asia%2FSeoul"
                + "&forecast_days=16";

        Map<String, Object> response = restClient.get()
                .uri(URI.create(url))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        return response;
    }

    // 날씨 코드를 한글 날씨 상태로 변환
    public String getWeatherDescription(int weatherCode) {

        switch (weatherCode) {

            case 0:
                return "맑음";

            case 1:
                return "대체로 맑음";

            case 2:
                return "부분적으로 흐림";

            case 3:
                return "흐림";

            case 45:
            case 48:
                return "안개";

            case 51:
            case 53:
            case 55:
                return "이슬비";

            case 56:
            case 57:
                return "어는 이슬비";

            case 61:
            case 63:
            case 65:
                return "비";

            case 66:
            case 67:
                return "어는 비";

            case 71:
            case 73:
            case 75:
            case 77:
                return "눈";

            case 80:
            case 81:
            case 82:
                return "소나기";

            case 85:
            case 86:
                return "눈 소나기";

            case 95:
            case 96:
            case 97:
            case 99:
                return "뇌우";

            default:
                return "날씨 정보 없음";
        }
    }
}
