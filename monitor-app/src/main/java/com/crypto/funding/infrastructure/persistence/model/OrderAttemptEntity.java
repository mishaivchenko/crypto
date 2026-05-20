package com.crypto.funding.infrastructure.persistence.model;

import com.crypto.funding.domain.execution.ExecutionType;
import com.crypto.funding.domain.execution.OrderAttemptStatus;
import com.crypto.funding.domain.trade.TradeSide;
import com.crypto.funding.infrastructure.persistence.converter.InstantEpochMillisConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "order_attempt",
    indexes = {
        @Index(name = "idx_order_attempt_trade_id", columnList = "armed_trade_id"),
        @Index(name = "idx_order_attempt_key", columnList = "attempt_key", unique = true),
        @Index(name = "idx_order_attempt_status", columnList = "status")
    }
)
public class OrderAttemptEntity extends AuditableEntity
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", columnDefinition = "integer")
    private Long id;

    // Nullable at DDL level so existing SQLite databases can be migrated by hibernate update.
    // Application service still requires a non-blank key before persisting new attempts.
    @Column(name = "attempt_key", length = 240)
    private String attemptKey;

    @Column(name = "armed_trade_id", nullable = false)
    private Long armedTradeId;

    @Column(name = "attempt_number")
    private Integer attemptNumber;

    @Column(name = "venue", nullable = false)
    private String venue;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false)
    private TradeSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_type", nullable = false)
    private ExecutionType executionType;

    @Column(name = "quantity", nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(name = "limit_price", precision = 19, scale = 8)
    private BigDecimal limitPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderAttemptStatus status = OrderAttemptStatus.CREATED;

    @Column(name = "external_order_id")
    private String externalOrderId;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "target_entry_at")
    private Instant targetEntryAt;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "trigger_at")
    private Instant triggerAt;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Convert(converter = InstantEpochMillisConverter.class)
    @Column(name = "exchange_timestamp")
    private Instant exchangeTimestamp;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "average_fill_price", precision = 19, scale = 8)
    private BigDecimal averageFillPrice;

    @Column(name = "filled_quantity", precision = 19, scale = 8)
    private BigDecimal filledQuantity;

    @Column(name = "fee_usd", precision = 19, scale = 8)
    private BigDecimal feeUsd;

    @Column(name = "request_duration_ms")
    private Long requestDurationMs;

    // New field for tracking execution node identity
    @Column(name = "executor_node_id", length = 64)
    private String executorNodeId;

    public Long getId()
    {
        return id;
    }

    public String getAttemptKey()
    {
        return attemptKey;
    }

    public void setAttemptKey( String attemptKey )
    {
        this.attemptKey = attemptKey;
    }

    public Long getArmedTradeId()
    {
        return armedTradeId;
    }

    public void setArmedTradeId( Long armedTradeId )
    {
        this.armedTradeId = armedTradeId;
    }

    public Integer getAttemptNumber()
    {
        return attemptNumber;
    }

    public void setAttemptNumber( Integer attemptNumber )
    {
        this.attemptNumber = attemptNumber;
    }

    public String getVenue()
    {
        return venue;
    }

    public void setVenue( String venue )
    {
        this.venue = venue;
    }

    public String getSymbol()
    {
        return symbol;
    }

    public void setSymbol( String symbol )
    {
        this.symbol = symbol;
    }

    public TradeSide getSide()
    {
        return side;
    }

    public void setSide( TradeSide side )
    {
        this.side = side;
    }

    public ExecutionType getExecutionType()
    {
        return executionType;
    }

    public void setExecutionType( ExecutionType executionType )
    {
        this.executionType = executionType;
    }

    public BigDecimal getQuantity()
    {
        return quantity;
    }

    public void setQuantity( BigDecimal quantity )
    {
        this.quantity = quantity;
    }

    public BigDecimal getLimitPrice()
    {
        return limitPrice;
    }

    public void setLimitPrice( BigDecimal limitPrice )
    {
        this.limitPrice = limitPrice;
    }

    public OrderAttemptStatus getStatus()
    {
        return status;
    }

    public void setStatus( OrderAttemptStatus status )
    {
        this.status = status;
    }

    public String getExternalOrderId()
    {
        return externalOrderId;
    }

    public void setExternalOrderId( String externalOrderId )
    {
        this.externalOrderId = externalOrderId;
    }

    public Instant getTargetEntryAt()
    {
        return targetEntryAt;
    }

    public void setTargetEntryAt( Instant targetEntryAt )
    {
        this.targetEntryAt = targetEntryAt;
    }

    public Instant getTriggerAt()
    {
        return triggerAt;
    }

    public void setTriggerAt( Instant triggerAt )
    {
        this.triggerAt = triggerAt;
    }

    public Instant getSubmittedAt()
    {
        return submittedAt;
    }

    public void setSubmittedAt( Instant submittedAt )
    {
        this.submittedAt = submittedAt;
    }

    public Instant getExchangeTimestamp()
    {
        return exchangeTimestamp;
    }

    public void setExchangeTimestamp( Instant exchangeTimestamp )
    {
        this.exchangeTimestamp = exchangeTimestamp;
    }

    public String getFailureReason()
    {
        return failureReason;
    }

    public void setFailureReason( String failureReason )
    {
        this.failureReason = failureReason;
    }

    public BigDecimal getAverageFillPrice()
    {
        return averageFillPrice;
    }

    public void setAverageFillPrice( BigDecimal averageFillPrice )
    {
        this.averageFillPrice = averageFillPrice;
    }

    public BigDecimal getFilledQuantity()
    {
        return filledQuantity;
    }

    public void setFilledQuantity( BigDecimal filledQuantity )
    {
        this.filledQuantity = filledQuantity;
    }

    public BigDecimal getFeeUsd()
    {
        return feeUsd;
    }

    public void setFeeUsd( BigDecimal feeUsd )
    {
        this.feeUsd = feeUsd;
    }

    public Long getRequestDurationMs()
    {
        return requestDurationMs;
    }

    public void setRequestDurationMs( Long requestDurationMs )
    {
        this.requestDurationMs = requestDurationMs;
    }

    public String getExecutorNodeId()
    {
        return executorNodeId;
    }

    public void setExecutorNodeId( String executorNodeId )
    {
        this.executorNodeId = executorNodeId;
    }
}
