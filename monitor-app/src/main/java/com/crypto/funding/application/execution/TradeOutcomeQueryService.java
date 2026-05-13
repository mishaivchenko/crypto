package com.crypto.funding.application.execution;

import com.crypto.funding.infrastructure.persistence.model.TradeOutcomeEntity;
import com.crypto.funding.infrastructure.persistence.repository.TradeOutcomeJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Service
public class TradeOutcomeQueryService
{
    public record OutcomeSummary(
        BigDecimal grossPnlUsd,
        BigDecimal netPnlUsd,
        BigDecimal feesUsd,
        String outcomeCode,
        Instant evaluatedAt
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

    private OutcomeSummary toSummary( TradeOutcomeEntity entity )
    {
        return new OutcomeSummary(
            entity.getGrossPnlUsd(),
            entity.getNetPnlUsd(),
            entity.getFeesUsd(),
            entity.getOutcomeCode(),
            entity.getEvaluatedAt()
        );
    }
}
