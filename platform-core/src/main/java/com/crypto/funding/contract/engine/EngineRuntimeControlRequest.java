package com.crypto.funding.contract.engine;

public record EngineRuntimeControlRequest(
    Boolean executionLoopEnabled,
    Long executionLoopIntervalMs,
    Boolean liveOrderEnabled,
    Boolean killSwitchEnabled
)
{
}
