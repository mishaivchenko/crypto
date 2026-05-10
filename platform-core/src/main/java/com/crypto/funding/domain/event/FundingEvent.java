package com.crypto.funding.domain.event;

import java.math.BigDecimal;
import java.time.Instant;

public record FundingEvent(
    Long id,
    String venue,
    String symbol,
    Instant fundingTime,
    BigDecimal fundingRatePct,
    FundingEventStatus status,
    String sourceType,
    String sourceRef,
    Long signalCandidateId,
    Long armedTradeId,
    Instant discoveredAt,
    Instant createdAt,
    Instant updatedAt
)
{
    public FundingEvent
    {
        if( venue == null || venue.isBlank() )
        {
            throw new IllegalArgumentException( "venue must not be blank" );
        }
        if( symbol == null || symbol.isBlank() )
        {
            throw new IllegalArgumentException( "symbol must not be blank" );
        }
        if( fundingTime == null )
        {
            throw new IllegalArgumentException( "fundingTime must not be null" );
        }
        if( status == null )
        {
            throw new IllegalArgumentException( "status must not be null" );
        }
        if( discoveredAt == null )
        {
            throw new IllegalArgumentException( "discoveredAt must not be null" );
        }
    }
}
