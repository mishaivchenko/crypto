package com.crypto.funding.api.dto;

import com.crypto.funding.domain.venue.VenueAccessMode;

import java.math.BigDecimal;

public record DevTestRunResponse(
    Long fundingEventId,
    Long armedTradeId,
    VenueAccessMode mode,
    String venue,
    String symbol,
    BigDecimal notionalUsd,
    String status
)
{
}
