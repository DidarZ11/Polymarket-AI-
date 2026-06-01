package org.example.pet1.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO ответа API — содержит все поля рынка плюс вычисленную вероятность YES.
 */
@Data
@Builder
public class MarketResponse {

    private Long id;
    private String marketId;
    private String question;
    private String outcomePrices;

    /** Вероятность исхода YES в процентах, например 52.5 */
    private Double probabilityYes;

    private Double volume;
    private String endDate;
    private Boolean active;

    /** URL картинки рынка */
    private String imageUrl;

    private String category;
    private LocalDateTime createdAt;
}
