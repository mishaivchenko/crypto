package com.crypto.funding.domain.execution;

import com.crypto.funding.domain.trade.TradeSide;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderAttempt(
    Long id,
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
    Instant createdAt,
    Instant updatedAt
)
{
    public OrderAttempt(
        Long id,
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
        Instant createdAt,
        Instant updatedAt
    )
    {
        this(
            id,
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
            createdAt,
            updatedAt
        );
    }

    public OrderAttempt
    {
        if( armedTradeId == null )
        {
            throw new IllegalArgumentException( "armedTradeId must not be null" );
        }
        if( attemptNumber != null && attemptNumber < 1 )
        {
            throw new IllegalArgumentException( "attemptNumber must be positive" );
        }
        if( venue == null || venue.isBlank() )
        {
            throw new IllegalArgumentException( "venue must not be blank" );
        }
        if( symbol == null || symbol.isBlank() )
        {
            throw new IllegalArgumentException( "symbol must not be blank" );
        }
        if( side == null )
        {
            throw new IllegalArgumentException( "side must not be null" );
        }
        if( executionType == null )
        {
            throw new IllegalArgumentException( "executionType must not be null" );
        }
        if( quantity == null || quantity.signum() <= 0 )
        {
            throw new IllegalArgumentException( "quantity must be positive" );
        }
        if( status == null )
        {
            throw new IllegalArgumentException( "status must not be null" );
        }
    }
}
