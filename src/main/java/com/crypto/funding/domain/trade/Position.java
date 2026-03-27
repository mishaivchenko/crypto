package com.crypto.funding.domain.trade;

import java.math.BigDecimal;
import java.time.Instant;

public record Position(
    Long id,
    Long armedTradeId,
    String venue,
    String symbol,
    TradeSide side,
    BigDecimal quantity,
    BigDecimal entryPrice,
    BigDecimal exitPrice,
    PositionState state,
    Instant openedAt,
    Instant closedAt,
    Instant createdAt,
    Instant updatedAt
)
{
    public Position
    {
        if( armedTradeId == null )
        {
            throw new IllegalArgumentException( "armedTradeId must not be null" );
        }
        if( venue == null || venue.isBlank() )
        {
            throw new IllegalArgumentException( "venue must not be blank" );
        }
        if( symbol == null || symbol.isBlank() )
        {
            throw new IllegalArgumentException( "symbol must not be blank" );
        }
        if( side == null )
        {
            throw new IllegalArgumentException( "side must not be null" );
        }
        if( quantity == null || quantity.signum() <= 0 )
        {
            throw new IllegalArgumentException( "quantity must be positive" );
        }
        if( state == null )
        {
            throw new IllegalArgumentException( "state must not be null" );
        }
    }
}
