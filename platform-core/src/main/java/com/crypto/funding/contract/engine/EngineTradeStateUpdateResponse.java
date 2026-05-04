package com.crypto.funding.contract.engine;

import com.crypto.funding.domain.trade.ArmedTradeState;

import java.time.Instant;

public record EngineTradeStateUpdateResponse(
    Long armedTradeId,
    ArmedTradeState state,
    Instant updatedAt
)
{
}
