package com.crypto.funding.api;

import com.crypto.funding.application.candidate.IngestSignalCandidateCommand;
import com.crypto.funding.application.candidate.SignalCandidateIngestService;
import com.crypto.funding.infrastructure.persistence.repository.InstrumentMetadataJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.SignalCandidateJpaRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:sqlite:./build/test-venue-diagnostics.sqlite",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.datasource.hikari.maximum-pool-size=1",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
    "trading.metadata.enabled-venues=binance",
    "trading.metadata.sync-on-startup=false",
    "trading.metadata.schedule-enabled=false",
    "trading.metadata.require-credentials-on-startup=false",
    "trading.metadata.bootstrap-fallback-enabled=false",
    "trading.binance.mode=production"
})
@AutoConfigureMockMvc
class VenueDiagnosticsApiIntegrationTest
{
    private static final WireMockServer BINANCE_SERVER = new WireMockServer( options().dynamicPort() );

    static
    {
        BINANCE_SERVER.start();
    }

    @DynamicPropertySource
    static void configureProperties( DynamicPropertyRegistry registry )
    {
        registry.add( "trading.binance.production.base-url", BINANCE_SERVER::baseUrl );
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InstrumentMetadataJpaRepository instrumentMetadataRepository;

    @Autowired
    private SignalCandidateJpaRepository signalCandidateRepository;

    @Autowired
    private SignalCandidateIngestService signalCandidateIngestService;

    @BeforeEach
    void clean()
    {
        instrumentMetadataRepository.deleteAll();
        signalCandidateRepository.deleteAll();
        BINANCE_SERVER.resetAll();
        BINANCE_SERVER.stubFor( get( urlEqualTo( "/fapi/v1/exchangeInfo" ) )
                                   .willReturn( okJson( """
                                       {
                                         "symbols": [
                                           {
                                             "symbol": "BTCUSDT",
                                             "contractType": "PERPETUAL",
                                             "status": "TRADING",
                                             "baseAsset": "BTC",
                                             "quoteAsset": "USDT",
                                             "quantityPrecision": 3,
                                             "filters": [
                                               {"filterType":"LOT_SIZE","minQty":"0.001","stepSize":"0.001"},
                                               {"filterType":"MIN_NOTIONAL","notional":"5"}
                                             ]
                                           }
                                         ]
                                       }
                                       """ ) ) );
    }

    @AfterAll
    static void stopServer()
    {
        BINANCE_SERVER.stop();
    }

    @Test
    void syncsVenueMetadataAndExposesDiagnostics() throws Exception
    {
        mockMvc.perform( post( "/api/v1/venues/binance/sync" )
                .contentType( MediaType.APPLICATION_JSON ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.venue" ).value( "binance" ) )
            .andExpect( jsonPath( "$.configuredMode" ).value( "production" ) )
            .andExpect( jsonPath( "$.activeInstrumentCount" ).value( 1 ) )
            .andExpect( jsonPath( "$.metadataProviderAvailable" ).value( true ) )
            .andExpect( jsonPath( "$.metadataBaseUrl" ).value( BINANCE_SERVER.baseUrl() ) );

        mockMvc.perform( org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get( "/api/v1/venues" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$[0].venue" ).value( "binance" ) )
            .andExpect( jsonPath( "$[0].activeInstrumentCount" ).value( 1 ) );

        mockMvc.perform( org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get( "/api/v1/venues/binance/instruments" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$[0].canonicalSymbol" ).value( "BTC/USDT" ) )
            .andExpect( jsonPath( "$[0].venueSymbol" ).value( "BTCUSDT" ) )
            .andExpect( jsonPath( "$[0].status" ).value( "ACTIVE" ) );

        mockMvc.perform( org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get( "/api/v1/venues/timings" ).param( "venue", "binance" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$[0].venue" ).value( "binance" ) )
            .andExpect( jsonPath( "$[0].operation" ).value( "metadata-sync" ) )
            .andExpect( jsonPath( "$[0].requests" ).value( 1 ) )
            .andExpect( jsonPath( "$[0].successes" ).value( 1 ) )
            .andExpect( jsonPath( "$[0].lastHttpStatus" ).value( 200 ) )
            .andExpect( jsonPath( "$[0].averageDurationMs" ).isNumber() );
    }

    @Test
    void usesSyncedRegistryForCandidateNormalization() throws Exception
    {
        mockMvc.perform( post( "/api/v1/venues/binance/sync" ) ).andExpect( status().isOk() );

        Long candidateId = signalCandidateIngestService.ingest( new IngestSignalCandidateCommand(
            "TELEGRAM",
            1L,
            10L,
            "coin: BTC/USDT:USDT",
            "BTC/USDT",
            Instant.parse( "2030-01-01T00:00:00Z" )
        ) ).id();

        mockMvc.perform( org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get( "/api/v1/candidates/{id}", candidateId ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.status" ).value( "NORMALIZED" ) )
            .andExpect( jsonPath( "$.normalizedSymbol" ).value( "BTC/USDT" ) )
            .andExpect( jsonPath( "$.venueHints[0]" ).value( "binance" ) );
    }
}
