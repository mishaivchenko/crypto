package com.crypto.funding.domain.trade;

import com.crypto.funding.domain.venue.VenueAccessMode;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)

public record ArmedTrade(
    Long id,
    Long fundingEventId,
    BigDecimal notionalUsd,
    TradeSide intendedSide,
    Instant plannedEntryAt,
    Instant plannedExitAt,
    Instant armedAt,
    Long eventAgeMsAtArm,
    Long entryLeadMs,
    Long exitLeadMs,
    Integer entryAttemptCount,
    Long entrySpacingMs,
    Long measuredEntryLatencyMs,
    Long manualLatencyAdjustmentMs,
    Long effectiveEntryLatencyMs,
    TradeArmSource armSource,
    ArmedTradeState state,
    String notes,
    VenueAccessMode mode,
    BigDecimal stopLossUsd,
    BigDecimal takeProfitUsd,
    Instant createdAt,
    Instant updatedAt,
    Long warmupP50Ms,
    Long warmupP95Ms,
    Boolean warmupFallbackUsed,
    Instant warmupDoneAt
)
{
    public ArmedTrade
    {
        if( fundingEventId == null )
        {
            throw new IllegalArgumentException( "fundingEventId must not be null" );
        }
        if( notionalUsd == null || notionalUsd.signum() <= 0 )
        {
            throw new IllegalArgumentException( "notionalUsd must be positive" );
        }
        if( state == null )
        {
            throw new IllegalArgumentException( "state must not be null" );
        }
        if( intendedSide != null && intendedSide != TradeSide.SHORT )
        {
            throw new IllegalArgumentException( "funding armed trades support SHORT side only" );
        }
        if( armedAt == null )
        {
            throw new IllegalArgumentException( "armedAt must not be null" );
        }
        if( entryAttemptCount == null )
        {
            entryAttemptCount = 1;
        }
        if( entryAttemptCount < 1 )
        {
            throw new IllegalArgumentException( "entryAttemptCount must be positive" );
        }
        if( entrySpacingMs == null )
        {
            entrySpacingMs = 0L;
        }
        if( entrySpacingMs < 0 )
        {
            throw new IllegalArgumentException( "entrySpacingMs must not be negative" );
        }
        if( measuredEntryLatencyMs != null && measuredEntryLatencyMs < 0 )
        {
            throw new IllegalArgumentException( "measuredEntryLatencyMs must not be negative" );
        }
        if( effectiveEntryLatencyMs != null && effectiveEntryLatencyMs < 0 )
        {
            throw new IllegalArgumentException( "effectiveEntryLatencyMs must not be negative" );
        }
        if( plannedEntryAt != null && plannedExitAt != null && plannedExitAt.isBefore( plannedEntryAt ) )
        {
            throw new IllegalArgumentException( "plannedExitAt must not be before plannedEntryAt" );
        }
    }
}
