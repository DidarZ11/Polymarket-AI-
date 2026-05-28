package org.example.pet1.service;

import lombok.extern.slf4j.Slf4j;
import org.example.pet1.entity.Market;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Сервис для анализа рынков через Claude API (Anthropic).
 */
@Slf4j
@Service
public class ClaudeService {

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String CLAUDE_MODEL = "claude-haiku-4-5-20251001";

    private final RestTemplate restTemplate;
    private final String apiKey;

    public ClaudeService(RestTemplate restTemplate,
                         @Value("${anthropic.api.key}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    /**
     * Отправляет вопрос рынка в Claude и возвращает краткий анализ.
     *
     * @param market рынок для анализа
     * @return текст анализа от Claude
     */
    public String analyzeMarket(Market market) {
        String prompt = String.format(
                "Analyze this prediction market: %s. " +
                "Current probability: %s. Volume: %.2f. " +
                "Give a brief analysis in 3 sentences: what affects this probability, " +
                "is it over/underpriced, what to watch.",
                market.getQuestion(),
                market.getOutcomePrices(),
                market.getVolume() != null ? market.getVolume() : 0.0
        );

        // Формируем тело запроса по спецификации Anthropic Messages API
        Map<String, Object> requestBody = Map.of(
                "model", CLAUDE_MODEL,
                "max_tokens", 300,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            log.debug("Отправляем запрос в Claude для рынка: {}", market.getMarketId());

            ResponseEntity<Map> response = restTemplate.exchange(
                    CLAUDE_API_URL, HttpMethod.POST, request, Map.class
            );

            // Извлекаем текст из ответа: response.content[0].text
            Map body = response.getBody();
            if (body != null && body.containsKey("content")) {
                List contentList = (List) body.get("content");
                if (!contentList.isEmpty()) {
                    Map firstBlock = (Map) contentList.get(0);
                    return (String) firstBlock.get("text");
                }
            }

            log.warn("Claude вернул пустой ответ для рынка {}", market.getMarketId());
            return "Анализ недоступен";

        } catch (Exception e) {
            log.error("Ошибка при запросе к Claude API для рынка {}: {}", market.getMarketId(), e.getMessage());
            return "Ошибка анализа: " + e.getMessage();
        }
    }
}
