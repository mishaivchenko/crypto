package com.crypto.funding.contract.engine;

public record EngineExecutionTargetRequest(
    Long armedTradeId,
    EngineExecutionTargetPhase phase,
    Boolean force
)
{
}
