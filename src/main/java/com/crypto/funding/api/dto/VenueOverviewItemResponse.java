package com.crypto.funding.api.dto;

import com.crypto.funding.domain.venue.VenueConnectionStatus;

import java.time.Instant;

public record VenueOverviewItemResponse(
    String venue,
    String mode,
    boolean credentialsConfigured,
    boolean apiKeyLoaded,
    boolean secretKeyLoaded,
    boolean passphraseLoaded,
    boolean credentialsRequired,
    boolean modeOverridden,
    VenueConnectionStatus connectionStatus,
    String connectionMessage,
    long activeInstrumentCount,
    Instant lastSyncedAt,
    Instant lastCheckedAt,
    Long averageRequestTimeMs,
    Long requests
)
{
}
