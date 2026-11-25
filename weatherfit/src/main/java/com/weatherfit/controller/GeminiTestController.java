
package com.weatherfit.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.weatherfit.service.GeminiService;

@RestController
public class GeminiTestController {

    private final GeminiService geminiService;

    public GeminiTestController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @GetMapping("/gemini-test")
    public String testGemini() {

        String prompt = "안녕! 너는 어떤 인공지능이니? "
                + "한국어로 간단하게 자기소개해 줘.";

        return geminiService.askGemini(prompt);
    }
}
