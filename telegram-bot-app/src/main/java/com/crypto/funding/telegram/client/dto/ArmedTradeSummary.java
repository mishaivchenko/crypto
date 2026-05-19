package com.crypto.funding.telegram.client.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ArmedTradeSummary(
    Long id,
    String venue,
    String symbol,
    String venueSymbol,
    BigDecimal notionalUsd,
    String intendedSide,
    Instant plannedEntryAt,
    Instant armedAt,
    String state
)
{
    public String displaySymbol()
    {
        return venueSymbol != null ? venueSymbol : symbol;
    }
}
