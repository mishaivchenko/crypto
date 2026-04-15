package com.crypto.funding.api.dto;

import com.crypto.funding.domain.trade.TradeSide;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record ArmFundingEventRequest(
    @NotNull @Positive BigDecimal notionalUsd,
    TradeSide intendedSide,
    Instant plannedEntryAt,
    Instant plannedExitAt,
    @Min(1) @Max(25) Integer entryAttemptCount,
    @PositiveOrZero Long entrySpacingMs,
    @Min(-60000) @Max(60000) Long manualLatencyAdjustmentMs,
    @Size(max = 1000) String notes
)
{
}
