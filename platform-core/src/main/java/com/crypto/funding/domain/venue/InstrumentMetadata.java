package com.crypto.funding.domain.venue;

import java.math.BigDecimal;
import java.time.Instant;

public record InstrumentMetadata(
    Long id,
    String venue,
    String canonicalSymbol,
    String venueSymbol,
    String baseAsset,
    String quoteAsset,
    String instrumentType,
    InstrumentStatus status,
    BigDecimal minOrderQty,
    BigDecimal qtyStep,
    BigDecimal minNotionalValue,
    Integer quantityPrecision,
    Instant lastSyncedAt,
    Instant createdAt,
    Instant updatedAt
)
{
    public InstrumentMetadata
    {
        if( venue == null || venue.isBlank() )
        {
            throw new IllegalArgumentException( "venue must not be blank" );
        }
        if( canonicalSymbol == null || canonicalSymbol.isBlank() )
        {
            throw new IllegalArgumentException( "canonicalSymbol must not be blank" );
        }
        if( venueSymbol == null || venueSymbol.isBlank() )
        {
            throw new IllegalArgumentException( "venueSymbol must not be blank" );
        }
        if( status == null )
        {
            throw new IllegalArgumentException( "status must not be null" );
        }
        if( lastSyncedAt == null )
        {
            throw new IllegalArgumentException( "lastSyncedAt must not be null" );
        }
    }
}
