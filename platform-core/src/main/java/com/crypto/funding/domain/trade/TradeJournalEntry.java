package com.crypto.funding.domain.trade;

import java.time.Instant;

public record TradeJournalEntry(
    Long id,
    TradeJournalEntityType entityType,
    Long entityId,
    TradeJournalEventCode eventCode,
    String oldState,
    String newState,
    TradeJournalActorType actorType,
    String actorRef,
    String note,
    Instant createdAt
)
{
    public TradeJournalEntry
    {
        if( entityType == null )
        {
            throw new IllegalArgumentException( "entityType must not be null" );
        }
        if( entityId == null )
        {
            throw new IllegalArgumentException( "entityId must not be null" );
        }
        if( eventCode == null )
        {
            throw new IllegalArgumentException( "eventCode must not be null" );
        }
        if( actorType == null )
        {
            throw new IllegalArgumentException( "actorType must not be null" );
        }
        if( createdAt == null )
        {
            throw new IllegalArgumentException( "createdAt must not be null" );
        }
    }
}
