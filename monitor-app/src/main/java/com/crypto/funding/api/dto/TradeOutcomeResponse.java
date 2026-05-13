package com.crypto.funding.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeOutcomeResponse(
    BigDecimal grossPnlUsd,
    BigDecimal netPnlUsd,
    BigDecimal feesUsd,
    String outcomeCode,
    Instant evaluatedAt
)
{
}
