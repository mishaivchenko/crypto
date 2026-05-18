package com.crypto.funding.telegram.client.dto;

import java.time.Instant;
import java.util.List;

public record MonitorOverview(
    String version,
    String globalAccessMode,
    boolean globalModeOverridden,
    long pendingCandidates,
    long fundingEvents,
    long discoveredEvents,
    long armedTrades,
    long activeVenues,
    List<VenueOverview> venues,
    Instant generatedAt
)
{
    public record VenueOverview(
        String venue,
        String mode,
        boolean credentialsConfigured,
        boolean apiKeyLoaded,
        boolean secretKeyLoaded,
        boolean passphraseLoaded,
        boolean credentialsRequired,
        boolean modeOverridden,
        String connectionStatus,
        String connectionMessage,
        int activeInstrumentCount,
        Instant lastSyncedAt,
        Instant lastCheckedAt,
        Long averageRequestTimeMs,
        Long requests
    )
    {
    }
}
