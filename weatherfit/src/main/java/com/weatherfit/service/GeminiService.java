
package com.weatherfit.service;

import tools.jackson.databind.json.JsonMapper;

import com.weatherfit.entity.OutfitRecommendation;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class GeminiService {

    private final JsonMapper jsonMapper;
    private final RestClient restClient;
    private final String apiKey;

    // 최대 재시도 횟수
    private static final int MAX_RETRIES = 2;

    // Gemini API 연결 준비
    public GeminiService(
            @Value("${gemini.api-key}") String apiKey,
            JsonMapper jsonMapper) {

        this.jsonMapper = jsonMapper;
        this.apiKey = apiKey;

        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
    }

    // Gemini에게 질문하고 답변 받기
    public String askGemini(String prompt) {

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of(
                                "parts", List.of(
                                        Map.of("text", prompt)
                                )
                        )
                )
        );

        Map<String, Object> response = callGemini(requestBody);

        return extractText(response, "Gemini가 답변을 반환하지 않았습니다.");
    }

    // AI에게 JSON 형식으로 코디 추천 요청하기
    public String askGeminiForOutfit(String prompt) {

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of(
                                "parts", List.of(
                                        Map.of("text", prompt)
                                )
                        )
                ),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json"
                )
        );

        Map<String, Object> response = callGemini(requestBody);

        return extractText(response, "Gemini가 코디 추천을 반환하지 않았습니다.");
    }

    // Gemini API 공통 호출 및 재시도 처리
    private Map<String, Object> callGemini(Map<String, Object> requestBody) {

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {

            try {

                return restClient.post()
                        .uri("/v1beta/models/gemini-3.5-flash-lite:generateContent")
                        .header("x-goog-api-key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            } catch (RestClientResponseException e) {

                int statusCode = e.getStatusCode().value();

                // 재시도 가능한 오류인지 확인
                boolean retryable =
                        statusCode == 429
                        || statusCode == 500
                        || statusCode == 502
                        || statusCode == 503
                        || statusCode == 504;

                // 재시도 대상이 아니거나 횟수를 모두 사용한 경우
                if (!retryable || attempt == MAX_RETRIES) {
                    throw e;
                }

                // 재시도 전 대기 시간 (1초, 2초)
                long waitTime = 1000L * (attempt + 1);

                System.out.println(
                        "Gemini API 오류 발생: " + statusCode
                        + " / " + (attempt + 1) + "번째 재시도 예정"
                );

                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "Gemini API 재시도가 중단되었습니다.",
                            interruptedException
                    );
                }
            }
        }

        throw new IllegalStateException("Gemini API 호출에 실패했습니다.");
    }

    // Gemini 응답에서 실제 텍스트 추출
    private String extractText(
            Map<String, Object> response,
            String errorMessage) {

        if (response == null
                || !(response.get("candidates") instanceof List<?> candidates)
                || candidates.isEmpty()) {
            throw new IllegalStateException(errorMessage);
        }

        Map<?, ?> candidate = (Map<?, ?>) candidates.get(0);

        if (!(candidate.get("content") instanceof Map<?, ?> content)
                || !(content.get("parts") instanceof List<?> parts)
                || parts.isEmpty()) {
            throw new IllegalStateException("Gemini 응답에 답변 내용이 없습니다.");
        }

        Map<?, ?> firstPart = (Map<?, ?>) parts.get(0);

        Object text = firstPart.get("text");

        if (text == null) {
            throw new IllegalStateException("Gemini 응답에 텍스트가 없습니다.");
        }

        return String.valueOf(text);
    }

    // Gemini의 JSON 답변을 OutfitRecommendation 객체로 변환
    public OutfitRecommendation convertToRecommendation(String json) {

        return jsonMapper.readValue(
                json,
                OutfitRecommendation.class
        );
    }
}
