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
    String summary,
    String venueSymbol,
    BigDecimal minOrderQty,
    BigDecimal qtyStep,
    BigDecimal minNotionalValue,
    Instant metadataLastSyncedAt,
    Instant latencySampledAt,
    BigDecimal positionQuantity,
    BigDecimal positionEntryPrice,
    BigDecimal stopLossUsd,
    BigDecimal takeProfitUsd
)
{
    public EngineExecutionPlan(
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
        this(
            armedTradeId,
            fundingEventId,
            venue,
            symbol,
            intendedSide,
            notionalUsd,
            tradeState,
            fundingTime,
            plannedEntryAt,
            plannedExitAt,
            entryAttemptCount,
            entrySpacingMs,
            measuredEntryLatencyMs,
            manualLatencyAdjustmentMs,
            effectiveEntryLatencyMs,
            entryAttempts,
            status,
            nextActionAt,
            millisUntilAction,
            millisUntilFunding,
            summary,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }
}
