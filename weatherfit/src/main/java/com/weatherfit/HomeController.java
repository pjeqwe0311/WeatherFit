
package com.weatherfit;

import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.weatherfit.service.WeatherService;

@Controller
public class HomeController {

    private final WeatherService weatherService;

    public HomeController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/")
    public String home(Model model) {

        // 서울의 날씨 정보 가져오기
        Map<String, Object> response = weatherService.getSeoulWeather();

        // 현재 날씨 정보 추출
        Map<String, Object> current =
                (Map<String, Object>) response.get("current");

        // 날씨 코드 가져오기
        int weatherCode = ((Number) current.get("weather_code")).intValue();

        // 날씨 코드를 한글로 변환
        String weatherDescription =
                weatherService.getWeatherDescription(weatherCode);

        // HTML 화면에 정보 전달
        model.addAttribute("weather", current);
        model.addAttribute("weatherDescription", weatherDescription);

        return "index";
    }
}
