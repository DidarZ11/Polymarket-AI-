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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * HTTP-клиент для получения данных о рынках с Polymarket Gamma API.
 * Загружает все рынки постранично (offset + limit).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolymarketClient {

    /** Базовый URL Gamma API */
    private static final String GAMMA_API_BASE = "https://gamma-api.polymarket.com/markets";

    /** Размер одной страницы при пагинации */
    private static final int PAGE_SIZE = 100;

    private final RestTemplate restTemplate;

    /**
     * Загружает все рынки с Polymarket Gamma API постранично.
     * Цикл продолжается, пока API возвращает непустые страницы.
     *
     * @return полный список DTO рынков, или пустой список при ошибке
     */
    public List<MarketDto> fetchMarkets() {
        List<MarketDto> all = new ArrayList<>();
        int offset = 0;
        int pageNum = 1;

        while (true) {
            String url = GAMMA_API_BASE + "?limit=" + PAGE_SIZE + "&offset=" + offset;
            try {
                ResponseEntity<List<MarketDto>> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<List<MarketDto>>() {}
                );

                List<MarketDto> page = response.getBody();

                // Пустой ответ — все страницы загружены
                if (page == null || page.isEmpty()) {
                    break;
                }

                all.addAll(page);
                log.info("Страница {}: загружено {} рынков, всего {}", pageNum, page.size(), all.size());

                // Страница неполная — это последняя
                if (page.size() < PAGE_SIZE) {
                    break;
                }

                offset += PAGE_SIZE;
                pageNum++;

            } catch (RestClientException e) {
                // API ограничивает offset (422 offset too large) — прерываем, возвращаем собранное
                log.warn("Пагинация остановлена на странице {}: {}", pageNum, e.getMessage());
                break;
            }
        }

        log.info("Итого загружено рынков с Gamma API: {}", all.size());
        return all;
    }
}
