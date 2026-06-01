package org.example.pet1.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO ответа AI-анализа рынка.
 * Десериализуется из JSON-ответа Claude.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalysisResponse {

    /** Рекомендация: BUY_YES / BUY_NO / SKIP */
    private String recommendation;

    /** Уверенность в рекомендации, 0-100 */
    private Integer confidence;

    /** Оценка цены: UNDERPRICED / OVERPRICED / FAIR */
    private String priceAssessment;

    /** Краткое резюме анализа (2-3 предложения) */
    private String summary;

    /** Факторы в пользу роста вероятности YES */
    private List<String> bullishFactors;

    /** Факторы против YES */
    private List<String> bearishFactors;

    /** Ключевые риски */
    private List<String> keyRisks;

    /** Заголовки новостей, использованных в анализе */
    private List<String> newsUsed;
}
