package com.crypto.funding.api.dto;

import java.time.Instant;

public record VenueRequestTimingResponse(
    String venue,
    String operation,
    long requests,
    long successes,
    long failures,
    long averageDurationMs,
    long lastDurationMs,
    Integer lastHttpStatus,
    String lastError,
    Instant lastOccurredAt,
    long lastPayloadSize
)
{
}
