package com.crypto.funding.api.dto;

import java.time.Instant;
import java.util.List;

public record MonitorOverviewResponse(
    String version,
    long pendingCandidates,
    long fundingEvents,
    long discoveredEvents,
    long armedTrades,
    long activeVenues,
    List<VenueOverviewItemResponse> venues,
    Instant generatedAt
)
{
}
