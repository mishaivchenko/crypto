package com.crypto.funding.domain.execution;

import com.crypto.funding.domain.trade.TradeSide;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderIntent(
    TradeSide side,
    ExecutionType executionType,
    BigDecimal quantity,
    BigDecimal limitPrice,
    Instant intendedAt
)
{
    public OrderIntent
    {
        if( side == null )
        {
            throw new IllegalArgumentException( "side must not be null" );
        }
        if( executionType == null )
        {
            throw new IllegalArgumentException( "executionType must not be null" );
        }
        if( quantity == null || quantity.signum() <= 0 )
        {
            throw new IllegalArgumentException( "quantity must be positive" );
        }
        if( executionType == ExecutionType.LIMIT && ( limitPrice == null || limitPrice.signum() <= 0 ) )
        {
            throw new IllegalArgumentException( "limitPrice must be positive for LIMIT intents" );
        }
    }
}
