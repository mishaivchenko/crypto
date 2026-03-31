package com.crypto.funding.api.dto;

import com.crypto.funding.domain.event.FundingEventStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record FundingEventResponse(
    Long id,
    String venue,
    String symbol,
    Instant fundingTime,
    BigDecimal fundingRatePct,
    FundingEventStatus status,
    String sourceType,
    String sourceRef,
    Long signalCandidateId,
    Instant discoveredAt,
    Instant createdAt,
    Instant updatedAt
)
{
}
