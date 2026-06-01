package org.example.pet1.repository;

import org.example.pet1.entity.MarketAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Репозиторий истории AI-анализов рынков.
 */
@Repository
public interface MarketAnalysisRepository extends JpaRepository<MarketAnalysis, Long> {

    /** История анализов конкретного рынка, новые — первыми */
    List<MarketAnalysis> findByMarketIdOrderByCreatedAtDesc(Long marketId);

    /** Последние 10 анализов по всем рынкам (для главной страницы) */
    List<MarketAnalysis> findTop10ByOrderByCreatedAtDesc();
}
