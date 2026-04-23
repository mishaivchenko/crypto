package com.crypto.funding.infrastructure.source;

import java.math.BigDecimal;
import java.time.Instant;

record FundingObservation(
    Instant detectedAt,
    String symbol,
    Instant fundingTime,
    BigDecimal fundingRatePct
)
{
}
