package org.example.pet1.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.pet1.dto.AnalysisResponse;
import org.example.pet1.dto.MarketAnalysisDto;
import org.example.pet1.dto.MarketResponse;
import org.example.pet1.entity.Market;
import org.example.pet1.service.AnalysisHistoryService;
import org.example.pet1.service.ClaudeService;
import org.example.pet1.service.MarketService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST-контроллер для работы с рынками Polymarket.
 * Все эндпоинты доступны по префиксу /api/markets
 */
@Slf4j
@RestController
@RequestMapping("/api/markets")
@RequiredArgsConstructor
public class MarketController {

    private final MarketService marketService;
    private final ClaudeService claudeService;
    private final AnalysisHistoryService analysisHistoryService;
    private final ObjectMapper objectMapper;

    /**
     * GET /api/markets — универсальный эндпоинт с фильтрацией и сортировкой.
     *
     * Параметры:
     *   page     — номер страницы (default: 0)
     *   size     — размер страницы (default: 20, max: 100)
     *   sortBy   — поле сортировки: volume | probability | endDate (default: volume)
     *   sortDir  — направление: asc | desc (default: desc)
     *   search   — поиск по тексту вопроса (необязательный)
     *   category — фильтр по категории (необязательный)
     *   minProb  — минимальная вероятность YES в % (необязательный)
     *   maxProb  — максимальная вероятность YES в % (необязательный)
     *
     * Примеры:
     *   GET /api/markets?sortBy=probability&sortDir=desc&minProb=60
     *   GET /api/markets?sortBy=endDate&sortDir=asc&search=NBA
     *   GET /api/markets?sortBy=volume&sortDir=asc&page=1&size=50
     */
    @GetMapping
    public Page<MarketResponse> getMarkets(
            @RequestParam(defaultValue = "0")        int    page,
            @RequestParam(defaultValue = "20")       int    size,
            @RequestParam(defaultValue = "volume")   String sortBy,
            @RequestParam(defaultValue = "desc")     String sortDir,
            @RequestParam(required = false)          Double minProb,
            @RequestParam(required = false)          Double maxProb,
            @RequestParam(required = false)          String search,
            @RequestParam(required = false)          String category) {

        log.debug("GET /api/markets page={} size={} sortBy={} sortDir={} search={} category={} minProb={} maxProb={}",
                page, size, sortBy, sortDir, search, category, minProb, maxProb);

        return marketService.getMarkets(page, size, sortBy, sortDir, minProb, maxProb, search, category);
    }

    /**
     * GET /api/markets/stats
     * Глобальная статистика: всего рынков, активных, суммарный объём, средняя вероятность YES.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        log.debug("GET /api/markets/stats");
        return ResponseEntity.ok(marketService.getStats());
    }

    /**
     * GET /api/markets/count
     * Возвращает количество рынков в базе данных.
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> getCount() {
        long count = marketService.countMarkets();
        log.debug("GET /api/markets/count = {}", count);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * GET /api/markets/{id}/analyze
     * AI-анализ рынка с учётом свежих новостей. Возвращает структурированный AnalysisResponse.
     */
    @GetMapping("/{id}/analyze")
    public ResponseEntity<AnalysisResponse> analyzeMarket(@PathVariable Long id) {
        log.info("GET /api/markets/{}/analyze", id);

        Optional<Market> marketOpt = marketService.getMarketById(id);
        if (marketOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AnalysisResponse analysis = claudeService.analyzeMarket(marketOpt.get());
        return ResponseEntity.ok(analysis);
    }

    /**
     * GET /api/markets/{id}/analyses
     * История всех AI-анализов для конкретного рынка, новые — первыми.
     */
    @GetMapping("/{id}/analyses")
    public ResponseEntity<List<MarketAnalysisDto>> getAnalysisHistory(@PathVariable Long id) {
        log.debug("GET /api/markets/{}/analyses", id);
        return ResponseEntity.ok(analysisHistoryService.getMarketAnalyses(id));
    }

    /**
     * POST /api/markets/sync
     * Полная пересинхронизация: очищает таблицу и загружает все рынки с API.
     */
    @CacheEvict(value = "stats", allEntries = true)
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncMarkets() {
        log.info("POST /api/markets/sync — запуск синхронизации");

        int syncedCount = marketService.syncMarkets();

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "synced", syncedCount,
                "message", "Синхронизировано рынков: " + syncedCount
        ));
    }
}
