package com.crypto.funding.contract.engine;

import com.crypto.funding.domain.trade.PositionState;
import com.crypto.funding.domain.trade.TradeSide;

import java.math.BigDecimal;
import java.time.Instant;

public record EnginePositionResponse(
    Long id,
    Long armedTradeId,
    String venue,
    String symbol,
    TradeSide side,
    BigDecimal quantity,
    BigDecimal entryPrice,
    BigDecimal exitPrice,
    PositionState state,
    Instant openedAt,
    Instant closedAt,
    Instant createdAt,
    Instant updatedAt
)
{
}
