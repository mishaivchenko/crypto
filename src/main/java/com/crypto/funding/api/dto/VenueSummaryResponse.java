package com.crypto.funding.api.dto;

import java.time.Instant;

public record VenueSummaryResponse(
    String venue,
    String configuredMode,
    String metadataBaseUrl,
    String contractsBaseUrl,
    boolean credentialsConfigured,
    boolean enabledForMetadata,
    boolean metadataProviderAvailable,
    long activeInstrumentCount,
    Instant lastSyncedAt
)
{
}
