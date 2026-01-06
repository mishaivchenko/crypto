package com.crypto.funding.trading;

import java.math.BigDecimal;

public record PlaceTestOrderCommand(String exchange,      // "binance", "bybit", "gate"
                                    String symbolUnified, // "BTCUSDT" в твоей unified-нотации
                                    OrderSide side,
                                    OrderType type,
                                    BigDecimal quantity,
                                    BigDecimal price)      // null для MARKET)
{
}
