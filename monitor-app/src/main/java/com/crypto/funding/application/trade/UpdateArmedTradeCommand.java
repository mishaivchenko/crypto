package com.crypto.funding.application.trade;

import java.math.BigDecimal;
import java.time.Instant;

public record UpdateArmedTradeCommand(
    Long id,
    BigDecimal notionalUsd,
    Instant plannedEntryAt,
    Instant plannedExitAt,
    Integer entryAttemptCount,
    Long entrySpacingMs,
    Long manualLatencyAdjustmentMs,
    BigDecimal stopLossUsd,
    BigDecimal takeProfitUsd,
    String notes
)
{
}
