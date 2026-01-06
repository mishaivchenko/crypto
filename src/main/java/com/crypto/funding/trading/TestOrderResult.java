package com.crypto.funding.trading;

import java.math.BigDecimal;

public record TestOrderResult(
    String exchange,        // "binance"
    String exchangeOrderId, // может быть null в MOCK
    String symbolUnified,
    OrderSide side,
    OrderType type,
    BigDecimal quantity,
    BigDecimal price,
    String status,          // "NEW", "FILLED", "REJECTED", "MOCKED"
    long tsMillis
)
{

}
