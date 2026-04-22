package com.crypto.funding.contract.engine;

import java.time.Instant;

public record EngineRuntimeControlResponse(
    String module,
    String version,
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
