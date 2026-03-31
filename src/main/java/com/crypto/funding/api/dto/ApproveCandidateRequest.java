package com.crypto.funding.api.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record ApproveCandidateRequest(
    @Size(max = 32) String venue,
    @Size(max = 32) String symbol,
    Instant fundingTime,
    BigDecimal fundingRatePct,
    @Size(max = 500) String reviewNotes
)
{
}
