package com.crypto.funding.trading;

import java.math.BigDecimal;
import java.time.Instant;

public record TestOrderResult(
    String exchange,        // "binance"
    String exchangeOrderId, // может быть null в MOCK
    String symbolUnified,
    OrderSide side,
    OrderType type,
    BigDecimal quantity,
    BigDecimal price,
    String status,          // "NEW", "FILLED", "REJECTED", "MOCKED"
    long tsMillis,          // серверное время получения ответа
    Long exchangeTsMillis,  // время открытия ордера на бирже (мс), если есть
    OrderTimestampSource timestampSource
)
{
    public TestOrderResult
    {
        if (timestampSource == null)
        {
            timestampSource = OrderTimestampSource.UNKNOWN;
        }
    }

    public Instant serverReceivedAt()
    {
        return Instant.ofEpochMilli( tsMillis );
    }

    public Instant exchangeExecutedAt()
    {
        return exchangeTsMillis == null ? null : Instant.ofEpochMilli( exchangeTsMillis );
    }

    public TestOrderResult withTsMillis( long newTsMillis )
    {
        return new TestOrderResult( exchange, exchangeOrderId, symbolUnified, side, type, quantity, price, status, newTsMillis, exchangeTsMillis, timestampSource );
    }

    public TestOrderResult withExchangeTimestamp( Long newExchangeTsMillis, OrderTimestampSource source )
    {
        OrderTimestampSource nextSource = source == null ? this.timestampSource : source;
        return new TestOrderResult( exchange, exchangeOrderId, symbolUnified, side, type, quantity, price, status, tsMillis, newExchangeTsMillis, nextSource );
    }
}
