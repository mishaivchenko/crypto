package com.crypto.funding.application.execution;

import com.crypto.funding.infrastructure.persistence.model.TradeOutcomeEntity;
import com.crypto.funding.infrastructure.persistence.repository.TradeOutcomeJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
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
        List<TradeOutcomeEntity> all = tradeOutcomeRepository.findAll();
        BigDecimal totalNet = BigDecimal.ZERO;
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;
        long profitable = 0;
        for( TradeOutcomeEntity entity : all )
        {
            totalNet = totalNet.add( entity.getNetPnlUsd() == null ? BigDecimal.ZERO : entity.getNetPnlUsd() );
            totalGross = totalGross.add( entity.getGrossPnlUsd() == null ? BigDecimal.ZERO : entity.getGrossPnlUsd() );
            totalFees = totalFees.add( entity.getFeesUsd() == null ? BigDecimal.ZERO : entity.getFeesUsd() );
            if( entity.getNetPnlUsd() != null && entity.getNetPnlUsd().signum() > 0 )
            {
                profitable++;
            }
        }
        return new PnlAggregate( totalNet, totalGross, totalFees, all.size(), profitable );
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
