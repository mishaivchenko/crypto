package com.crypto.funding.contract.engine;

import com.crypto.funding.domain.execution.ExecutionType;
import com.crypto.funding.domain.execution.OrderAttemptStatus;
import com.crypto.funding.domain.trade.TradeSide;

import java.math.BigDecimal;
import java.time.Instant;

public record EngineOrderAttemptRecordRequest(
    String attemptKey,
    Long armedTradeId,
    Integer attemptNumber,
    String venue,
    String symbol,
    TradeSide side,
    ExecutionType executionType,
    BigDecimal quantity,
    BigDecimal limitPrice,
    OrderAttemptStatus status,
    String externalOrderId,
    Instant targetEntryAt,
    Instant triggerAt,
    Instant submittedAt,
    Instant exchangeTimestamp,
    String failureReason,
    BigDecimal averageFillPrice,
    BigDecimal filledQuantity,
    BigDecimal feeUsd,
    Long requestDurationMs
)
{
    public EngineOrderAttemptRecordRequest(
        String attemptKey,
        Long armedTradeId,
        Integer attemptNumber,
        String venue,
        String symbol,
        TradeSide side,
        ExecutionType executionType,
        BigDecimal quantity,
        BigDecimal limitPrice,
        OrderAttemptStatus status,
        String externalOrderId,
        Instant targetEntryAt,
        Instant triggerAt,
        Instant submittedAt,
        Instant exchangeTimestamp,
        String failureReason
    )
    {
        this(
            attemptKey,
            armedTradeId,
            attemptNumber,
            venue,
            symbol,
            side,
            executionType,
            quantity,
            limitPrice,
            status,
            externalOrderId,
            targetEntryAt,
            triggerAt,
            submittedAt,
            exchangeTimestamp,
            failureReason,
            null,
            null,
            null,
            null
        );
    }
}
