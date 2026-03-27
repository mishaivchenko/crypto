package com.crypto.funding.application.trade;

import com.crypto.funding.domain.trade.TradeSide;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateArmedTradeCommand(
    Long fundingEventId,
    BigDecimal notionalUsd,
    TradeSide intendedSide,
    Instant plannedEntryAt,
    Instant plannedExitAt,
    String notes
)
{
}
