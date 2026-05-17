package com.crypto.funding.application.execution;

import com.crypto.funding.infrastructure.persistence.model.TradeOutcomeEntity;
import com.crypto.funding.infrastructure.persistence.repository.TradeOutcomeJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TradeOutcomeQueryService
{
    public record OutcomeSummary(
        Long armedTradeId,
        BigDecimal grossPnlUsd,
        BigDecimal netPnlUsd,
        BigDecimal feesUsd,
        String outcomeCode,
        Instant evaluatedAt
    )
    {
    }

    public record PnlAggregate(
        BigDecimal totalNetPnlUsd,
        BigDecimal totalGrossPnlUsd,
        BigDecimal totalFeesUsd,
        long closedTrades,
        long profitableTrades
    )
    {
    }

    private final TradeOutcomeJpaRepository tradeOutcomeRepository;

    public TradeOutcomeQueryService( TradeOutcomeJpaRepository tradeOutcomeRepository )
    {
        this.tradeOutcomeRepository = tradeOutcomeRepository;
    }

    @Transactional(readOnly = true)
    public Optional<OutcomeSummary> findByArmedTrade( Long armedTradeId )
    {
        return tradeOutcomeRepository.findFirstByArmedTradeIdOrderByCreatedAtDesc( armedTradeId )
                                     .map( this::toSummary );
    }

    @Transactional(readOnly = true)
    public Map<Long, OutcomeSummary> findByArmedTradeIds( Collection<Long> armedTradeIds )
    {
        return tradeOutcomeRepository.findByArmedTradeIdIn( armedTradeIds ).stream()
                                     .collect( Collectors.toMap(
                                         TradeOutcomeEntity::getArmedTradeId,
                                         this::toSummary,
                                         ( existing, replacement ) -> existing
                                     ) );
    }

    @Transactional(readOnly = true)
    public PnlAggregate aggregate()
    {
        Object[] totals = tradeOutcomeRepository.aggregateTotals();
        BigDecimal totalNet = totals[0] instanceof BigDecimal bd ? bd : new BigDecimal( totals[0].toString() );
        BigDecimal totalGross = totals[1] instanceof BigDecimal bd ? bd : new BigDecimal( totals[1].toString() );
        BigDecimal totalFees = totals[2] instanceof BigDecimal bd ? bd : new BigDecimal( totals[2].toString() );
        long closedTrades = ((Number) totals[3]).longValue();
        long profitable = tradeOutcomeRepository.countProfitable();
        return new PnlAggregate( totalNet, totalGross, totalFees, closedTrades, profitable );
    }

    private OutcomeSummary toSummary( TradeOutcomeEntity entity )
    {
        return new OutcomeSummary(
            entity.getArmedTradeId(),
            entity.getGrossPnlUsd(),
            entity.getNetPnlUsd(),
            entity.getFeesUsd(),
            entity.getOutcomeCode(),
            entity.getEvaluatedAt()
        );
    }
}
