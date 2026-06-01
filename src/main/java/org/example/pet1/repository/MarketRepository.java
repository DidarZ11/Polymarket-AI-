package org.example.pet1.repository;

import org.example.pet1.entity.Market;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с таблицей markets в PostgreSQL.
 */
@Repository
public interface MarketRepository extends JpaRepository<Market, Long> {

    /** Найти рынок по его идентификатору на Polymarket */
    Optional<Market> findByMarketId(String marketId);

    /** Вставить или обновить рынок по market_id (PostgreSQL ON CONFLICT upsert) */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO markets (market_id, question, outcome_prices,
                volume, end_date, active, image_url, category, probability_yes, created_at)
            VALUES (:marketId, :question, :outcomePrices, :volume,
                :endDate, :active, :imageUrl, :category, :probabilityYes, NOW())
            ON CONFLICT (market_id)
            DO UPDATE SET
                question        = EXCLUDED.question,
                outcome_prices  = EXCLUDED.outcome_prices,
                volume          = EXCLUDED.volume,
                probability_yes = EXCLUDED.probability_yes,
                active          = EXCLUDED.active
            """)
    void upsertMarket(@Param("marketId")      String  marketId,
                      @Param("question")      String  question,
                      @Param("outcomePrices") String  outcomePrices,
                      @Param("volume")        Double  volume,
                      @Param("endDate")       String  endDate,
                      @Param("active")        Boolean active,
                      @Param("imageUrl")      String  imageUrl,
                      @Param("category")      String  category,
                      @Param("probabilityYes") Double probabilityYes);

    /** Найти все активные рынки */
    List<Market> findByActive(Boolean active);

    /** Найти рынки по категории */
    List<Market> findByCategory(String category);

    // ── Сортировка ────────────────────────────────────────────────────────────

    /** Default: сортировка по объёму убывает, null-значения в конце */
    @Query("SELECT m FROM Market m ORDER BY m.volume DESC NULLS LAST")
    Page<Market> findAllOrderByVolumeDesc(Pageable pageable);

    /** Сортировка по вероятности YES убывает */
    Page<Market> findAllByOrderByProbabilityYesDesc(Pageable pageable);

    /** Сортировка по дате закрытия возрастает (ближайшие первые) */
    Page<Market> findAllByOrderByEndDateAsc(Pageable pageable);

    // ── Поиск ─────────────────────────────────────────────────────────────────

    /** Поиск по тексту вопроса, сортировка по объёму */
    Page<Market> findByQuestionContainingIgnoreCaseOrderByVolumeDesc(String search, Pageable pageable);

    // ── Фильтр по вероятности ─────────────────────────────────────────────────

    /** Фильтр по диапазону вероятности YES, сортировка по вероятности убывает */
    Page<Market> findByProbabilityYesBetweenOrderByProbabilityYesDesc(
            Double minProb, Double maxProb, Pageable pageable);

    // ── Агрегаты для статистики ────────────────────────────────────────────────

    /** Суммарный объём торгов по всем рынкам */
    @Query("SELECT SUM(m.volume) FROM Market m")
    Double sumVolume();

    /** Количество активных рынков */
    @Query("SELECT COUNT(m) FROM Market m WHERE m.active = true")
    Long countActive();

    /** Средняя вероятность YES из хранимой колонки */
    @Query("SELECT AVG(m.probabilityYes) FROM Market m WHERE m.probabilityYes > 0")
    Double avgProbabilityYes();
}
