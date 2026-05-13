package com.crypto.funding.api.dto;

import java.time.Instant;

public record VenueLatencyProbeResponse(
    String venue,
    long durationMs,
    Instant sampledAt
)
{
}
