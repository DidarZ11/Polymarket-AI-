package org.example.pet1.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Сохранённый результат AI-анализа рынка.
 * Каждый вызов /analyze создаёт новую запись.
 */
@Entity
@Table(name = "market_analyses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Рынок, для которого выполнен анализ */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_fk", nullable = false)
    private Market market;

    /** Рекомендация: BUY_YES / BUY_NO / SKIP */
    @Column(name = "recommendation")
    private String recommendation;

    /** Уверенность 0-100 */
    @Column(name = "confidence")
    private Integer confidence;

    /** Оценка цены: UNDERPRICED / OVERPRICED / FAIR */
    @Column(name = "price_assessment")
    private String priceAssessment;

    /** Резюме анализа */
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    /** JSON-массив факторов роста */
    @Column(name = "bullish_factors", columnDefinition = "TEXT")
    private String bullishFactors;

    /** JSON-массив факторов снижения */
    @Column(name = "bearish_factors", columnDefinition = "TEXT")
    private String bearishFactors;

    /** JSON-массив ключевых рисков */
    @Column(name = "key_risks", columnDefinition = "TEXT")
    private String keyRisks;

    /** JSON-массив использованных заголовков новостей */
    @Column(name = "news_used", columnDefinition = "TEXT")
    private String newsUsed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
