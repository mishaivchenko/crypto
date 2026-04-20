package com.crypto.funding.api.dto;

import com.crypto.funding.domain.venue.VenueAccessMode;
import com.crypto.funding.domain.venue.VenueConnectionStatus;

import java.time.Instant;

public record OperatorCredentialResponse(
    String venue,
    VenueAccessMode mode,
    boolean configured,
    String apiKeyMask,
    String secretKeyMask,
    String passphraseMask,
    VenueConnectionStatus connectionStatus,
    String connectionMessage,
    Integer lastConnectionHttpStatus,
    Instant lastCheckedAt,
    Instant updatedAt
)
{
}
