package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.infrastructure.persistence.model.LiquidityAssessmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface LiquidityAssessmentJpaRepository extends JpaRepository<LiquidityAssessmentEntity, Long>
{
    Optional<LiquidityAssessmentEntity> findFirstByTradeIdOrderBySampledAtDesc( Long tradeId );

    Optional<LiquidityAssessmentEntity> findFirstBySignalCandidateIdOrderBySampledAtDesc( Long signalCandidateId );

    Optional<LiquidityAssessmentEntity> findFirstBySignalCandidateIdOrderBySampledAtAsc( Long signalCandidateId );

    Optional<LiquidityAssessmentEntity> findByAssessmentId( String assessmentId );

    @Query("SELECT DISTINCT a.tradeId FROM LiquidityAssessmentEntity a WHERE a.tradeId IN :tradeIds")
    Set<Long> findCoveredTradeIds( @Param("tradeIds") Collection<Long> tradeIds );

    /**
     * Returns the latest sampledAt per covered trade in one query (one row per tradeId).
     * Use this to compute avg freshness in Java without N+1 selects.
     */
    @Query("SELECT MAX(a.sampledAt) FROM LiquidityAssessmentEntity a WHERE a.tradeId IN :tradeIds GROUP BY a.tradeId")
    List<Instant> findLatestSampledAtPerTrade( @Param("tradeIds") Collection<Long> tradeIds );

    /**
     * Returns the count of distinct active trades that have at least one liquidity assessment, grouped by venue.
     * Only trades whose state is in the given set are considered.
     * Each element is an Object[2]: [venue (String), enrichedCount (Long)].
     */
    @Query(
        "SELECT la.venue, COUNT(DISTINCT la.tradeId) " +
        "FROM LiquidityAssessmentEntity la " +
        "WHERE la.tradeId IS NOT NULL " +
        "  AND EXISTS (" +
        "      SELECT 1 FROM ArmedTradeEntity at " +
        "      WHERE at.id = la.tradeId AND at.state IN :states" +
        "  ) " +
        "GROUP BY la.venue"
    )
    List<Object[]> countEnrichedArmedTradesByVenue( @Param("states") Set<com.crypto.funding.domain.trade.ArmedTradeState> states );
}
