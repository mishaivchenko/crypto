package com.crypto.funding.domain.trade;

import java.math.BigDecimal;
import java.time.Instant;

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
    TradeArmSource armSource,
    ArmedTradeState state,
    String notes,
    Instant createdAt,
    Instant updatedAt
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
        if( armedAt == null )
        {
            throw new IllegalArgumentException( "armedAt must not be null" );
        }
        if( plannedEntryAt != null && plannedExitAt != null && plannedExitAt.isBefore( plannedEntryAt ) )
        {
            throw new IllegalArgumentException( "plannedExitAt must not be before plannedEntryAt" );
        }
    }
}
