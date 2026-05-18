package com.crypto.funding.contract.engine;

import java.time.Instant;

public record WarmupCalibrationRequest(
    Long p50Ms,
    Long p95Ms,
    Boolean fallbackUsed,
    Instant doneAt
)
{
}
