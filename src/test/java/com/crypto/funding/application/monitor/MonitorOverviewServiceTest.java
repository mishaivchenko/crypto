package com.crypto.funding.application.monitor;

import com.crypto.funding.application.venue.VenueDiagnosticsService;
import com.crypto.funding.domain.candidate.SignalCandidateStatus;
import com.crypto.funding.domain.event.FundingEventStatus;
import com.crypto.funding.infrastructure.persistence.model.ArmedTradeEntity;
import com.crypto.funding.infrastructure.persistence.model.FundingEventEntity;
import com.crypto.funding.infrastructure.persistence.model.SignalCandidateEntity;
import com.crypto.funding.infrastructure.persistence.repository.ArmedTradeJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.SignalCandidateJpaRepository;
import com.crypto.funding.infrastructure.telemetry.VenueRequestTimingService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonitorOverviewServiceTest
{
    @Test
    void aggregatesCountsAndVenueTimingSummary()
    {
        SignalCandidateJpaRepository candidateRepository = mock( SignalCandidateJpaRepository.class );
        FundingEventJpaRepository fundingEventRepository = mock( FundingEventJpaRepository.class );
        ArmedTradeJpaRepository armedTradeRepository = mock( ArmedTradeJpaRepository.class );
        VenueDiagnosticsService venueDiagnosticsService = mock( VenueDiagnosticsService.class );

        SignalCandidateEntity pending = new SignalCandidateEntity();
        pending.setStatus( SignalCandidateStatus.NORMALIZED );
        SignalCandidateEntity terminal = new SignalCandidateEntity();
        terminal.setStatus( SignalCandidateStatus.EVENT_CREATED );
        when( candidateRepository.findAll() ).thenReturn( List.of( pending, terminal ) );

        FundingEventEntity discovered = new FundingEventEntity();
        discovered.setStatus( FundingEventStatus.DISCOVERED );
        FundingEventEntity armed = new FundingEventEntity();
        armed.setStatus( FundingEventStatus.ARMED );
        when( fundingEventRepository.count() ).thenReturn( 2L );
        when( fundingEventRepository.findAll() ).thenReturn( List.of( discovered, armed ) );

        when( armedTradeRepository.count() ).thenReturn( 3L );

        when( venueDiagnosticsService.listVenues() ).thenReturn( List.of(
            new VenueDiagnosticsService.VenueSummary(
                "binance",
                "production",
                "https://fapi.binance.com",
                null,
                true,
                true,
                true,
                12,
                Instant.parse( "2030-01-01T00:00:00Z" )
            )
        ) );
        when( venueDiagnosticsService.listTimings() ).thenReturn( List.of(
            new VenueRequestTimingService.Snapshot(
                "binance",
                "metadata-sync",
                3,
                3,
                0,
                120,
                118,
                200,
                null,
                Instant.parse( "2030-01-01T00:05:00Z" ),
                12
            )
        ) );

        MonitorOverviewService service = new MonitorOverviewService(
            candidateRepository,
            fundingEventRepository,
            armedTradeRepository,
            venueDiagnosticsService
        );

        MonitorOverviewService.Overview overview = service.load();

        assertThat( overview.version() ).isEqualTo( "2.0.0" );
        assertThat( overview.pendingCandidates() ).isEqualTo( 1 );
        assertThat( overview.fundingEvents() ).isEqualTo( 2 );
        assertThat( overview.discoveredEvents() ).isEqualTo( 1 );
        assertThat( overview.armedTrades() ).isEqualTo( 3 );
        assertThat( overview.activeVenues() ).isEqualTo( 1 );
        assertThat( overview.venues() ).singleElement().satisfies( venue -> {
            assertThat( venue.venue() ).isEqualTo( "binance" );
            assertThat( venue.averageRequestTimeMs() ).isEqualTo( 120 );
            assertThat( venue.requests() ).isEqualTo( 3 );
        } );
    }
}
