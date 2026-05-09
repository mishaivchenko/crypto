package com.crypto.funding.contract.engine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record EngineRuntimeControlResponse(
    String module,
    String version,
    String tradingVenueAccessMode,
    boolean liveOrderEnabled,
    boolean killSwitchEnabled,
    List<String> liveEnabledVenues,
    BigDecimal maxNotionalUsd,
    boolean executionLoopEnabled,
    long executionLoopIntervalMs,
    long minimumExecutionLoopIntervalMs,
    Instant runtimeUpdatedAt,
    Instant lastRunStartedAt,
    Instant lastRunFinishedAt,
    boolean lastRunForced,
    int lastPlansScanned,
    int lastAttemptsSubmitted,
    int lastAttemptsSkipped,
    long lastExecutionRunDurationMs,
    Instant lastForcedRunStartedAt,
    Instant lastForcedRunFinishedAt,
    int lastForcedPlansScanned,
    int lastForcedAttemptsSubmitted,
    int lastForcedAttemptsSkipped,
    long lastForcedRunDurationMs
)
{
}
