package com.crypto.funding.persistence.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
    name = "order_execution_time",
    indexes = {
        @Index(name = "idx_order_exec_exchange", columnList = "exchange"),
        @Index(name = "idx_order_exec_created_at", columnList = "created_at")
    }
)
public class OrderExecutionTimeEntity
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "exchange", nullable = false)
    private String exchange;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "server_received_at", nullable = false)
    private Long serverReceivedAt;

    @Column(name = "exchange_executed_at")
    private Long exchangeExecutedAt;

    @Column(name = "timestamp_source", nullable = false)
    private String timestampSource;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "funding_at", nullable = false)
    private Instant fundingAt;

    protected OrderExecutionTimeEntity() {}

    public OrderExecutionTimeEntity(String exchange, String orderId, String symbol, Long serverReceivedAt,
                                    Long exchangeExecutedAt, String timestampSource, Instant fundingAt)
    {
        this.exchange = exchange;
        this.orderId = orderId;
        this.symbol = symbol;
        this.serverReceivedAt = serverReceivedAt;
        this.exchangeExecutedAt = exchangeExecutedAt;
        this.timestampSource = timestampSource;
        this.fundingAt = fundingAt;
    }

    @PrePersist
    void onCreate()
    {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate()
    {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getExchange() { return exchange; }
    public String getOrderId() { return orderId; }
    public String getSymbol() { return symbol; }
    public Long getServerReceivedAt() { return serverReceivedAt; }
    public Long getExchangeExecutedAt() { return exchangeExecutedAt; }
    public String getTimestampSource() { return timestampSource; }
}
