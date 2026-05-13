package com.crypto.funding.contract.engine;

import java.time.Instant;

public record EngineLatencySampleRequest(
    String venue,
    String symbol,
    String operation,
    long durationMs,
    Instant sampledAt
)
{
}
