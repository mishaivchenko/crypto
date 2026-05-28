package com.crypto.funding.application.ai;

import com.crypto.funding.domain.ai.AiRecommendation;
import com.crypto.funding.infrastructure.persistence.repository.AiSignalAdviceJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AiAdvisorPerformanceService
{
    private final AiSignalAdviceJpaRepository adviceRepository;

    public AiAdvisorPerformanceService( AiSignalAdviceJpaRepository adviceRepository )
    {
        this.adviceRepository = adviceRepository;
    }

    public record RecommendationStat(
        String recommendation,
        int tradeCount,
        Double winRate,
        BigDecimal avgPnlUsd
    )
    {
    }

    public record PerformanceStats(
        List<RecommendationStat> stats,
        int totalTrades
    )
    {
    }

    @Transactional(readOnly = true)
    public PerformanceStats getPerformanceStats()
    {
        List<Object[]> rows = adviceRepository.findPerformanceStats();

        Map<String, Object[]> byRec = rows.stream()
            .collect( Collectors.toMap( r -> (String) r[0], r -> r ) );

        List<RecommendationStat> stats = Arrays.stream( AiRecommendation.values() )
            .map( rec -> {
                Object[] row = byRec.get( rec.name() );
                if( row == null || row[1] == null || ((Number) row[1]).intValue() == 0 )
                {
                    return new RecommendationStat( rec.name(), 0, null, null );
                }
                int tradeCount = ((Number) row[1]).intValue();
                int profitableCount = row[2] != null ? ((Number) row[2]).intValue() : 0;
                double winRate = tradeCount > 0 ? (double) profitableCount / tradeCount : 0.0;
                BigDecimal avgPnl = row[3] != null
                    ? new BigDecimal( row[3].toString() ).setScale( 2, RoundingMode.HALF_UP )
                    : null;
                return new RecommendationStat( rec.name(), tradeCount, winRate, avgPnl );
            } )
            .toList();

        int totalTrades = stats.stream().mapToInt( RecommendationStat::tradeCount ).sum();
        return new PerformanceStats( stats, totalTrades );
    }
}
