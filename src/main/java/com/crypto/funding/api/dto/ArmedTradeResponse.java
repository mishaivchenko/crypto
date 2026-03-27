package com.crypto.funding.api.dto;

import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.TradeSide;

import java.math.BigDecimal;
import java.time.Instant;

public record ArmedTradeResponse(
    Long id,
    Long fundingEventId,
    BigDecimal notionalUsd,
    TradeSide intendedSide,
    Instant plannedEntryAt,
    Instant plannedExitAt,
    ArmedTradeState state,
    String notes,
    Instant createdAt,
    Instant updatedAt
)
{
}
