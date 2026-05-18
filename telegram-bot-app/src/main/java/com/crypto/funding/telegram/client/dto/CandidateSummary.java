package com.crypto.funding.telegram.client.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CandidateSummary(
    Long id,
    String sourceType,
    Long sourceChatId,
    Long sourceMessageId,
    String sourceVenue,
    String rawSymbol,
    String normalizedSymbol,
    List<String> venueHints,
    Instant detectedAt,
    String status,
    String reviewDecision,
    Instant sourceFundingTime,
    BigDecimal sourceFundingRatePct,
    Long fundingEventId,
    String normalizationFailureReason
)
{
    public String displaySymbol()
    {
        return normalizedSymbol != null ? normalizedSymbol : rawSymbol;
    }

    public String displayVenue()
    {
        if( sourceVenue != null && !sourceVenue.isBlank() )
        {
            return sourceVenue;
        }
        if( venueHints != null && !venueHints.isEmpty() )
        {
            return venueHints.getFirst();
        }
        return "—";
    }
}
