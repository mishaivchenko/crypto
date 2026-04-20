package com.crypto.funding.api.dto;

import com.crypto.funding.domain.trade.TradeJournalActorType;
import com.crypto.funding.domain.trade.TradeJournalEntityType;
import com.crypto.funding.domain.trade.TradeJournalEventCode;

import java.time.Instant;

public record TradeJournalEntryResponse(
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
}
