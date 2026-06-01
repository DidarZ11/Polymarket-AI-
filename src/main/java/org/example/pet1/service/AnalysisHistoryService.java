package org.example.pet1.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.pet1.dto.MarketAnalysisDto;
import org.example.pet1.entity.MarketAnalysis;
import org.example.pet1.repository.MarketAnalysisRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Сервис чтения истории AI-анализов.
 * Конвертирует JSON-строки из БД обратно в списки для фронта.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisHistoryService {

    private final MarketAnalysisRepository analysisRepository;
    private final ObjectMapper objectMapper;

    /** История анализов конкретного рынка, новые — первыми */
    public List<MarketAnalysisDto> getMarketAnalyses(Long marketId) {
        return analysisRepository.findByMarketIdOrderByCreatedAtDesc(marketId)
                .stream().map(this::toDto).toList();
    }

    /** Последние 10 анализов по всем рынкам */
    public List<MarketAnalysisDto> getRecentAnalyses() {
        return analysisRepository.findTop10ByOrderByCreatedAtDesc()
                .stream().map(this::toDto).toList();
    }

    /** Конвертирует сущность анализа в DTO, десериализуя JSON-поля */
    private MarketAnalysisDto toDto(MarketAnalysis a) {
        return MarketAnalysisDto.builder()
                .id(a.getId())
                .marketId(a.getMarket().getId())
                .marketQuestion(a.getMarket().getQuestion())
                .recommendation(a.getRecommendation())
                .confidence(a.getConfidence())
                .priceAssessment(a.getPriceAssessment())
                .summary(a.getSummary())
                .bullishFactors(parseJson(a.getBullishFactors()))
                .bearishFactors(parseJson(a.getBearishFactors()))
                .keyRisks(parseJson(a.getKeyRisks()))
                .newsUsed(parseJson(a.getNewsUsed()))
                .createdAt(a.getCreatedAt())
                .build();
    }

    /** Десериализует JSON-строку в список, при ошибке — пустой список */
    private List<String> parseJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Не удалось десериализовать JSON-поле анализа: {}", json);
            return Collections.emptyList();
        }
    }
}
