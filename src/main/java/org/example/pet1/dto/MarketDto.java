package org.example.pet1.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO для парсинга ответа от Polymarket Gamma API.
 * Поля соответствуют JSON-структуре из https://gamma-api.polymarket.com/markets
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketDto {

    /** Уникальный идентификатор рынка */
    @JsonProperty("id")
    private String id;

    /** Вопрос рынка */
    @JsonProperty("question")
    private String question;

    /**
     * Цены исходов в виде строки-массива JSON, например: "[\"0.82\", \"0.18\"]".
     * Первый элемент — вероятность YES, второй — NO.
     */
    @JsonProperty("outcomePrices")
    private String outcomePrices;

    /** Общий объём торгов в долларах */
    @JsonProperty("volume")
    private Double volume;

    /** Дата окончания рынка (ISO 8601), например: "2025-11-04T00:00:00Z" */
    @JsonProperty("endDate")
    private String endDate;

    /** Признак активности рынка */
    @JsonProperty("active")
    private Boolean active;

    /** URL картинки рынка */
    @JsonProperty("image")
    private String image;

    /** Категория рынка */
    @JsonProperty("category")
    private String category;
}