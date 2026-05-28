package org.example.pet1.repository;

import org.example.pet1.entity.Market;
import org.springframework.data.jpa.repository.JpaRepository;
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

    /** Найти все активные рынки */
    List<Market> findByActive(Boolean active);
}
