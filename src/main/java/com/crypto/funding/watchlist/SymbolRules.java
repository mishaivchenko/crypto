package com.crypto.funding.watchlist;

import java.math.BigDecimal;

public record SymbolRules(
    BigDecimal minOrderQty,
    BigDecimal qtyStep,
    BigDecimal minNotionalValue // nullable
) {}
