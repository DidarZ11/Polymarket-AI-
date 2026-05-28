package org.example.pet1.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.pet1.dto.MarketDto;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

/**
 * HTTP-клиент для получения данных о рынках с Polymarket Gamma API.
 * Gamma API возвращает прямой JSON-массив (без обёртки data/next_cursor).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolymarketClient {

    /** URL Gamma API: 20 активных рынков */
    private static final String GAMMA_API_URL =
            "https://gamma-api.polymarket.com/markets?limit=20&active=true";

    private final RestTemplate restTemplate;

    /**
     * Запрашивает активные рынки с Gamma API.
     * Ответ — прямой JSON-массив объектов рынков.
     *
     * @return список DTO рынков, или пустой список при ошибке
     */
    public List<MarketDto> fetchMarkets() {
        try {
            log.info("Запрашиваю рынки с Polymarket Gamma API: {}", GAMMA_API_URL);

            // Gamma API возвращает JSON-массив напрямую, без обёртки
            ResponseEntity<List<MarketDto>> response = restTemplate.exchange(
                    GAMMA_API_URL,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<MarketDto>>() {}
            );

            if (response.getBody() == null) {
                log.warn("Polymarket Gamma API вернул пустой ответ");
                return Collections.emptyList();
            }

            log.info("Получено рынков с Gamma API: {}", response.getBody().size());
            return response.getBody();

        } catch (RestClientException e) {
            log.error("Ошибка при запросе к Polymarket Gamma API: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}