package com.crypto.funding.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TradePositionResponse(
    String state,
    BigDecimal quantity,
    BigDecimal entryPrice,
    BigDecimal exitPrice,
    Instant openedAt,
    Instant closedAt
)
{
}
