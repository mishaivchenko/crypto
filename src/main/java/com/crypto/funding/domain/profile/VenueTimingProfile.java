package com.crypto.funding.domain.profile;

import java.time.Instant;

public record VenueTimingProfile(
    Long id,
    String venue,
    String symbol,
    Long observedLagMs,
    Long entryLatencyMs,
    Long exitLatencyMs,
    Instant sampledAt,
    String notes,
    Instant createdAt,
    Instant updatedAt
)
{
    public VenueTimingProfile
    {
        if( venue == null || venue.isBlank() )
        {
            throw new IllegalArgumentException( "venue must not be blank" );
        }
        if( symbol == null || symbol.isBlank() )
        {
            throw new IllegalArgumentException( "symbol must not be blank" );
        }
        if( sampledAt == null )
        {
            throw new IllegalArgumentException( "sampledAt must not be null" );
        }
    }
}
