package com.crypto.funding.domain.liquidity;

import java.time.Instant;
import java.util.List;

public record OrderBookSnapshot(
    String venue,
    String symbol,
    List<OrderBookLevel> bids,
    List<OrderBookLevel> asks,
    Instant sampledAt
)
{
    public OrderBookSnapshot
    {
        if( venue == null || venue.isBlank() )
        {
            throw new IllegalArgumentException( "venue must not be blank" );
        }
        if( symbol == null || symbol.isBlank() )
        {
            throw new IllegalArgumentException( "symbol must not be blank" );
        }
        if( bids == null )
        {
            throw new IllegalArgumentException( "bids must not be null" );
        }
        if( asks == null )
        {
            throw new IllegalArgumentException( "asks must not be null" );
        }
        if( sampledAt == null )
        {
            throw new IllegalArgumentException( "sampledAt must not be null" );
        }
        bids = List.copyOf( bids );
        asks = List.copyOf( asks );
    }
}
