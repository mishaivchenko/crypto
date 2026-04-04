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
import com.crypto.funding.watchlist.FundingWatchlistService;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
    void refreshCandidatesUpdatesWatchlistAndIngestsCandidate()
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
                                          "exchange": "binance",
                                          "exchange_name": "Binance",
                                          "price": "65000.5",
                                          "funding": "0.0125",
                                          "funding_interval": 8,
                                          "updated_at": "2026-04-04 07:30:00"
                                        }
                                      ]
                                    }
                                    """ ) ) );

        FundingWatchlistService watchlistService = new FundingWatchlistService();
        SignalCandidateIngestService ingestService = mock( SignalCandidateIngestService.class );
        SymbolMetadataPort symbolMetadataPort = mock( SymbolMetadataPort.class );
        VenueRequestTimingService timingService = new VenueRequestTimingService();

        when( symbolMetadataPort.findByVenueSymbol( "binance", "BTCUSDT" ) )
            .thenReturn( java.util.Optional.of( new SymbolMetadata(
                "binance",
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
                "BTCUSDT",
                "BTC/USDT",
                List.of( "binance" ),
                Instant.parse( "2026-04-04T07:30:00Z" ),
                SignalCandidateStatus.NORMALIZED,
                null,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now()
            ) );

        FundingApiCandidateSourceService service = new FundingApiCandidateSourceService(
            HttpClient.newHttpClient(),
            httpProperties(),
            sourceProperties( fundingApi.baseUrl() + "/api/funding?sort_by=funding&sort_dir=asc&limit=30" ),
            metadataProperties( "binance" ),
            ingestService,
            watchlistService,
            symbolMetadataPort,
            timingService
        );

        service.refreshCandidates();

        assertThat( watchlistService.findFunding( "BTC/USDT", "binance" ) )
            .get()
            .satisfies( funding -> {
                assertThat( funding.fundingRatePct() ).isEqualTo( 0.0125d );
                assertThat( funding.nextFundingAt() ).isEqualTo( Instant.parse( "2026-04-04T08:00:00Z" ) );
                assertThat( funding.secondsToFunding() ).isGreaterThanOrEqualTo( 0L );
            } );

        verify( ingestService ).ingest( any( IngestSignalCandidateCommand.class ) );
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

        FundingWatchlistService watchlistService = new FundingWatchlistService();
        SignalCandidateIngestService ingestService = mock( SignalCandidateIngestService.class );
        SymbolMetadataPort symbolMetadataPort = mock( SymbolMetadataPort.class );
        VenueRequestTimingService timingService = new VenueRequestTimingService();

        FundingApiCandidateSourceService service = new FundingApiCandidateSourceService(
            HttpClient.newHttpClient(),
            httpProperties(),
            sourceProperties( fundingApi.baseUrl() + "/api/funding?sort_by=funding&sort_dir=asc&limit=30" ),
            metadataProperties( "binance" ),
            ingestService,
            watchlistService,
            symbolMetadataPort,
            timingService
        );

        service.refreshCandidates();

        verifyNoInteractions( ingestService );
        assertThat( watchlistService.all() ).isEmpty();
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
}
