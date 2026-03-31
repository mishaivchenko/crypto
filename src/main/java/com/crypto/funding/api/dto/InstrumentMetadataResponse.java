package com.crypto.funding.api.dto;

import com.crypto.funding.domain.venue.InstrumentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record InstrumentMetadataResponse(
    Long id,
    String venue,
    String canonicalSymbol,
    String venueSymbol,
    String baseAsset,
    String quoteAsset,
    String instrumentType,
    InstrumentStatus status,
    BigDecimal minOrderQty,
    BigDecimal qtyStep,
    BigDecimal minNotionalValue,
    Integer quantityPrecision,
    Instant lastSyncedAt
)
{
}
