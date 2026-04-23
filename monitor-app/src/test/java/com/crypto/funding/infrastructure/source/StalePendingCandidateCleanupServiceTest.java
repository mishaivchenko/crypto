package com.crypto.funding.infrastructure.source;

import com.crypto.funding.config.FundingCandidateSourceProperties;
import com.crypto.funding.domain.candidate.SignalCandidateStatus;
import com.crypto.funding.infrastructure.persistence.model.SignalCandidateEntity;
import com.crypto.funding.infrastructure.persistence.repository.SignalCandidateJpaRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StalePendingCandidateCleanupServiceTest
{
    @Test
    void deletesOnlyPendingCandidatesMissingFromActiveSnapshot()
    {
        SignalCandidateJpaRepository repository = mock( SignalCandidateJpaRepository.class );
        FundingCandidateSourceProperties properties = new FundingCandidateSourceProperties();
        properties.setSourceType( "FUNDING_API" );
        StalePendingCandidateCleanupService cleanupService = new StalePendingCandidateCleanupService( repository, properties );

        SignalCandidateEntity missingPending = candidate( 101L, SignalCandidateStatus.NEW, null );
        SignalCandidateEntity stillActive = candidate( 202L, SignalCandidateStatus.NORMALIZED, null );
        SignalCandidateEntity rejected = candidate( 303L, SignalCandidateStatus.REJECTED, null );
        SignalCandidateEntity createdEvent = candidate( 404L, SignalCandidateStatus.EVENT_CREATED, 55L );

        when( repository.findAllBySourceTypeAndSourceChatIdAndFundingEventIdIsNullOrderByDetectedAtDesc( "FUNDING_API", 1L ) )
            .thenReturn( List.of( missingPending, stillActive, rejected, createdEvent ) );

        cleanupService.deleteMissingPendingCandidates( Set.of( 202L ) );

        verify( repository ).deleteAll( List.of( missingPending ) );
    }

    @Test
    void skipsDeleteWhenNothingStaleRemains()
    {
        SignalCandidateJpaRepository repository = mock( SignalCandidateJpaRepository.class );
        FundingCandidateSourceProperties properties = new FundingCandidateSourceProperties();
        properties.setSourceType( "FUNDING_API" );
        StalePendingCandidateCleanupService cleanupService = new StalePendingCandidateCleanupService( repository, properties );

        when( repository.findAllBySourceTypeAndSourceChatIdAndFundingEventIdIsNullOrderByDetectedAtDesc( "FUNDING_API", 1L ) )
            .thenReturn( List.of( candidate( 202L, SignalCandidateStatus.NORMALIZED, null ) ) );

        cleanupService.deleteMissingPendingCandidates( Set.of( 202L ) );

        verify( repository, never() ).deleteAll( anyList() );
    }

    private static SignalCandidateEntity candidate( Long sourceMessageId, SignalCandidateStatus status, Long fundingEventId )
    {
        SignalCandidateEntity entity = new SignalCandidateEntity();
        entity.setSourceMessageId( sourceMessageId );
        entity.setStatus( status );
        entity.setFundingEventId( fundingEventId );
        entity.setDetectedAt( Instant.parse( "2030-01-01T00:00:00Z" ) );
        return entity;
    }
}
