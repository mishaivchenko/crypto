package com.crypto.funding.api.dto;

import com.crypto.funding.domain.candidate.ReviewDecision;
import com.crypto.funding.domain.candidate.SignalCandidateStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CandidateResponse(
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
    Long fundingEventId,
    String suggestedVenue,
    Instant suggestedFundingTime,
    BigDecimal suggestedFundingRatePct,
    Instant createdAt,
    Instant updatedAt
)
{
}
