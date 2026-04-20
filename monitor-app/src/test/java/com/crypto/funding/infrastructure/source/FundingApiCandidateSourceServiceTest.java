package com.crypto.funding.infrastructure.source;

import com.crypto.funding.application.candidate.IngestSignalCandidateCommand;
import com.crypto.funding.application.candidate.SignalCandidateIngestService;
import com.crypto.funding.application.port.SymbolMetadata;
import com.crypto.funding.application.port.SymbolMetadataPort;
import com.crypto.funding.config.FundingCandidateSourceProperties;
import com.crypto.funding.config.MetadataSyncProperties;
import com.crypto.funding.config.VenueHttpProperties;
import com.crypto.funding.domain.candidate.SignalCandidate;
import com.crypto.funding.domain.candidate.SignalCandidateStatus;
import com.crypto.funding.infrastructure.telemetry.VenueRequestTimingService;
import com.crypto.funding.infrastructure.persistence.repository.SignalCandidateJpaRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FundingApiCandidateSourceServiceTest
{
    private final WireMockServer fundingApi = new WireMockServer( options().dynamicPort() );

    @AfterEach
    void stopServer()
    {
        if( fundingApi.isRunning() )
        {
            fundingApi.stop();
        }
    }

    @Test
    void refreshCandidatesIngestsCandidateWithFundingSnapshot()
    {
        fundingApi.start();
        fundingApi.stubFor( get( urlEqualTo( "/api/funding?sort_by=funding&sort_dir=asc&limit=30" ) )
                                .willReturn( okJson( """
                                    {
                                      "data": [
                                        {
                                          "id": 1,
                                          "symbol": "BTCUSDT",
                                          "coin": "BTC",
                                          "exchange": "bybit",
                                          "exchange_name": "Bybit",
                                          "price": "65000.5",
                                          "funding": "0.0125",
                                          "funding_interval": 8,
                                          "updated_at": "2030-04-04 07:30:00"
                                        }
                                      ]
                                    }
                                    """ ) ) );

        SignalCandidateIngestService ingestService = mock( SignalCandidateIngestService.class );
        SymbolMetadataPort symbolMetadataPort = mock( SymbolMetadataPort.class );
        VenueRequestTimingService timingService = new VenueRequestTimingService();
        SignalCandidateJpaRepository candidateRepository = mock( SignalCandidateJpaRepository.class );

        when( symbolMetadataPort.findByVenueSymbol( "bybit", "BTCUSDT" ) )
            .thenReturn( java.util.Optional.of( new SymbolMetadata(
                "bybit",
                "BTC/USDT",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
            ) ) );
        when( ingestService.ingest( any( IngestSignalCandidateCommand.class ) ) )
            .thenReturn( new SignalCandidate(
                1L,
                "FUNDING_API",
                1L,
                10L,
                "{}",
                "bybit",
                "BTCUSDT",
                "BTC/USDT",
                List.of( "bybit" ),
                Instant.parse( "2030-04-04T07:30:00Z" ),
                SignalCandidateStatus.NORMALIZED,
                null,
                null,
                null,
                null,
                Instant.parse( "2030-04-04T16:00:00Z" ),
                BigDecimal.valueOf( 0.0125 ),
                null,
                Instant.now(),
                Instant.now()
            ) );

        FundingApiCandidateSourceService service = new FundingApiCandidateSourceService(
            HttpClient.newHttpClient(),
            httpProperties(),
            sourceProperties( fundingApi.baseUrl() + "/api/funding?sort_by=funding&sort_dir=asc&limit=30" ),
            metadataProperties( "bybit" ),
            ingestService,
            symbolMetadataPort,
            timingService,
            candidateRepository,
            fixedClock( "2030-04-04T11:35:00Z" )
        );

        service.refreshCandidates();

        ArgumentCaptor<IngestSignalCandidateCommand> candidateCaptor = ArgumentCaptor.forClass( IngestSignalCandidateCommand.class );
        verify( ingestService ).ingest( candidateCaptor.capture() );
        assertThat( candidateCaptor.getValue().sourceFundingTime() ).isEqualTo( Instant.parse( "2030-04-04T16:00:00Z" ) );
        assertThat( candidateCaptor.getValue().sourceFundingRatePct() ).isEqualByComparingTo( BigDecimal.valueOf( 0.0125 ) );
        verify( candidateRepository ).findAllBySourceTypeAndSourceChatIdAndFundingEventIdIsNullOrderByDetectedAtDesc( "FUNDING_API", 1L );
        assertThat( timingService.snapshots( "candidate-source" ) )
            .singleElement()
            .satisfies( snapshot -> {
                assertThat( snapshot.operation() ).isEqualTo( "uainvest-funding-fetch" );
                assertThat( snapshot.successes() ).isEqualTo( 1L );
                assertThat( snapshot.lastHttpStatus() ).isEqualTo( 200 );
                assertThat( snapshot.lastPayloadSize() ).isEqualTo( 1L );
            } );
    }

    @Test
    void refreshCandidatesSkipsVenuesOutsideEnabledList()
    {
        fundingApi.start();
        fundingApi.stubFor( get( urlEqualTo( "/api/funding?sort_by=funding&sort_dir=asc&limit=30" ) )
                                .willReturn( okJson( """
                                    {
                                      "data": [
                                        {
                                          "id": 2,
                                          "symbol": "BTCUSDT",
                                          "coin": "BTC",
                                          "exchange": "gate",
                                          "exchange_name": "Gate",
                                          "price": "65000.5",
                                          "funding": "0.0125",
                                          "funding_interval": 8,
                                          "updated_at": "2026-04-04 07:30:00"
                                        }
                                      ]
                                    }
                                    """ ) ) );

        SignalCandidateIngestService ingestService = mock( SignalCandidateIngestService.class );
        SymbolMetadataPort symbolMetadataPort = mock( SymbolMetadataPort.class );
        VenueRequestTimingService timingService = new VenueRequestTimingService();
        SignalCandidateJpaRepository candidateRepository = mock( SignalCandidateJpaRepository.class );

        FundingApiCandidateSourceService service = new FundingApiCandidateSourceService(
            HttpClient.newHttpClient(),
            httpProperties(),
            sourceProperties( fundingApi.baseUrl() + "/api/funding?sort_by=funding&sort_dir=asc&limit=30" ),
            metadataProperties( "bybit" ),
            ingestService,
            symbolMetadataPort,
            timingService,
            candidateRepository,
            fixedClock( "2026-04-04T11:35:00Z" )
        );

        service.refreshCandidates();

        verifyNoInteractions( ingestService );
        verify( candidateRepository, never() ).deleteAll( any() );
    }

    @Test
    void refreshCandidatesUsesStableSourceMessageIdForSameFundingApiEntry()
    {
        fundingApi.start();
        fundingApi.stubFor( get( urlEqualTo( "/api/funding?sort_by=funding&sort_dir=asc&limit=30" ) )
            .willReturn( okJson( """
                {
                  "data": [
                    {
                      "id": 42,
                      "symbol": "NOMUSDT",
                      "coin": "NOM",
                      "exchange": "gate",
                      "exchange_name": "Gate",
                      "price": "0.15",
                      "funding": "-0.0125",
                      "funding_interval": 4,
                      "updated_at": "2026-04-04 07:30:00"
                    }
                  ]
                }
                """ ) ) );

        SignalCandidateIngestService ingestService = mock( SignalCandidateIngestService.class );
        SymbolMetadataPort symbolMetadataPort = mock( SymbolMetadataPort.class );
        VenueRequestTimingService timingService = new VenueRequestTimingService();
        SignalCandidateJpaRepository candidateRepository = mock( SignalCandidateJpaRepository.class );

        when( symbolMetadataPort.findByVenueSymbol( "gate", "NOMUSDT" ) )
            .thenReturn( java.util.Optional.of( new SymbolMetadata(
                "gate",
                "NOM/USDT",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
            ) ) );
        when( ingestService.ingest( any( IngestSignalCandidateCommand.class ) ) )
            .thenReturn( new SignalCandidate(
                1L,
                "FUNDING_API",
                1L,
                0L,
                "{}",
                "gate",
                "NOMUSDT",
                "NOM/USDT",
                List.of( "gate" ),
                Instant.parse( "2026-04-04T07:30:00Z" ),
                SignalCandidateStatus.NORMALIZED,
                null,
                null,
                null,
                null,
                Instant.parse( "2026-04-04T16:00:00Z" ),
                BigDecimal.valueOf( -0.0125 ),
                null,
                Instant.now(),
                Instant.now()
            ) );

        FundingApiCandidateSourceService service = new FundingApiCandidateSourceService(
            HttpClient.newHttpClient(),
            httpProperties(),
            sourceProperties( fundingApi.baseUrl() + "/api/funding?sort_by=funding&sort_dir=asc&limit=30" ),
            metadataProperties( "gate" ),
            ingestService,
            symbolMetadataPort,
            timingService,
            candidateRepository,
            fixedClock( "2026-04-04T12:35:00Z" )
        );

        service.refreshCandidates();
        service.refreshCandidates();

        ArgumentCaptor<IngestSignalCandidateCommand> captor = ArgumentCaptor.forClass( IngestSignalCandidateCommand.class );
        verify( ingestService, org.mockito.Mockito.times( 2 ) ).ingest( captor.capture() );
        assertThat( captor.getAllValues() )
            .extracting( IngestSignalCandidateCommand::sourceMessageId )
            .containsExactly( captor.getAllValues().getFirst().sourceMessageId(), captor.getAllValues().getFirst().sourceMessageId() );
    }

    private static VenueHttpProperties httpProperties()
    {
        VenueHttpProperties properties = new VenueHttpProperties();
        properties.setConnectTimeoutMs( 1_000 );
        properties.setRequestTimeoutMs( 5_000 );
        properties.setPreferHttp2( false );
        return properties;
    }

    private static FundingCandidateSourceProperties sourceProperties( String url )
    {
        FundingCandidateSourceProperties properties = new FundingCandidateSourceProperties();
        properties.setUrl( url );
        properties.setSourceType( "FUNDING_API" );
        properties.setRefreshIntervalSeconds( 60 );
        properties.setEnabled( true );
        return properties;
    }

    private static MetadataSyncProperties metadataProperties( String... venues )
    {
        MetadataSyncProperties properties = new MetadataSyncProperties();
        properties.setEnabledVenues( List.of( venues ) );
        properties.setRequireCredentialsOnStartup( false );
        properties.setSyncOnStartup( false );
        properties.setScheduleEnabled( false );
        return properties;
    }

    private static Clock fixedClock( String instant )
    {
        return Clock.fixed( Instant.parse( instant ), ZoneOffset.UTC );
    }
}
