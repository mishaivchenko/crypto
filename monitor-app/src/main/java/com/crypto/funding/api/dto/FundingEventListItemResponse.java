package com.crypto.funding.api.dto;

import com.crypto.funding.domain.event.FundingEventStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record FundingEventListItemResponse(
    Long id,
    String venue,
    String symbol,
    Instant fundingTime,
    BigDecimal fundingRatePct,
    FundingEventStatus status,
    String sourceType,
    Long signalCandidateId,
    Instant discoveredAt
)
{
}
