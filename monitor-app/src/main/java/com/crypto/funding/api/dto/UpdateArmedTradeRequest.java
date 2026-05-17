package com.crypto.funding.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;

public record UpdateArmedTradeRequest(
    @NotNull @Positive BigDecimal notionalUsd,
    Instant plannedEntryAt,
    Instant plannedExitAt,
    @Min(1) @Max(25) Integer entryAttemptCount,
    @PositiveOrZero Long entrySpacingMs,
    @Min(-60000) @Max(60000) Long manualLatencyAdjustmentMs,
    BigDecimal stopLossUsd,
    BigDecimal takeProfitUsd,
    String notes
)
{
}
