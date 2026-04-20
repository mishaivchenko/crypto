package com.crypto.funding.domain.candidate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SignalCandidate(
    Long id,
    String sourceType,
    Long sourceChatId,
    Long sourceMessageId,
    String rawPayload,
    String sourceVenue,
    String rawSymbol,
    String normalizedSymbol,
    List<String> venueHints,
    Instant detectedAt,
    SignalCandidateStatus status,
    Instant reviewedAt,
    ReviewDecision reviewDecision,
    String reviewNotes,
    String normalizationFailureReason,
    Instant sourceFundingTime,
    BigDecimal sourceFundingRatePct,
    Long fundingEventId,
    Instant createdAt,
    Instant updatedAt
)
{
    public SignalCandidate
    {
        if( sourceType == null || sourceType.isBlank() )
        {
            throw new IllegalArgumentException( "sourceType must not be blank" );
        }
        if( rawSymbol == null || rawSymbol.isBlank() )
        {
            throw new IllegalArgumentException( "rawSymbol must not be blank" );
        }
        if( detectedAt == null )
        {
            throw new IllegalArgumentException( "detectedAt must not be null" );
        }
        if( status == null )
        {
            throw new IllegalArgumentException( "status must not be null" );
        }
        venueHints = venueHints == null ? List.of() : List.copyOf( venueHints );
    }
}
