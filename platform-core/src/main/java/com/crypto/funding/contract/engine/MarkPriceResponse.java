package com.crypto.funding.contract.engine;

import java.math.BigDecimal;
import java.time.Instant;

public record MarkPriceResponse(
    String venue,
    String symbol,
    BigDecimal markPrice,
    Instant fetchedAt
)
{
}
