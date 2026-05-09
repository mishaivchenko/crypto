package com.crypto.funding.api;

import com.crypto.funding.application.candidate.IngestSignalCandidateCommand;
import com.crypto.funding.application.candidate.SignalCandidateIngestService;
import com.crypto.funding.infrastructure.persistence.repository.InstrumentMetadataJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.SignalCandidateJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.VenueProfileJpaRepository;
import com.crypto.funding.infrastructure.telemetry.VenueRequestTimingService;
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
import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
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
    "trading.metadata.enabled-venues=bybit",
    "trading.metadata.sync-on-startup=false",
    "trading.metadata.schedule-enabled=false",
    "trading.metadata.require-credentials-on-startup=false",
    "trading.metadata.bootstrap-fallback-enabled=false",
    "trading.venue-access.mode=production",
    "trading.bybit.production.api-key=dummy-key",
    "trading.bybit.production.secret-key=dummy-secret",
    "security.operators.auth-enabled=false"
})
@AutoConfigureMockMvc
class VenueDiagnosticsApiIntegrationTest
{
    private static final WireMockServer BYBIT_SERVER = new WireMockServer( options().dynamicPort() );

    static
    {
        BYBIT_SERVER.start();
    }

    @DynamicPropertySource
    static void configureProperties( DynamicPropertyRegistry registry )
    {
        registry.add( "trading.bybit.production.base-url", BYBIT_SERVER::baseUrl );
        registry.add( "trading.bybit.testnet.base-url", BYBIT_SERVER::baseUrl );
        registry.add( "trading.bybit.metadata-base-url", BYBIT_SERVER::baseUrl );
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InstrumentMetadataJpaRepository instrumentMetadataRepository;

    @Autowired
    private SignalCandidateJpaRepository signalCandidateRepository;

    @Autowired
    private VenueProfileJpaRepository venueProfileRepository;

    @Autowired
    private SignalCandidateIngestService signalCandidateIngestService;

    @Autowired
    private VenueRequestTimingService venueRequestTimingService;

    @BeforeEach
    void clean()
    {
        instrumentMetadataRepository.deleteAll();
        signalCandidateRepository.deleteAll();
        venueProfileRepository.deleteAll();
        venueRequestTimingService.clear();
        BYBIT_SERVER.resetAll();
        BYBIT_SERVER.stubFor( get( urlPathEqualTo( "/v5/market/instruments-info" ) )
                                   .willReturn( okJson( """
                                       {
                                         "retCode": 0,
                                         "retMsg": "OK",
                                         "result": {
                                           "list": [
                                             {
                                               "symbol": "BTCUSDT",
                                               "status": "Trading",
                                               "baseCoin": "BTC",
                                               "quoteCoin": "USDT",
                                               "lotSizeFilter": {
                                                 "minOrderQty": "0.001",
                                                 "qtyStep": "0.001",
                                                 "minNotionalValue": "5"
                                               }
                                             }
                                           ],
                                           "nextPageCursor": ""
                                         }
                                       }
                                       """ ) ) );
    }

    @AfterAll
    static void stopServer()
    {
        BYBIT_SERVER.stop();
    }

    @Test
    void syncsVenueMetadataAndExposesDiagnostics() throws Exception
    {
        mockMvc.perform( post( "/api/v1/venues/bybit/sync" )
                .contentType( MediaType.APPLICATION_JSON ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.venue" ).value( "bybit" ) )
            .andExpect( jsonPath( "$.configuredMode" ).value( "production" ) )
            .andExpect( jsonPath( "$.activeInstrumentCount" ).value( 1 ) )
            .andExpect( jsonPath( "$.metadataProviderAvailable" ).value( true ) )
            .andExpect( jsonPath( "$.metadataBaseUrl" ).value( BYBIT_SERVER.baseUrl() ) )
            .andExpect( jsonPath( "$.availableModes[0]" ).value( "TESTNET" ) )
            .andExpect( jsonPath( "$.availableModes[1]" ).value( "PRODUCTION" ) )
            .andExpect( jsonPath( "$.connectionStatus" ).value( "NOT_CONNECTED" ) );

        mockMvc.perform( org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get( "/api/v1/venues" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$[0].venue" ).value( "bybit" ) )
            .andExpect( jsonPath( "$[0].activeInstrumentCount" ).value( 1 ) );

        mockMvc.perform( org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get( "/api/v1/venues/bybit/instruments" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$[0].canonicalSymbol" ).value( "BTC/USDT" ) )
            .andExpect( jsonPath( "$[0].venueSymbol" ).value( "BTCUSDT" ) )
            .andExpect( jsonPath( "$[0].status" ).value( "ACTIVE" ) );

        mockMvc.perform( org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get( "/api/v1/venues/timings" ).param( "venue", "bybit" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$[0].venue" ).value( "bybit" ) )
            .andExpect( jsonPath( "$[0].operation" ).value( "metadata-sync" ) )
            .andExpect( jsonPath( "$[0].requests" ).value( 1 ) )
            .andExpect( jsonPath( "$[0].successes" ).value( 1 ) )
            .andExpect( jsonPath( "$[0].lastHttpStatus" ).value( 200 ) )
            .andExpect( jsonPath( "$[0].averageDurationMs" ).isNumber() );
    }

    @Test
    void usesSyncedRegistryForCandidateNormalization() throws Exception
    {
        mockMvc.perform( post( "/api/v1/venues/bybit/sync" ) ).andExpect( status().isOk() );

        Long candidateId = signalCandidateIngestService.ingest( new IngestSignalCandidateCommand(
            "FUNDING_API",
            1L,
            10L,
            "coin: BTC/USDT:USDT",
            "bybit",
            "BTC/USDT",
            Instant.parse( "2030-01-01T00:00:00Z" ),
            Instant.parse( "2030-01-01T08:00:00Z" ),
            BigDecimal.valueOf( 0.01 )
        ) ).id();

        mockMvc.perform( org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get( "/api/v1/candidates/{id}", candidateId ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.status" ).value( "NORMALIZED" ) )
            .andExpect( jsonPath( "$.normalizedSymbol" ).value( "BTC/USDT" ) )
            .andExpect( jsonPath( "$.venueHints[0]" ).value( "bybit" ) );
    }

    @Test
    void allowsRuntimeModeSwitchAndReportsMissingCredentials() throws Exception
    {
        mockMvc.perform( post( "/api/v1/venues/access-mode" )
                .contentType( MediaType.APPLICATION_JSON )
                .content( """
                    {
                      "mode": "TESTNET"
                    }
                    """ ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.mode" ).value( "TESTNET" ) )
            .andExpect( jsonPath( "$.modeOverridden" ).value( true ) )
            .andExpect( jsonPath( "$.availableModes[0]" ).value( "TESTNET" ) );

        mockMvc.perform( org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get( "/api/v1/venues/bybit" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.connectionStatus" ).value( "NOT_CONNECTED" ) )
            .andExpect( jsonPath( "$.connectionMessage" ).value( "Credential storage is disabled." ) );

        mockMvc.perform( post( "/api/v1/venues/access-mode" )
                .contentType( MediaType.APPLICATION_JSON )
                .content( """
                    {
                      "mode": "PRODUCTION"
                    }
                    """ ) )
            .andExpect( status().isOk() );

        mockMvc.perform( org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get( "/api/v1/venues/bybit" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.configuredMode" ).value( "production" ) )
            .andExpect( jsonPath( "$.credentialsConfigured" ).value( true ) )
            .andExpect( jsonPath( "$.connectionMessage" ).value( "ENV keys loaded, check not run." ) );

        BYBIT_SERVER.stubFor( get( urlPathEqualTo( "/v5/account/wallet-balance" ) )
                                   .willReturn( okJson( """
                                       {
                                         "retCode": 0,
                                         "retMsg": "OK",
                                         "result": {}
                                       }
                                       """ ) ) );

        mockMvc.perform( post( "/api/v1/venues/bybit/check" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.credentialsConfigured" ).value( true ) )
            .andExpect( jsonPath( "$.connectionStatus" ).value( "CONNECTED" ) );

        mockMvc.perform( org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get( "/api/v1/venues/bybit" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.credentialsConfigured" ).value( true ) )
            .andExpect( jsonPath( "$.connectionStatus" ).value( "CONNECTED" ) );
    }
}
