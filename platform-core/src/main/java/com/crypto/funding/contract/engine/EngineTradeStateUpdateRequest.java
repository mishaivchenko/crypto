package com.crypto.funding.contract.engine;

import com.crypto.funding.domain.trade.ArmedTradeState;

public record EngineTradeStateUpdateRequest(
    ArmedTradeState state,
    String note
)
{
}
