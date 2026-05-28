package com.crypto.funding.api.dto;

import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.TradeSide;

import java.math.BigDecimal;
import java.time.Instant;

public record ArmedTradeResponse(
    Long id,
    Long fundingEventId,
    Long signalCandidateId,
    String venue,
    String symbol,
    String venueSymbol,
    Instant fundingTime,
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
    com.crypto.funding.domain.trade.TradeArmSource armSource,
    ArmedTradeState state,
    String notes,
    String mode,
    BigDecimal stopLossUsd,
    BigDecimal takeProfitUsd,
    Instant createdAt,
    Instant updatedAt,
    Long warmupP50Ms,
    Long warmupP95Ms,
    Boolean warmupFallbackUsed,
    Instant warmupDoneAt,
    BigDecimal fundingRatePct
)
{
}
