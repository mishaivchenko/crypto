package com.crypto.funding.application.port;

import java.math.BigDecimal;

public record SymbolMetadata(
    String venue,
    String symbol,
    BigDecimal minOrderQty,
    BigDecimal qtyStep,
    BigDecimal minNotionalValue
)
{
}
