package com.crypto.funding.contract.engine;

import com.crypto.funding.domain.execution.OrderAttemptStatus;

import java.time.Instant;

public record EngineExecutionAttemptResult(
    Long armedTradeId,
    Integer attemptNumber,
    String attemptKey,
    OrderAttemptStatus status,
    String failureReason,
    Instant recordedAt
)
{
}
