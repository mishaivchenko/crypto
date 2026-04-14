package com.crypto.funding.application.monitor;

import com.crypto.funding.application.venue.VenueDiagnosticsService;
import com.crypto.funding.application.venue.VenueProfileService;
import com.crypto.funding.application.event.FundingEventLifecycleService;
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
import org.springframework.test.util.ReflectionTestUtils;

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
        FundingEventLifecycleService fundingEventLifecycleService = mock( FundingEventLifecycleService.class );
        VenueProfileService venueProfileService = mock( VenueProfileService.class );

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

        ArmedTradeEntity tradeOne = new ArmedTradeEntity();
        tradeOne.setFundingEventId( 1L );
        ArmedTradeEntity tradeTwo = new ArmedTradeEntity();
        tradeTwo.setFundingEventId( 2L );
        ArmedTradeEntity staleTrade = new ArmedTradeEntity();
        staleTrade.setFundingEventId( 999L );
        ReflectionTestUtils.setField( discovered, "id", 1L );
        ReflectionTestUtils.setField( armed, "id", 2L );
        when( armedTradeRepository.findAll() ).thenReturn( List.of( tradeOne, tradeTwo, staleTrade ) );

        when( venueDiagnosticsService.listVenues() ).thenReturn( List.of(
            new VenueDiagnosticsService.VenueSummary(
                "bybit",
                "production",
                "https://api.bybit.com",
                null,
                true,
                true,
                true,
                false,
                true,
                false,
                List.of( com.crypto.funding.domain.venue.VenueAccessMode.TESTNET, com.crypto.funding.domain.venue.VenueAccessMode.PRODUCTION ),
                com.crypto.funding.domain.venue.VenueConnectionStatus.NOT_CONNECTED,
                "Ключи не подключены.",
                null,
                null,
                true,
                true,
                12,
                Instant.parse( "2030-01-01T00:00:00Z" )
            )
        ) );
        when( venueDiagnosticsService.listTimings() ).thenReturn( List.of(
            new VenueRequestTimingService.Snapshot(
                "bybit",
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
        when( venueProfileService.getGlobalAccessProfile() ).thenReturn(
            new VenueProfileService.GlobalAccessProfile(
                com.crypto.funding.domain.venue.VenueAccessMode.TESTNET,
                true,
                List.of(
                    com.crypto.funding.domain.venue.VenueAccessMode.TESTNET,
                    com.crypto.funding.domain.venue.VenueAccessMode.PRODUCTION
                )
            )
        );

        MonitorOverviewService service = new MonitorOverviewService(
            candidateRepository,
            fundingEventRepository,
            armedTradeRepository,
            venueDiagnosticsService,
            fundingEventLifecycleService,
            venueProfileService
        );

        MonitorOverviewService.Overview overview = service.load();

        assertThat( overview.version() ).isEqualTo( "2.0.0" );
        assertThat( overview.globalAccessMode() ).isEqualTo( "testnet" );
        assertThat( overview.pendingCandidates() ).isEqualTo( 1 );
        assertThat( overview.fundingEvents() ).isEqualTo( 2 );
        assertThat( overview.discoveredEvents() ).isEqualTo( 1 );
        assertThat( overview.armedTrades() ).isEqualTo( 2 );
        assertThat( overview.activeVenues() ).isEqualTo( 1 );
        assertThat( overview.venues() ).singleElement().satisfies( venue -> {
            assertThat( venue.venue() ).isEqualTo( "bybit" );
            assertThat( venue.averageRequestTimeMs() ).isEqualTo( 120 );
            assertThat( venue.requests() ).isEqualTo( 3 );
        } );
    }
}
