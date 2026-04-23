package com.crypto.funding.infrastructure.source;

import com.crypto.funding.config.FundingCandidateSourceProperties;
import com.crypto.funding.domain.candidate.SignalCandidateStatus;
import com.crypto.funding.infrastructure.persistence.model.SignalCandidateEntity;
import com.crypto.funding.infrastructure.persistence.repository.SignalCandidateJpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
class StalePendingCandidateCleanupService
{
    private static final long SOURCE_STREAM_ID = 1L;

    private final SignalCandidateJpaRepository signalCandidateRepository;
    private final FundingCandidateSourceProperties sourceProperties;

    StalePendingCandidateCleanupService(
        SignalCandidateJpaRepository signalCandidateRepository,
        FundingCandidateSourceProperties sourceProperties
    )
    {
        this.signalCandidateRepository = signalCandidateRepository;
        this.sourceProperties = sourceProperties;
    }

    void deleteMissingPendingCandidates( Set<Long> activeSourceMessageIds )
    {
        String sourceType = sourceProperties.getSourceType().trim().toUpperCase( Locale.ROOT );
        List<SignalCandidateEntity> staleCandidates = signalCandidateRepository
            .findAllBySourceTypeAndSourceChatIdAndFundingEventIdIsNullOrderByDetectedAtDesc( sourceType, SOURCE_STREAM_ID )
            .stream()
            .filter( candidate -> candidate.getSourceMessageId() != null )
            .filter( candidate -> !activeSourceMessageIds.contains( candidate.getSourceMessageId() ) )
            .filter( candidate -> candidate.getStatus() != SignalCandidateStatus.REJECTED )
            .filter( candidate -> candidate.getStatus() != SignalCandidateStatus.EVENT_CREATED )
            .toList();

        if( !staleCandidates.isEmpty() )
        {
            signalCandidateRepository.deleteAll( staleCandidates );
        }
    }
}
