package org.example.pet1.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Сущность рынка Polymarket.
 * Хранит информацию об одном предсказательном рынке в базе данных.
 */
@Entity
@Table(name = "markets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Market {

    /** Внутренний идентификатор записи в нашей БД */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Уникальный идентификатор рынка на стороне Polymarket */
    @Column(name = "market_id", unique = true, nullable = false)
    private String marketId;

    /** Вопрос рынка, например: "Выиграет ли Трамп выборы?" */
    @Column(name = "question", columnDefinition = "TEXT")
    private String question;

    /**
     * Цены исходов в виде строки-массива JSON, например: "[\"0.82\", \"0.18\"]".
     * Хранится как текст, первый элемент — вероятность YES.
     */
    @Column(name = "outcome_prices", columnDefinition = "TEXT")
    private String outcomePrices;

    /** Общий объём торгов в долларах */
    @Column(name = "volume")
    private Double volume;

    /** Дата окончания рынка (ISO 8601 строка) */
    @Column(name = "end_date")
    private String endDate;

    /** Активен ли рынок на Polymarket */
    @Column(name = "active")
    private Boolean active;

    /** Дата и время сохранения записи в нашу БД */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}