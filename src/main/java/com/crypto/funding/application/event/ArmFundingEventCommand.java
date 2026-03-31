package com.crypto.funding.application.event;

import com.crypto.funding.domain.trade.TradeSide;

import java.math.BigDecimal;
import java.time.Instant;

public record ArmFundingEventCommand(
    BigDecimal notionalUsd,
    TradeSide intendedSide,
    Instant plannedEntryAt,
    Instant plannedExitAt,
    String notes
)
{
}
