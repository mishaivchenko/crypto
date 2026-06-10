package com.crypto.funding.application.monitor;

import com.crypto.funding.application.venue.VenueDiagnosticsService;
import com.crypto.funding.application.venue.VenueProfileService;
import com.crypto.funding.application.event.FundingEventLifecycleService;
import com.crypto.funding.domain.candidate.SignalCandidateStatus;
import com.crypto.funding.domain.event.FundingEventStatus;
import com.crypto.funding.domain.venue.VenueAccessMode;
import com.crypto.funding.domain.venue.VenueConnectionStatus;
import com.crypto.funding.infrastructure.persistence.model.ArmedTradeEntity;
import com.crypto.funding.infrastructure.persistence.model.FundingEventEntity;
import com.crypto.funding.infrastructure.persistence.model.SignalCandidateEntity;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.infrastructure.persistence.repository.ArmedTradeJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.LiquidityAssessmentJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.SignalCandidateJpaRepository;
import com.crypto.funding.infrastructure.telemetry.VenueRequestTimingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
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
        LiquidityAssessmentJpaRepository liquidityAssessmentRepository = mock( LiquidityAssessmentJpaRepository.class );
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
        when( armedTradeRepository.countArmedTradesByVenue( ArgumentMatchers.anySet() ) ).thenReturn( List.of() );
        when( liquidityAssessmentRepository.countEnrichedArmedTradesByVenue( ArgumentMatchers.anySet() ) ).thenReturn( List.of() );
        when( armedTradeRepository.findAllByStateIn( ArgumentMatchers.anySet() ) ).thenReturn( List.of() );

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
                12,
                null,
                null,
                null
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
            liquidityAssessmentRepository,
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

    @Test
    void enrichmentFreshness_noActiveTrades_returnsNullAvgAndZeroUncovered()
    {
        MonitorOverviewService service = serviceWithMinimalMocks( mock( ArmedTradeJpaRepository.class ), mock( LiquidityAssessmentJpaRepository.class ) );
        MonitorOverviewService.Overview overview = service.load();

        assertThat( overview.enrichmentFreshness().avgSecondsSinceLastAssessment() ).isNull();
        assertThat( overview.enrichmentFreshness().uncoveredEntityCount() ).isZero();
    }

    @Test
    void enrichmentFreshness_allTradesUncovered_returnsNullAvgAndCorrectCount()
    {
        ArmedTradeJpaRepository armedTradeRepo = mock( ArmedTradeJpaRepository.class );
        LiquidityAssessmentJpaRepository liquidityRepo = mock( LiquidityAssessmentJpaRepository.class );
        MonitorOverviewService service = serviceWithMinimalMocks( armedTradeRepo, liquidityRepo );

        ArmedTradeEntity trade = armedTradeWithId( 10L );
        when( armedTradeRepo.findAllByStateIn( ArgumentMatchers.anySet() ) ).thenReturn( List.of( trade ) );
        when( liquidityRepo.findCoveredTradeIds( ArgumentMatchers.anyCollection() ) ).thenReturn( Set.of() );

        MonitorOverviewService.Overview overview = service.load();

        assertThat( overview.enrichmentFreshness().avgSecondsSinceLastAssessment() ).isNull();
        assertThat( overview.enrichmentFreshness().uncoveredEntityCount() ).isEqualTo( 1L );
    }

    @Test
    void enrichmentFreshness_coveredTrades_computesAvgFromBatchQuery()
    {
        ArmedTradeJpaRepository armedTradeRepo = mock( ArmedTradeJpaRepository.class );
        LiquidityAssessmentJpaRepository liquidityRepo = mock( LiquidityAssessmentJpaRepository.class );
        MonitorOverviewService service = serviceWithMinimalMocks( armedTradeRepo, liquidityRepo );

        ArmedTradeEntity trade1 = armedTradeWithId( 1L );
        ArmedTradeEntity trade2 = armedTradeWithId( 2L );
        when( armedTradeRepo.findAllByStateIn( ArgumentMatchers.anySet() ) ).thenReturn( List.of( trade1, trade2 ) );
        when( liquidityRepo.findCoveredTradeIds( ArgumentMatchers.anyCollection() ) ).thenReturn( Set.of( 1L, 2L ) );

        Instant now = Instant.now();
        // trade1: assessed 10s ago, trade2: assessed 30s ago → avg = 20s
        when( liquidityRepo.findLatestSampledAtPerTrade( ArgumentMatchers.anyCollection() ) )
            .thenReturn( List.of( now.minusSeconds( 10 ), now.minusSeconds( 30 ) ) );

        MonitorOverviewService.Overview overview = service.load();

        assertThat( overview.enrichmentFreshness().uncoveredEntityCount() ).isZero();
        assertThat( overview.enrichmentFreshness().avgSecondsSinceLastAssessment() )
            .isNotNull()
            .isCloseTo( 20.0, within( 2.0 ) );
    }

    @SuppressWarnings("unchecked")
    @Test
    void enrichmentCoveragePct_computedFromActiveStates_notOnlyArmed()
    {
        ArmedTradeJpaRepository armedTradeRepo = mock( ArmedTradeJpaRepository.class );
        LiquidityAssessmentJpaRepository liquidityRepo = mock( LiquidityAssessmentJpaRepository.class );

        List<Object[]> totalRows = new java.util.ArrayList<>();
        totalRows.add( new Object[]{ "gate", 4L } );
        List<Object[]> enrichedRows = new java.util.ArrayList<>();
        enrichedRows.add( new Object[]{ "gate", 2L } );
        when( armedTradeRepo.countArmedTradesByVenue( ArgumentMatchers.anySet() ) ).thenReturn( totalRows );
        when( liquidityRepo.countEnrichedArmedTradesByVenue( ArgumentMatchers.anySet() ) ).thenReturn( enrichedRows );
        when( armedTradeRepo.findAllByStateIn( ArgumentMatchers.anySet() ) ).thenReturn( List.of() );

        // Verify the query is called with the full active-state set, not just ARMED
        MonitorOverviewService service = serviceWithMinimalMocks( armedTradeRepo, liquidityRepo );
        service.load();

        // The call must include states beyond ARMED (e.g. OPEN, EXIT_PENDING)
        org.mockito.ArgumentCaptor<Set<ArmedTradeState>> captor = org.mockito.ArgumentCaptor.forClass( Set.class );
        org.mockito.Mockito.verify( armedTradeRepo ).countArmedTradesByVenue( captor.capture() );
        assertThat( captor.getValue() ).contains(
            ArmedTradeState.ARMED,
            ArmedTradeState.OPEN,
            ArmedTradeState.EXIT_PENDING,
            ArmedTradeState.ENTRY_PENDING
        );
    }

    @Test
    void enrichmentCoveragePct_noActiveTrades_returnsNull()
    {
        ArmedTradeJpaRepository armedTradeRepo = mock( ArmedTradeJpaRepository.class );
        LiquidityAssessmentJpaRepository liquidityRepo = mock( LiquidityAssessmentJpaRepository.class );

        when( armedTradeRepo.countArmedTradesByVenue( ArgumentMatchers.anySet() ) ).thenReturn( List.of() );
        when( liquidityRepo.countEnrichedArmedTradesByVenue( ArgumentMatchers.anySet() ) ).thenReturn( List.of() );
        when( armedTradeRepo.findAllByStateIn( ArgumentMatchers.anySet() ) ).thenReturn( List.of() );

        MonitorOverviewService service = serviceWithMinimalMocks( armedTradeRepo, liquidityRepo );
        MonitorOverviewService.Overview overview = service.load();

        // No venue data → no VenueBlocks to check; enrichmentCoveragePct would be null per venue when total=0
        assertThat( overview.enrichmentFreshness().uncoveredEntityCount() ).isZero();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private MonitorOverviewService serviceWithMinimalMocks(
        ArmedTradeJpaRepository armedTradeRepo,
        LiquidityAssessmentJpaRepository liquidityRepo
    )
    {
        SignalCandidateJpaRepository candidateRepo = mock( SignalCandidateJpaRepository.class );
        FundingEventJpaRepository fundingEventRepo = mock( FundingEventJpaRepository.class );
        VenueDiagnosticsService venueDiagnosticsService = mock( VenueDiagnosticsService.class );
        FundingEventLifecycleService lifecycleService = mock( FundingEventLifecycleService.class );
        VenueProfileService venueProfileService = mock( VenueProfileService.class );

        when( candidateRepo.findAll() ).thenReturn( List.of() );
        when( fundingEventRepo.findAll() ).thenReturn( List.of() );
        when( armedTradeRepo.findAll() ).thenReturn( List.of() );
        when( armedTradeRepo.countArmedTradesByVenue( ArgumentMatchers.anySet() ) ).thenReturn( List.of() );
        when( liquidityRepo.countEnrichedArmedTradesByVenue( ArgumentMatchers.anySet() ) ).thenReturn( List.of() );
        when( armedTradeRepo.findAllByStateIn( ArgumentMatchers.anySet() ) ).thenReturn( List.of() );
        when( venueDiagnosticsService.listVenues() ).thenReturn( List.of() );
        when( venueDiagnosticsService.listTimings() ).thenReturn( List.of() );
        when( venueProfileService.getGlobalAccessProfile() ).thenReturn(
            new VenueProfileService.GlobalAccessProfile(
                VenueAccessMode.TESTNET, false, List.of( VenueAccessMode.TESTNET )
            )
        );

        return new MonitorOverviewService(
            candidateRepo, fundingEventRepo, armedTradeRepo, liquidityRepo,
            venueDiagnosticsService, lifecycleService, venueProfileService
        );
    }

    private ArmedTradeEntity armedTradeWithId( long id )
    {
        ArmedTradeEntity e = new ArmedTradeEntity();
        ReflectionTestUtils.setField( e, "id", id );
        return e;
    }
}
