package com.crypto.funding.infrastructure.persistence.mapper;

import com.crypto.funding.domain.trade.TradeJournalEntry;
import com.crypto.funding.infrastructure.persistence.model.TradeJournalEntryEntity;

public final class TradeJournalEntryMapper
{
    private TradeJournalEntryMapper()
    {
    }

    public static TradeJournalEntry toDomain( TradeJournalEntryEntity entity )
    {
        return new TradeJournalEntry(
            entity.getId(),
            entity.getEntityType(),
            entity.getEntityId(),
            entity.getEventCode(),
            entity.getOldState(),
            entity.getNewState(),
            entity.getActorType(),
            entity.getActorRef(),
            entity.getNote(),
            entity.getCreatedAt()
        );
    }
}
