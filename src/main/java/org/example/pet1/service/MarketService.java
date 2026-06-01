package org.example.pet1.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.pet1.client.PolymarketClient;
import org.example.pet1.dto.MarketDto;
import org.example.pet1.dto.MarketResponse;
import org.example.pet1.entity.Market;
import org.example.pet1.repository.MarketAnalysisRepository;
import org.example.pet1.repository.MarketRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Сервисный слой: бизнес-логика работы с рынками.
 * Связывает HTTP-клиент Polymarket с базой данных.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketService {

    private static final int BATCH_SIZE    = 500;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int FETCH_THREADS = 10;
    private static final int PAGES_PER_ROUND = 10;

    private final MarketRepository marketRepository;
    private final MarketAnalysisRepository marketAnalysisRepository;
    private final PolymarketClient polymarketClient;
    private final ObjectMapper objectMapper;

    /**
     * Универсальный метод получения рынков с фильтрацией и сортировкой.
     * Использует отдельные именованные методы репозитория — без сложных CASE WHEN.
     *
     * Приоритет:
     *   1. search    → поиск по тексту, сортировка по volume DESC
     *   2. minProb/maxProb → фильтр по вероятности, сортировка по probability DESC
     *   3. sortBy=probability → все рынки, probability DESC
     *   4. sortBy=endDate     → все рынки, endDate ASC (ближайшие первые)
     *   5. default            → все рынки, volume DESC
     */
    public Page<MarketResponse> getMarkets(int page, int size, String sortBy, String sortDir,
                                            Double minProb, Double maxProb,
                                            String search, String category) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));
        String q = (search == null || search.isBlank()) ? null : search;

        Page<Market> result;

        if (q != null) {
            // Поиск по тексту
            result = marketRepository.findByQuestionContainingIgnoreCaseOrderByVolumeDesc(q, pageable);

        } else if (minProb != null || maxProb != null) {
            // Фильтр по диапазону вероятности
            double min = minProb != null ? minProb : 0.0;
            double max = maxProb != null ? maxProb : 100.0;
            result = marketRepository.findByProbabilityYesBetweenOrderByProbabilityYesDesc(min, max, pageable);

        } else if ("probability".equals(sortBy)) {
            result = marketRepository.findAllByOrderByProbabilityYesDesc(pageable);

        } else if ("endDate".equals(sortBy)) {
            result = marketRepository.findAllByOrderByEndDateAsc(pageable);

        } else {
            // Default: volume DESC
            result = marketRepository.findAllOrderByVolumeDesc(pageable);
        }

        return result.map(this::toResponse);
    }

    /** Возвращает все рынки из базы данных */
    public List<Market> getAllMarkets() {
        return marketRepository.findAll();
    }

    /** Возвращает рынок по внутреннему id */
    public Optional<Market> getMarketById(Long id) {
        return marketRepository.findById(id);
    }

    /** Возвращает только активные рынки */
    public List<Market> getActiveMarkets() {
        return marketRepository.findByActive(true);
    }

    /** Возвращает рынки по категории */
    public List<Market> getMarketsByCategory(String category) {
        return marketRepository.findByCategory(category);
    }

    /** Возвращает количество рынков в базе данных */
    public long countMarkets() {
        return marketRepository.count();
    }

    /**
     * Возвращает глобальную статистику по всей базе.
     * avgProbability — среднее по first element outcomePrices, умноженное на 100.
     */
    @Cacheable("stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalMarkets",  marketRepository.count());
        stats.put("activeMarkets", marketRepository.countActive());
        stats.put("totalVolume",   marketRepository.sumVolume());
        stats.put("avgProbability", marketRepository.avgProbabilityYes());
        return stats;
    }

    /** Плановая синхронизация каждые 6 часов, сбрасывает кэш статистики */
    @Scheduled(cron = "0 0 */6 * * *")
    @CacheEvict(value = "stats", allEntries = true)
    public void scheduledSync() {
        log.info("Плановая синхронизация рынков (каждые 6 часов)...");
        syncMarkets();
    }

    /**
     * Параллельная загрузка + batch upsert рынков через нативный SQL.
     * История анализов (market_analyses) не затрагивается.
     *
     * @return количество сохранённых/обновлённых рынков
     */
    @Transactional
    public int syncMarkets() {
        long start = System.currentTimeMillis();
        log.info("=== НАЧАЛО СИНХРОНИЗАЦИИ ===");
        log.info("@Transactional из: org.springframework.transaction.annotation.Transactional");

        // Проверка записи в БД через простой save() до основной синхронизации
        testDbWrite();

        List<MarketDto> dtos = fetchAllPagesParallel();
        if (dtos.isEmpty()) {
            log.warn("API не вернул ни одного рынка");
            return 0;
        }

        log.info("Загружено {} рынков с API, начинаю upsert батчами по {}...", dtos.size(), BATCH_SIZE);

        int saved = 0;
        int batchNum = 0;

        for (int i = 0; i < dtos.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, dtos.size());
            List<MarketDto> batch = dtos.subList(i, end);
            batchNum++;
            log.info("Сохраняю батч #{} из {} рынков (обработано {}/{})", batchNum, batch.size(), i, dtos.size());
            try {
                for (MarketDto dto : batch) {
                    if (dto.getId() == null) continue;
                    marketRepository.upsertMarket(
                            dto.getId(),
                            dto.getQuestion(),
                            dto.getOutcomePrices(),
                            dto.getVolume(),
                            dto.getEndDate(),
                            dto.getActive(),
                            dto.getImage(),
                            dto.getCategory(),
                            parseProbabilityYes(dto.getOutcomePrices())
                    );
                    saved++;
                }
                log.info("Батч #{} сохранён успешно ({} рынков, итого {})", batchNum, batch.size(), saved);
            } catch (Exception e) {
                log.error("Ошибка при сохранении батча #{} (offset {}): {}", batchNum, i, e.getMessage(), e);
            }
        }

        long seconds = (System.currentTimeMillis() - start) / 1000;
        log.info("Синхронизировано {} рынков за {} секунд", saved, seconds);
        log.info("=== КОНЕЦ СИНХРОНИЗАЦИИ, сохранено: {} ===", marketRepository.count());
        return saved;
    }

    /** Тестовая запись в БД через save() для проверки соединения и прав на запись */
    private void testDbWrite() {
        try {
            log.info("[DB-TEST] Проверка записи через marketRepository.save()...");
            Market test = new Market();
            test.setMarketId("__test_" + System.currentTimeMillis() + "__");
            test.setQuestion("DB connection test — safe to delete");
            test.setActive(false);
            Market persisted = marketRepository.saveAndFlush(test);
            marketRepository.deleteById(persisted.getId());
            log.info("[DB-TEST] Запись в БД работает: save+delete успешно (id={})", persisted.getId());
        } catch (Exception e) {
            log.error("[DB-TEST] Ошибка записи в БД: {}", e.getMessage(), e);
        }
    }

    /**
     * Загружает все страницы Polymarket API параллельно.
     * Запускает по PAGES_PER_ROUND страниц за раз, останавливается когда весь раунд пустой.
     */
    private List<MarketDto> fetchAllPagesParallel() {
        List<MarketDto> all = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(FETCH_THREADS);
        int pageSize = polymarketClient.getPageSize();
        int batchStart = 0;
        boolean hasMore = true;

        try {
            while (hasMore) {
                List<CompletableFuture<List<MarketDto>>> futures = new ArrayList<>();
                for (int i = 0; i < PAGES_PER_ROUND; i++) {
                    final int offset = (batchStart + i) * pageSize;
                    futures.add(CompletableFuture.supplyAsync(
                            () -> polymarketClient.fetchPage(offset), executor));
                }

                hasMore = false;
                for (CompletableFuture<List<MarketDto>> f : futures) {
                    List<MarketDto> page = f.join();
                    if (!page.isEmpty()) {
                        all.addAll(page);
                        if (page.size() >= pageSize) hasMore = true;
                    }
                }
                batchStart += PAGES_PER_ROUND;
            }
        } finally {
            executor.shutdown();
        }

        log.info("Параллельная загрузка завершена: {} рынков", all.size());
        return all;
    }

    /** Конвертирует сущность Market в DTO ответа, берёт probabilityYes из хранимой колонки */
    private MarketResponse toResponse(Market market) {
        return MarketResponse.builder()
                .id(market.getId())
                .marketId(market.getMarketId())
                .question(market.getQuestion())
                .outcomePrices(market.getOutcomePrices())
                .probabilityYes(market.getProbabilityYes())
                .volume(market.getVolume())
                .endDate(market.getEndDate())
                .active(market.getActive())
                .imageUrl(market.getImageUrl())
                .category(market.getCategory())
                .createdAt(market.getCreatedAt())
                .build();
    }

    /** Парсит outcomePrices через ObjectMapper, возвращает вероятность YES в процентах */
    private Double parseProbabilityYes(String outcomePrices) {
        if (outcomePrices == null || outcomePrices.isBlank()) return null;
        try {
            List<String> prices = objectMapper.readValue(outcomePrices, new TypeReference<List<String>>() {});
            if (prices.isEmpty()) return null;
            return Math.round(Double.parseDouble(prices.get(0)) * 10000.0) / 100.0;
        } catch (Exception e) {
            log.warn("Не удалось распарсить outcomePrices: {}", outcomePrices);
            return null;
        }
    }

}
