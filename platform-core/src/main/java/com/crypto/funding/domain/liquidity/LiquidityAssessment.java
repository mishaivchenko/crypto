package com.crypto.funding.domain.liquidity;

import com.crypto.funding.domain.trade.TradeSide;

import java.math.BigDecimal;
import java.time.Instant;

public record LiquidityAssessment(
    String id,
    Long tradeId,
    String venue,
    String symbol,
    TradeSide side,
    BigDecimal bestBid,
    BigDecimal bestAsk,
    BigDecimal spreadBps,
    BigDecimal maxSlippageBps,
    BigDecimal entryBidDepthNotional,
    BigDecimal exitAskDepthNotional,
    BigDecimal roundTripSafeNotional,
    BigDecimal safetyHaircut,
    BigDecimal recommendedMaxOrderNotional,
    LiquidityScore score,
    Instant sampledAt,
    Instant expiresAt
)
{
    public LiquidityAssessment
    {
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
        if( sampledAt == null )
        {
            throw new IllegalArgumentException( "sampledAt must not be null" );
        }
        if( score == null )
        {
            throw new IllegalArgumentException( "score must not be null" );
        }
    }
}
