package com.crypto.funding.infrastructure.persistence.mapper;

import com.crypto.funding.domain.candidate.SignalCandidate;
import com.crypto.funding.infrastructure.persistence.model.SignalCandidateEntity;

public final class SignalCandidateMapper
{
    private SignalCandidateMapper()
    {
    }

    public static SignalCandidate toDomain( SignalCandidateEntity entity )
    {
        return new SignalCandidate(
            entity.getId(),
            entity.getSourceType(),
            entity.getSourceChatId(),
            entity.getSourceMessageId(),
            entity.getRawPayload(),
            entity.getSourceVenue(),
            entity.getRawSymbol(),
            entity.getNormalizedSymbol(),
            entity.getVenueHints(),
            entity.getDetectedAt(),
            entity.getStatus(),
            entity.getReviewedAt(),
            entity.getReviewDecision(),
            entity.getReviewNotes(),
            entity.getNormalizationFailureReason(),
            entity.getSourceFundingTime(),
            entity.getSourceFundingRatePct(),
            entity.getFundingEventId(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
