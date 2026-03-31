package com.crypto.funding.application.event;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateFundingEventCommand(
    String venue,
    String symbol,
    Instant fundingTime,
    BigDecimal fundingRatePct,
    String sourceType,
    String sourceRef,
    Long signalCandidateId
)
{
}
