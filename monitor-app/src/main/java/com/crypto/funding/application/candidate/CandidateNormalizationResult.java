package com.crypto.funding.application.candidate;

import java.util.List;

public record CandidateNormalizationResult(
    String normalizedSymbol,
    List<String> venueHints,
    String failureReason
)
{
    public boolean isNormalized()
    {
        return normalizedSymbol != null && !normalizedSymbol.isBlank() && failureReason == null;
    }
}
