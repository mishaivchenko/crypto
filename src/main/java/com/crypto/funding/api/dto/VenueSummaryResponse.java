package com.crypto.funding.api.dto;

import com.crypto.funding.domain.venue.VenueAccessMode;
import com.crypto.funding.domain.venue.VenueConnectionStatus;

import java.time.Instant;
import java.util.List;

public record VenueSummaryResponse(
    String venue,
    String configuredMode,
    String metadataBaseUrl,
    String contractsBaseUrl,
    boolean credentialsConfigured,
    boolean apiKeyLoaded,
    boolean secretKeyLoaded,
    boolean passphraseLoaded,
    boolean credentialsRequired,
    boolean modeOverridden,
    List<VenueAccessMode> availableModes,
    VenueConnectionStatus connectionStatus,
    String connectionMessage,
    Integer lastConnectionHttpStatus,
    Instant lastCheckedAt,
    boolean enabledForMetadata,
    boolean metadataProviderAvailable,
    long activeInstrumentCount,
    Instant lastSyncedAt
)
{
}
