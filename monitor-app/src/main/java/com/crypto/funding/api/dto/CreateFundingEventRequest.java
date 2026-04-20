package com.crypto.funding.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateFundingEventRequest(
    @NotBlank String venue,
    @NotBlank String symbol,
    @NotNull Instant fundingTime,
    BigDecimal fundingRatePct,
    String sourceType,
    String sourceRef
)
{
}
