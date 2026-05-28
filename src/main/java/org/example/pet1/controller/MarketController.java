package org.example.pet1.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.pet1.entity.Market;
import org.example.pet1.service.ClaudeService;
import org.example.pet1.service.MarketService;
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

    /**
     * GET /api/markets
     * Возвращает список рынков из БД.
     * Параметр ?active=true вернёт только активные рынки.
     */
    @GetMapping
    public ResponseEntity<List<Market>> getMarkets(
            @RequestParam(required = false) Boolean active) {

        log.debug("GET /api/markets, active={}", active);

        List<Market> markets = Boolean.TRUE.equals(active)
                ? marketService.getActiveMarkets()
                : marketService.getAllMarkets();

        return ResponseEntity.ok(markets);
    }

    /**
     * GET /api/markets/{id}/analyze
     * Запрашивает AI-анализ рынка через Claude API.
     */
    @GetMapping("/{id}/analyze")
    public ResponseEntity<Map<String, Object>> analyzeMarket(@PathVariable Long id) {
        log.info("GET /api/markets/{}/analyze", id);

        Optional<Market> marketOpt = marketService.getMarketById(id);
        if (marketOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Market market = marketOpt.get();
        String analysis = claudeService.analyzeMarket(market);

        return ResponseEntity.ok(Map.of(
                "marketId", market.getMarketId(),
                "question", market.getQuestion(),
                "analysis", analysis
        ));
    }

    /**
     * POST /api/markets/sync
     * Запускает синхронизацию данных с Polymarket Gamma API.
     * Загружает 20 активных рынков и сохраняет их в PostgreSQL.
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncMarkets() {
        log.info("POST /api/markets/sync — запуск синхронизации");

        int syncedCount = marketService.syncMarkets();

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "synced", syncedCount,
                "message", "Сохранено рынков: " + syncedCount
        ));
    }
}
