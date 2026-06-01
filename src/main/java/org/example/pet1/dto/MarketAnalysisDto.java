package org.example.pet1.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO истории анализа рынка.
 * Содержит marketQuestion, чтобы фронт мог показать название рынка
 * без дополнительного запроса.
 */
@Data
@Builder
public class MarketAnalysisDto {

    private Long id;

    /** Внутренний id рынка */
    private Long marketId;

    /** Вопрос рынка — для отображения на фронте */
    private String marketQuestion;

    private String recommendation;
    private Integer confidence;
    private String priceAssessment;
    private String summary;
    private List<String> bullishFactors;
    private List<String> bearishFactors;
    private List<String> keyRisks;
    private List<String> newsUsed;
    private LocalDateTime createdAt;
}
