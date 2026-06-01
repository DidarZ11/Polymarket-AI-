package org.example.pet1.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сервис для получения свежих новостей через NewsAPI.
 */
@Slf4j
@Service
public class NewsService {

    private static final String NEWS_API_URL = "https://newsapi.org/v2/everything";

    private final RestTemplate restTemplate;
    private final String apiKey;

    public NewsService(RestTemplate restTemplate,
                       @Value("${newsapi.key:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    /**
     * Возвращает список заголовков свежих новостей по запросу.
     * Если ключ не настроен или произошла ошибка — возвращает пустой список.
     *
     * @param query поисковый запрос (первые 5 слов вопроса рынка)
     * @return до 5 заголовков новостей
     */
    public List<String> getNews(String query) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("NEWS_API_KEY не задан — новости недоступны");
            return Collections.emptyList();
        }

        try {
            URI uri = UriComponentsBuilder
                    .fromHttpUrl(NEWS_API_URL)
                    .queryParam("q", query)
                    .queryParam("pageSize", 5)
                    .queryParam("sortBy", "publishedAt")
                    .queryParam("apiKey", apiKey)
                    .encode()
                    .build()
                    .toUri();

            log.debug("Запрашиваю новости по запросу: {}", query);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(uri, Map.class);

            if (response == null || !"ok".equals(response.get("status"))) {
                log.warn("NewsAPI вернул статус: {}", response != null ? response.get("status") : "null");
                return Collections.emptyList();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> articles = (List<Map<String, Object>>) response.get("articles");

            if (articles == null || articles.isEmpty()) {
                return Collections.emptyList();
            }

            // Извлекаем заголовки, пропускаем статьи без title
            List<String> headlines = articles.stream()
                    .map(a -> (String) a.get("title"))
                    .filter(t -> t != null && !t.isBlank() && !"[Removed]".equals(t))
                    .collect(Collectors.toList());

            log.debug("Получено {} новостей для запроса: {}", headlines.size(), query);
            return headlines;

        } catch (Exception e) {
            log.error("Ошибка при запросе к NewsAPI: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
