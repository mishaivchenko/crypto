package com.crypto.funding.engine;

import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.TradeSide;

import java.math.BigDecimal;
import java.time.Instant;

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
    EnginePlanStatus status,
    Instant nextActionAt,
    Long millisUntilAction,
    Long millisUntilFunding,
    String summary
)
{
}
