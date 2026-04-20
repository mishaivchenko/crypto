package com.crypto.funding.contract.engine;

import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.TradeSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record EngineExecutionPlan(
    Long armedTradeId,
    Long fundingEventId,
    String venue,
    String symbol,
    TradeSide intendedSide,
    BigDecimal notionalUsd,
    ArmedTradeState tradeState,
    Instant fundingTime,
    Instant plannedEntryAt,
    Instant plannedExitAt,
    Integer entryAttemptCount,
    Long entrySpacingMs,
    Long measuredEntryLatencyMs,
    Long manualLatencyAdjustmentMs,
    Long effectiveEntryLatencyMs,
    List<EngineEntryAttemptPlan> entryAttempts,
    EnginePlanStatus status,
    Instant nextActionAt,
    Long millisUntilAction,
    Long millisUntilFunding,
    String summary
)
{
}
