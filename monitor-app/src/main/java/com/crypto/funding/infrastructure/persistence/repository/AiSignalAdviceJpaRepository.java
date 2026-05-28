package com.crypto.funding.infrastructure.persistence.repository;

import com.crypto.funding.infrastructure.persistence.model.AiSignalAdviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AiSignalAdviceJpaRepository extends JpaRepository<AiSignalAdviceEntity, Long>
{
    Optional<AiSignalAdviceEntity> findFirstBySignalCandidateIdOrderByAnalyzedAtDesc( Long signalCandidateId );

    @Query(nativeQuery = true, value = """
        WITH latest_advice AS (
            SELECT id, signal_candidate_id, recommendation
            FROM ai_signal_advice a
            WHERE id = (
                SELECT MAX(id) FROM ai_signal_advice WHERE signal_candidate_id = a.signal_candidate_id
            )
        )
        SELECT
            la.recommendation,
            COUNT(DISTINCT at2.id)                                              AS trade_count,
            COUNT(DISTINCT CASE WHEN o.net_pnl_usd > 0 THEN at2.id END)        AS profitable_count,
            AVG(o.net_pnl_usd)                                                  AS avg_pnl_usd
        FROM latest_advice la
        JOIN signal_candidate sc  ON sc.id               = la.signal_candidate_id
        JOIN funding_event    fe  ON fe.id               = sc.funding_event_id
        JOIN armed_trade      at2 ON at2.funding_event_id = fe.id
        JOIN trade_outcome    o   ON o.armed_trade_id    = at2.id
        WHERE at2.state = 'CLOSED'
        GROUP BY la.recommendation
        """)
    List<Object[]> findPerformanceStats();
}
