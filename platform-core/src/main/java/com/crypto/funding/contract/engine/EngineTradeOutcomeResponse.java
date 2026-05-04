package com.crypto.funding.contract.engine;

import java.math.BigDecimal;
import java.time.Instant;

public record EngineTradeOutcomeResponse(
    Long id,
    Long armedTradeId,
    BigDecimal grossPnlUsd,
    BigDecimal netPnlUsd,
    BigDecimal feesUsd,
    String outcomeCode,
    String notes,
    Instant evaluatedAt,
    Instant createdAt,
    Instant updatedAt
)
{
}
