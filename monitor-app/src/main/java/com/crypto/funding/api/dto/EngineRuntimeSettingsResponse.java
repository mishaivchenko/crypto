package com.crypto.funding.api.dto;

import java.time.Instant;

public record EngineRuntimeSettingsResponse(
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
