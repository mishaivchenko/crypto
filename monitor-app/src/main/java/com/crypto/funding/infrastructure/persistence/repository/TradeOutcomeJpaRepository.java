package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.infrastructure.persistence.model.TradeOutcomeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TradeOutcomeJpaRepository extends JpaRepository<TradeOutcomeEntity, Long>
{
    Optional<TradeOutcomeEntity> findFirstByArmedTradeIdOrderByCreatedAtDesc( Long armedTradeId );

    List<TradeOutcomeEntity> findByArmedTradeIdIn( Collection<Long> armedTradeIds );

    @Query("SELECT COALESCE(SUM(t.netPnlUsd), 0), COALESCE(SUM(t.grossPnlUsd), 0), COALESCE(SUM(t.feesUsd), 0), COUNT(t) FROM TradeOutcomeEntity t")
    Object[] aggregateTotals();

    @Query("SELECT COUNT(t) FROM TradeOutcomeEntity t WHERE t.netPnlUsd > 0")
    long countProfitable();
}
