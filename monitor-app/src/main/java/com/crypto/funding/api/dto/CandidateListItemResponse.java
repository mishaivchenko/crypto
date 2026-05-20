package com.crypto.funding.api.dto;

import com.crypto.funding.domain.candidate.ReviewDecision;
import com.crypto.funding.domain.candidate.SignalCandidateStatus;

import java.math.BigDecimal;
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
    Instant sourceFundingTime,
    BigDecimal sourceFundingRatePct,
    Long fundingEventId,
    String normalizationFailureReason,
    AiAdviceDto aiAdvice
)
{
}
