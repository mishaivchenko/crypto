package com.crypto.funding.api.dto;

import java.time.Instant;

public record VenueOverviewItemResponse(
    String venue,
    String mode,
    boolean credentialsConfigured,
    long activeInstrumentCount,
    Instant lastSyncedAt,
    Long averageRequestTimeMs,
    Long requests
)
{
}
