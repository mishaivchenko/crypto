package com.crypto.funding.api.dto;

import com.crypto.funding.domain.trade.TradeSide;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record ArmFundingEventRequest(
    @NotNull @Positive BigDecimal notionalUsd,
    TradeSide intendedSide,
    Instant plannedEntryAt,
    Instant plannedExitAt,
    @Size(max = 1000) String notes
)
{
}
