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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Сервисный слой: бизнес-логика работы с рынками.
 * Связывает HTTP-клиент Polymarket с базой данных.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketService {

    /** Размер батча при сохранении рынков */
    private static final int BATCH_SIZE = 100;

    /** Максимальный разрешённый размер страницы */
    private static final int MAX_PAGE_SIZE = 100;

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
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalMarkets",  marketRepository.count());
        stats.put("activeMarkets", marketRepository.countActive());
        stats.put("totalVolume",   marketRepository.sumVolume());
        stats.put("avgProbability", marketRepository.avgProbabilityYes());
        return stats;
    }

    /**
     * Upsert-синхронизация рынков с Polymarket Gamma API.
     * Новые рынки — вставляются, существующие — обновляются.
     * История анализов (market_analyses) не затрагивается.
     *
     * @return количество сохранённых/обновлённых рынков
     */
    @Transactional
    public int syncMarkets() {
        log.info("Синхронизация рынков с Polymarket Gamma API...");

        List<MarketDto> dtos = polymarketClient.fetchMarkets();
        if (dtos.isEmpty()) {
            log.warn("API не вернул ни одного рынка");
            return 0;
        }

        List<Market> toSave = new ArrayList<>(dtos.size());
        for (MarketDto dto : dtos) {
            if (dto.getId() == null) continue;
            // Upsert: обновляем существующий рынок или создаём новый
            Market market = marketRepository.findByMarketId(dto.getId())
                    .orElseGet(Market::new);
            applyDto(market, dto);
            toSave.add(market);
        }

        int saved = 0;
        for (int i = 0; i < toSave.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, toSave.size());
            marketRepository.saveAll(toSave.subList(i, end));
            saved += end - i;
            log.debug("Upsert батч: {}/{}", saved, toSave.size());
        }

        log.info("Синхронизировано {} рынков (история анализов сохранена)", saved);
        return saved;
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

    /** Применяет поля DTO к сущности (новой или существующей) */
    private void applyDto(Market market, MarketDto dto) {
        market.setMarketId(dto.getId());
        market.setQuestion(dto.getQuestion());
        market.setOutcomePrices(dto.getOutcomePrices());
        market.setProbabilityYes(parseProbabilityYes(dto.getOutcomePrices()));
        market.setVolume(dto.getVolume());
        market.setEndDate(dto.getEndDate());
        market.setActive(dto.getActive());
        market.setImageUrl(dto.getImage());
        market.setCategory(dto.getCategory());
    }
}
