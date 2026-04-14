package com.crypto.funding.api.dto;

import com.crypto.funding.domain.candidate.ReviewDecision;
import com.crypto.funding.domain.candidate.SignalCandidateStatus;

import java.time.Instant;
import java.util.List;

public record CandidateListItemResponse(
    Long id,
    String sourceType,
    Long sourceChatId,
    Long sourceMessageId,
    String sourceVenue,
    String rawSymbol,
    String normalizedSymbol,
    List<String> venueHints,
    Instant detectedAt,
    SignalCandidateStatus status,
    ReviewDecision reviewDecision,
    Long fundingEventId,
    String normalizationFailureReason
)
{
}
