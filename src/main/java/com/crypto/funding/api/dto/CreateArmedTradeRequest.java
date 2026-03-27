package com.crypto.funding.api.dto;

import com.crypto.funding.domain.trade.TradeSide;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateArmedTradeRequest(
    @NotNull Long fundingEventId,
    @NotNull @Positive BigDecimal notionalUsd,
    TradeSide intendedSide,
    Instant plannedEntryAt,
    Instant plannedExitAt,
    String notes
)
{
}
