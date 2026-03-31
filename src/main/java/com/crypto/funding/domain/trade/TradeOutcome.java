package com.crypto.funding.domain.trade;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeOutcome(
    Long id,
    Long armedTradeId,
    BigDecimal grossPnlUsd,
    BigDecimal netPnlUsd,
    BigDecimal feesUsd,
    String outcomeCode,
    String notes,
    Instant evaluatedAt,
    Instant createdAt,
    Instant updatedAt
)
{
    public TradeOutcome
    {
        if( armedTradeId == null )
        {
            throw new IllegalArgumentException( "armedTradeId must not be null" );
        }
        if( outcomeCode == null || outcomeCode.isBlank() )
        {
            throw new IllegalArgumentException( "outcomeCode must not be blank" );
        }
        if( evaluatedAt == null )
        {
            throw new IllegalArgumentException( "evaluatedAt must not be null" );
        }
    }
}
