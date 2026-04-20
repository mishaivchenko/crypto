package com.crypto.funding.contract.engine;

import java.time.Instant;

public record EngineEntryAttemptPlan(
    int attemptNumber,
    Instant targetEntryAt,
    Instant triggerAt,
    Long millisUntilTrigger,
    Long offsetFromFirstEntryMs,
    Long effectiveLatencyMs
)
{
}
