package org.example.pet1.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.pet1.dto.AnalysisResponse;
import org.example.pet1.entity.Market;
import org.example.pet1.entity.MarketAnalysis;
import org.example.pet1.repository.MarketAnalysisRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Сервис AI-анализа рынков через Claude API с учётом свежих новостей.
 */
@Slf4j
@Service
public class ClaudeService {

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String CLAUDE_MODEL    = "claude-haiku-4-5-20251001";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final NewsService newsService;
    private final MarketAnalysisRepository analysisRepository;
    private final String apiKey;

    public ClaudeService(RestTemplate restTemplate,
                         ObjectMapper objectMapper,
                         NewsService newsService,
                         MarketAnalysisRepository analysisRepository,
                         @Value("${anthropic.api.key}") String apiKey) {
        this.restTemplate       = restTemplate;
        this.objectMapper       = objectMapper;
        this.newsService        = newsService;
        this.analysisRepository = analysisRepository;
        this.apiKey             = apiKey;
    }

    /**
     * Анализирует рынок с учётом свежих новостей и возвращает структурированный ответ.
     *
     * @param market рынок для анализа
     * @return структурированный анализ от Claude
     */
    public AnalysisResponse analyzeMarket(Market market) {
        // Формируем поисковый запрос: первые 5 слов вопроса рынка
        String newsQuery = buildNewsQuery(market.getQuestion());
        List<String> news = newsService.getNews(newsQuery);

        // Вычисляем вероятность YES из outcomePrices
        double probabilityYes = parseProbability(market.getOutcomePrices());

        // Считаем дней до закрытия рынка
        long daysUntilClose = calcDaysUntilClose(market.getEndDate());

        String prompt = buildPrompt(market, probabilityYes, daysUntilClose, news);

        String rawJson = callClaude(prompt, market.getMarketId());
        if (rawJson == null) {
            return fallback("Claude API недоступен");
        }

        AnalysisResponse result = parseResponse(rawJson, market.getMarketId());
        saveAnalysis(market, result);
        return result;
    }

    /** Берёт первые 5 слов вопроса для поиска новостей */
    private String buildNewsQuery(String question) {
        if (question == null || question.isBlank()) return "prediction market";
        String[] words = question.split("\\s+");
        return String.join(" ", Arrays.copyOf(words, Math.min(5, words.length)));
    }

    /** Парсит первый элемент outcomePrices в проценты */
    private double parseProbability(String outcomePrices) {
        if (outcomePrices == null || outcomePrices.isBlank()) return 50.0;
        try {
            List<String> prices = objectMapper.readValue(outcomePrices, new TypeReference<List<String>>() {});
            if (!prices.isEmpty()) return Math.round(Double.parseDouble(prices.get(0)) * 1000.0) / 10.0;
        } catch (Exception ignored) {}
        return 50.0;
    }

    /** Считает количество дней до закрытия рынка */
    private long calcDaysUntilClose(String endDate) {
        if (endDate == null || endDate.isBlank()) return 0;
        try {
            ZonedDateTime end = ZonedDateTime.parse(endDate);
            long days = ChronoUnit.DAYS.between(ZonedDateTime.now(), end);
            return Math.max(days, 0);
        } catch (Exception ignored) {
            return 0;
        }
    }

    /** Формирует промпт с данными рынка и новостями */
    private String buildPrompt(Market market, double probability, long daysUntilClose, List<String> news) {
        StringBuilder newsBlock = new StringBuilder();
        if (news.isEmpty()) {
            newsBlock.append("No recent news available.");
        } else {
            for (int i = 0; i < news.size(); i++) {
                newsBlock.append(i + 1).append(". ").append(news.get(i)).append("\n");
            }
        }

        return String.format("""
                You are a prediction market analyst. Analyze this market and respond ONLY with valid JSON, no markdown, no explanation.

                Market: %s
                Current YES probability: %.1f%%
                Volume: $%.0f
                Days until close: %d

                Recent news:
                %s

                Respond with this exact JSON structure:
                {
                  "recommendation": "BUY_YES or BUY_NO or SKIP",
                  "confidence": 0-100,
                  "priceAssessment": "UNDERPRICED or OVERPRICED or FAIR",
                  "summary": "2-3 sentence analysis",
                  "bullishFactors": ["factor1", "factor2"],
                  "bearishFactors": ["factor1", "factor2"],
                  "keyRisks": ["risk1", "risk2"],
                  "newsUsed": ["headline1", "headline2"]
                }""",
                market.getQuestion(),
                probability,
                market.getVolume() != null ? market.getVolume() : 0.0,
                daysUntilClose,
                newsBlock.toString().trim()
        );
    }

    /** Отправляет промпт в Claude и возвращает текст ответа */
    private String callClaude(String prompt, String marketId) {
        Map<String, Object> requestBody = Map.of(
                "model", CLAUDE_MODEL,
                "max_tokens", 800,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        try {
            log.debug("Отправляем запрос в Claude для рынка: {}", marketId);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = restTemplate.exchange(
                    CLAUDE_API_URL, HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers), Map.class
            ).getBody();

            if (body == null) return null;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");

            if (content == null || content.isEmpty()) return null;

            return (String) content.get(0).get("text");

        } catch (Exception e) {
            log.error("Ошибка при запросе к Claude API для рынка {}: {}", marketId, e.getMessage());
            return null;
        }
    }

    /** Парсит JSON-ответ Claude в AnalysisResponse */
    private AnalysisResponse parseResponse(String rawText, String marketId) {
        try {
            // Вырезаем JSON из возможных markdown-блоков
            String json = rawText.trim();
            if (json.contains("```")) {
                json = json.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();
            }
            // Берём от первой { до последней }
            int start = json.indexOf('{');
            int end   = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }

            return objectMapper.readValue(json, AnalysisResponse.class);

        } catch (Exception e) {
            log.warn("Не удалось распарсить JSON от Claude для рынка {}: {}", marketId, e.getMessage());
            log.debug("Сырой ответ Claude: {}", rawText);
            return fallback("Не удалось разобрать ответ Claude");
        }
    }

    /** Сохраняет результат анализа в БД, сериализуя списки в JSON */
    private void saveAnalysis(Market market, AnalysisResponse r) {
        try {
            MarketAnalysis entity = MarketAnalysis.builder()
                    .market(market)
                    .recommendation(r.getRecommendation())
                    .confidence(r.getConfidence())
                    .priceAssessment(r.getPriceAssessment())
                    .summary(r.getSummary())
                    .bullishFactors(toJson(r.getBullishFactors()))
                    .bearishFactors(toJson(r.getBearishFactors()))
                    .keyRisks(toJson(r.getKeyRisks()))
                    .newsUsed(toJson(r.getNewsUsed()))
                    .build();
            analysisRepository.save(entity);
            log.debug("Анализ рынка {} сохранён в БД", market.getMarketId());
        } catch (Exception e) {
            log.error("Не удалось сохранить анализ рынка {}: {}", market.getMarketId(), e.getMessage());
        }
    }

    /** Сериализует список в JSON-строку для хранения в TEXT-колонке */
    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** Возвращает объект-заглушку при ошибке */
    private AnalysisResponse fallback(String reason) {
        return AnalysisResponse.builder()
                .recommendation("SKIP")
                .confidence(0)
                .priceAssessment("FAIR")
                .summary("Анализ недоступен: " + reason)
                .bullishFactors(List.of())
                .bearishFactors(List.of())
                .keyRisks(List.of(reason))
                .newsUsed(List.of())
                .build();
    }
}
