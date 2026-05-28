package com.crypto.funding.api;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.crypto.funding.domain.venue.InstrumentStatus;
import com.crypto.funding.infrastructure.persistence.model.ArmedTradeEntity;
import com.crypto.funding.infrastructure.persistence.model.InstrumentMetadataEntity;
import com.crypto.funding.infrastructure.persistence.repository.ArmedTradeJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.FundingEventJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.InstrumentMetadataJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.VenueTimingProfileJpaRepository;
import com.crypto.funding.infrastructure.persistence.repository.VenueProfileJpaRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:sqlite:./build/test-monitor-dev-tools.sqlite",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.datasource.hikari.maximum-pool-size=1",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
    "trading.candidate-source.enabled=false",
    "trading.metadata.sync-on-startup=false",
    "trading.metadata.schedule-enabled=false",
    "trading.metadata.require-credentials-on-startup=false",
    "trading.venue-access.mode=testnet",
    "monitor.dev-test-tool.enabled=true",
    "security.operators.auth-enabled=true",
    "security.cloudflare.audience=test-aud",
    "security.cloudflare.team-domain=test-team.cloudflareaccess.com",
    "monitor.engine-control.internal-token=test-internal-token"
})
@AutoConfigureMockMvc
class MonitorDevToolsApiIntegrationTest
{
    private static final WireMockServer ENGINE = new WireMockServer( options().dynamicPort() );
    private static final WireMockServer JWKS_SERVER = new WireMockServer( options().dynamicPort() );
    private static RSAKey RSA_KEY;
    private static String ALICE_JWT;

    static
    {
        ENGINE.start();
        JWKS_SERVER.start();
    }

    @BeforeAll
    static void initJwks() throws Exception
    {
        RSA_KEY = new RSAKeyGenerator( 2048 )
            .keyUse( KeyUse.SIGNATURE )
            .keyID( "dev-tools-kid" )
            .generate();
        String jwksBody = new com.nimbusds.jose.jwk.JWKSet( RSA_KEY.toPublicJWK() ).toString();
        JWKS_SERVER.stubFor( com.github.tomakehurst.wiremock.client.WireMock.get( urlPathEqualTo( "/cdn-cgi/access/certs" ) )
            .willReturn( okJson( jwksBody ) ) );
        ALICE_JWT = buildJwt( "alice@example.com" );
    }

    @DynamicPropertySource
    static void configureProperties( DynamicPropertyRegistry registry )
    {
        registry.add( "monitor.engine-control.base-url", ENGINE::baseUrl );
        registry.add( "security.cloudflare.certs-url",
            () -> JWKS_SERVER.baseUrl() + "/cdn-cgi/access/certs" );
    }

    private static String buildJwt( String email ) throws Exception
    {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .subject( email )
            .claim( "email", email )
            .issuer( "https://test-team.cloudflareaccess.com" )
            .audience( "test-aud" )
            .expirationTime( new Date( System.currentTimeMillis() + 3_600_000L ) )
            .jwtID( UUID.randomUUID().toString() )
            .build();
        SignedJWT jwt = new SignedJWT(
            new JWSHeader.Builder( JWSAlgorithm.RS256 ).keyID( RSA_KEY.getKeyID() ).build(),
            claims
        );
        jwt.sign( new RSASSASigner( RSA_KEY ) );
        return jwt.serialize();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InstrumentMetadataJpaRepository instrumentMetadataRepository;

    @Autowired
    private VenueTimingProfileJpaRepository venueTimingProfileRepository;

    @Autowired
    private FundingEventJpaRepository fundingEventRepository;

    @Autowired
    private ArmedTradeJpaRepository armedTradeRepository;

    @Autowired
    private VenueProfileJpaRepository venueProfileRepository;

    @BeforeEach
    void resetEngine()
    {
        armedTradeRepository.deleteAll();
        fundingEventRepository.deleteAll();
        instrumentMetadataRepository.deleteAll();
        venueTimingProfileRepository.deleteAll();
        venueProfileRepository.deleteAll();
        ENGINE.resetAll();
        ENGINE.stubFor( com.github.tomakehurst.wiremock.client.WireMock.get( urlPathEqualTo( "/internal/engine/runtime" ) )
            .willReturn( okJson( """
                {
                  "module":"engine-app",
                  "version":"2.0.0",
                  "tradingVenueAccessMode":"testnet",
                  "liveOrderEnabled":true,
                  "killSwitchEnabled":false,
                  "liveEnabledVenues":["bybit","gate"],
                  "maxNotionalUsd":25,
                  "executionLoopEnabled":true,
                  "executionLoopIntervalMs":1000,
                  "minimumExecutionLoopIntervalMs":100,
                  "runtimeUpdatedAt":"2030-01-01T00:00:10Z",
                  "lastRunStartedAt":"2030-01-01T00:00:00Z",
                  "lastRunFinishedAt":"2030-01-01T00:00:01Z",
                  "lastRunForced":true,
                  "lastPlansScanned":4,
                  "lastAttemptsSubmitted":3,
                  "lastAttemptsSkipped":1,
                  "lastExecutionRunDurationMs":1000,
                  "lastForcedRunStartedAt":"2030-01-01T00:00:00Z",
                  "lastForcedRunFinishedAt":"2030-01-01T00:00:01Z",
                  "lastForcedPlansScanned":4,
                  "lastForcedAttemptsSubmitted":3,
                  "lastForcedAttemptsSkipped":1,
                  "lastForcedRunDurationMs":1000
                }
                """ ) ) );
        ENGINE.stubFor( com.github.tomakehurst.wiremock.client.WireMock.post( urlPathEqualTo( "/internal/engine/execution/target" ) )
            .willReturn( okJson( """
                {
                  "startedAt":"2030-01-01T00:00:00Z",
                  "finishedAt":"2030-01-01T00:00:01Z",
                  "force":true,
                  "plansScanned":1,
                  "attemptsSubmitted":1,
                  "attemptsSkipped":0,
                  "results":[{"armedTradeId":1,"attemptNumber":1,"attemptKey":"entry:1:1:2030-01-01T00:00:00Z","status":"FILLED","failureReason":null,"recordedAt":"2030-01-01T00:00:01Z"}]
                }
                """ ) ) );
        ENGINE.stubFor( com.github.tomakehurst.wiremock.client.WireMock.post( urlPathEqualTo( "/internal/engine/execution/run-once" ) )
            .withQueryParam( "force", equalTo( "true" ) )
            .willReturn( okJson( """
                {
                  "startedAt":"2030-01-01T00:00:00Z",
                  "finishedAt":"2030-01-01T00:00:01Z",
                  "force":true,
                  "plansScanned":4,
                  "attemptsSubmitted":3,
                  "attemptsSkipped":1,
                  "results":[]
                }
                """ ) ) );
        ENGINE.stubFor( com.github.tomakehurst.wiremock.client.WireMock.post( urlPathEqualTo( "/internal/engine/runtime" ) )
            .willReturn( okJson( """
                {
                  "module":"engine-app",
                  "version":"2.0.0",
                  "tradingVenueAccessMode":"testnet",
                  "liveOrderEnabled":false,
                  "killSwitchEnabled":true,
                  "liveEnabledVenues":["bybit","gate"],
                  "maxNotionalUsd":25,
                  "executionLoopEnabled":false,
                  "executionLoopIntervalMs":2500,
                  "minimumExecutionLoopIntervalMs":100,
                  "runtimeUpdatedAt":"2030-01-01T00:05:00Z",
                  "lastRunStartedAt":"2030-01-01T00:00:00Z",
                  "lastRunFinishedAt":"2030-01-01T00:00:01Z",
                  "lastRunForced":false,
                  "lastPlansScanned":4,
                  "lastAttemptsSubmitted":3,
                  "lastAttemptsSkipped":1,
                  "lastExecutionRunDurationMs":1000,
                  "lastForcedRunStartedAt":"2030-01-01T00:00:00Z",
                  "lastForcedRunFinishedAt":"2030-01-01T00:00:01Z",
                  "lastForcedPlansScanned":4,
                  "lastForcedAttemptsSubmitted":3,
                  "lastForcedAttemptsSkipped":1,
                  "lastForcedRunDurationMs":1000
                }
                """ ) ) );
    }

    @AfterAll
    static void stopServers()
    {
        ENGINE.stop();
        JWKS_SERVER.stop();
    }

    @Test
    void requiresOperatorTokenForDevTools() throws Exception
    {
        // REQ: ENG-ACC-007
        mockMvc.perform( post( "/api/v2/monitor/dev/engine/run-once" ) )
               .andExpect( status().isUnauthorized() )
               .andExpect( jsonPath( "$.message" ).value( "Valid Cloudflare Access session required." ) );
    }

    @Test
    void proxiesRunOnceToEngineWithInternalToken() throws Exception
    {
        // REQ: ENG-ACC-007
        mockMvc.perform( post( "/api/v2/monitor/dev/engine/run-once" )
                .header( "Cf-Access-Jwt-Assertion", ALICE_JWT )
                .param( "force", "true" ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$.force" ).value( true ) )
               .andExpect( jsonPath( "$.plansScanned" ).value( 4 ) )
               .andExpect( jsonPath( "$.attemptsSubmitted" ).value( 3 ) )
               .andExpect( jsonPath( "$.attemptsSkipped" ).value( 1 ) );

        ENGINE.verify( postRequestedFor( urlPathEqualTo( "/internal/engine/execution/run-once" ) )
            .withQueryParam( "force", equalTo( "true" ) )
            .withHeader( "X-Internal-Token", equalTo( "test-internal-token" ) ) );
    }

    @Test
    void loadsAndUpdatesEngineRuntimeThroughMonitorApi() throws Exception
    {
        // REQ: ENG-ACC-007
        mockMvc.perform( get( "/api/v2/monitor/dev/engine/runtime" )
                .header( "Cf-Access-Jwt-Assertion", ALICE_JWT ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$.tradingVenueAccessMode" ).value( "testnet" ) )
               .andExpect( jsonPath( "$.liveOrderEnabled" ).value( true ) )
               .andExpect( jsonPath( "$.killSwitchEnabled" ).value( false ) )
               .andExpect( jsonPath( "$.executionLoopEnabled" ).value( true ) )
               .andExpect( jsonPath( "$.executionLoopIntervalMs" ).value( 1000 ) );

        mockMvc.perform( post( "/api/v2/monitor/dev/engine/runtime" )
                .header( "Cf-Access-Jwt-Assertion", ALICE_JWT )
                .contentType( "application/json" )
                .content( """
                    {
                      "executionLoopEnabled": false,
                      "executionLoopIntervalMs": 2500
                    }
                    """ ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$.executionLoopEnabled" ).value( false ) )
               .andExpect( jsonPath( "$.executionLoopIntervalMs" ).value( 2500 ) );

        ENGINE.verify( com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor( urlPathEqualTo( "/internal/engine/runtime" ) )
            .withHeader( "X-Internal-Token", equalTo( "test-internal-token" ) ) );
        ENGINE.verify( postRequestedFor( urlPathEqualTo( "/internal/engine/runtime" ) )
            .withHeader( "X-Internal-Token", equalTo( "test-internal-token" ) ) );
    }

    @Test
    void listsDevTestRunOptionsForActiveBybitAndGateSymbols() throws Exception
    {
        seedInstrument( "bybit", "BTC/USDT", "BTCUSDT" );
        seedInstrument( "gate", "ETH/USDT", "ETH_USDT" );

        mockMvc.perform( get( "/api/v2/monitor/dev/test-runs/options" )
                .header( "Cf-Access-Jwt-Assertion", ALICE_JWT ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$.enabled" ).value( true ) )
               .andExpect( jsonPath( "$.currentMode" ).value( "TESTNET" ) )
               .andExpect( jsonPath( "$.venues[0].venue" ).value( "bybit" ) )
               .andExpect( jsonPath( "$.venues[0].symbols[0].symbol" ).value( "BTC/USDT" ) )
               .andExpect( jsonPath( "$.venues[1].venue" ).value( "gate" ) )
               .andExpect( jsonPath( "$.venues[1].symbols[0].symbol" ).value( "ETH/USDT" ) );
    }

    @Test
    void createsSyntheticDevTestRunWithSingleEntryAttempt() throws Exception
    {
        seedInstrument( "bybit", "BTC/USDT", "BTCUSDT" );

        mockMvc.perform( post( "/api/v2/monitor/dev/test-runs" )
                .header( "Cf-Access-Jwt-Assertion", ALICE_JWT )
                .contentType( "application/json" )
                .content( """
                    {
                      "venue":"bybit",
                      "symbol":"BTC/USDT",
                      "notionalUsd":25
                    }
                    """ ) )
               .andExpect( status().isCreated() )
               .andExpect( jsonPath( "$.mode" ).value( "TESTNET" ) )
               .andExpect( jsonPath( "$.venue" ).value( "bybit" ) )
               .andExpect( jsonPath( "$.symbol" ).value( "BTC/USDT" ) )
               .andExpect( jsonPath( "$.notionalUsd" ).value( 25 ) )
               .andExpect( jsonPath( "$.armedTradeId" ).isNumber() );

        assertThat( fundingEventRepository.findAll() ).singleElement().satisfies( event -> {
            assertThat( event.getSourceType() ).isEqualTo( "DEV_TEST_RUN" );
            assertThat( event.getSourceRef() ).startsWith( "dev-test-run:bybit:BTC/USDT:" );
        } );
        assertThat( armedTradeRepository.findAll() ).singleElement().satisfies( trade -> {
            assertThat( trade.getEntryAttemptCount() ).isEqualTo( 1 );
            assertThat( trade.getEntrySpacingMs() ).isZero();
            assertThat( trade.getNotes() ).contains( "DEV_TEST_RUN" );
        } );
        assertThat( venueTimingProfileRepository.findFirstByVenueAndSymbolOrderBySampledAtDesc( "bybit", "BTC/USDT" ) )
            .get()
            .satisfies( timing -> {
                assertThat( timing.getSampledAt() ).isAfter( Instant.parse( "2026-01-01T00:00:00Z" ) );
                assertThat( timing.getNotes() ).contains( "DEV_TEST_RUN" );
            } );
    }

    @Test
    void runsTargetedEntryForCreatedDevTestRun() throws Exception
    {
        seedInstrument( "bybit", "BTC/USDT", "BTCUSDT" );
        Long armedTradeId = createDevRun( "bybit", "BTC/USDT" );

        mockMvc.perform( post( "/api/v2/monitor/dev/test-runs/{armedTradeId}/entry", armedTradeId )
                .header( "Cf-Access-Jwt-Assertion", ALICE_JWT )
                .contentType( "application/json" )
                .content( "{}" ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$.phase" ).value( "ENTRY" ) )
               .andExpect( jsonPath( "$.execution.attemptsSubmitted" ).value( 1 ) );

        ENGINE.verify( postRequestedFor( urlPathEqualTo( "/internal/engine/execution/target" ) )
            .withHeader( "X-Internal-Token", equalTo( "test-internal-token" ) )
            .withRequestBody( matchingJsonPath( "$.armedTradeId", equalTo( armedTradeId.toString() ) ) )
            .withRequestBody( matchingJsonPath( "$.phase", equalTo( "ENTRY" ) ) )
            .withRequestBody( matchingJsonPath( "$.force", equalTo( "true" ) ) ) );
    }

    @Test
    void productionTargetExecutionRequiresTypedConfirmation() throws Exception
    {
        seedInstrument( "bybit", "BTC/USDT", "BTCUSDT" );
        mockMvc.perform( post( "/api/v1/venues/access-mode" )
                .header( "Cf-Access-Jwt-Assertion", ALICE_JWT )
                .contentType( "application/json" )
                .content( "{\"mode\":\"PRODUCTION\"}" ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$.mode" ).value( "PRODUCTION" ) );
        Long armedTradeId = createDevRun( "bybit", "BTC/USDT" );

        mockMvc.perform( post( "/api/v2/monitor/dev/test-runs/{armedTradeId}/entry", armedTradeId )
                .header( "Cf-Access-Jwt-Assertion", ALICE_JWT )
                .contentType( "application/json" )
                .content( "{\"productionConfirm\":\"wrong\"}" ) )
               .andExpect( status().isConflict() )
               .andExpect( jsonPath( "$.message" ).value( "Production dev test run requires confirmation: bybit BTC/USDT LIVE" ) );
    }

    @Test
    void productionTargetExecutionRejectsWhenEngineLiveGatesAreNotAligned() throws Exception
    {
        seedInstrument( "bybit", "BTC/USDT", "BTCUSDT" );
        mockMvc.perform( post( "/api/v1/venues/access-mode" )
                .header( "Cf-Access-Jwt-Assertion", ALICE_JWT )
                .contentType( "application/json" )
                .content( "{\"mode\":\"PRODUCTION\"}" ) )
               .andExpect( status().isOk() );
        Long armedTradeId = createDevRun( "bybit", "BTC/USDT" );
        ENGINE.stubFor( com.github.tomakehurst.wiremock.client.WireMock.get( urlPathEqualTo( "/internal/engine/runtime" ) )
            .atPriority( 1 )
            .willReturn( okJson( """
                {
                  "module":"engine-app",
                  "version":"2.0.0",
                  "tradingVenueAccessMode":"production",
                  "liveOrderEnabled":false,
                  "killSwitchEnabled":false,
                  "liveEnabledVenues":["bybit","gate"],
                  "maxNotionalUsd":25,
                  "executionLoopEnabled":true,
                  "executionLoopIntervalMs":1000,
                  "minimumExecutionLoopIntervalMs":100,
                  "runtimeUpdatedAt":"2030-01-01T00:00:10Z",
                  "lastRunStartedAt":null,
                  "lastRunFinishedAt":null,
                  "lastRunForced":false,
                  "lastPlansScanned":0,
                  "lastAttemptsSubmitted":0,
                  "lastAttemptsSkipped":0,
                  "lastExecutionRunDurationMs":0,
                  "lastForcedRunStartedAt":null,
                  "lastForcedRunFinishedAt":null,
                  "lastForcedPlansScanned":0,
                  "lastForcedAttemptsSubmitted":0,
                  "lastForcedAttemptsSkipped":0,
                  "lastForcedRunDurationMs":0
                }
                """ ) ) );

        mockMvc.perform( post( "/api/v2/monitor/dev/test-runs/{armedTradeId}/entry", armedTradeId )
                .header( "Cf-Access-Jwt-Assertion", ALICE_JWT )
                .contentType( "application/json" )
                .content( "{\"productionConfirm\":\"bybit BTC/USDT LIVE\"}" ) )
               .andExpect( status().isConflict() )
               .andExpect( jsonPath( "$.message" ).value( "Production dev test run safety gate failed: ENGINE_LIVE_ORDER_ENABLED is false." ) );
    }

    private Long createDevRun( String venue, String symbol ) throws Exception
    {
        mockMvc.perform( post( "/api/v2/monitor/dev/test-runs" )
                .header( "Cf-Access-Jwt-Assertion", ALICE_JWT )
                .contentType( "application/json" )
                .content( """
                    {
                      "venue":"%s",
                      "symbol":"%s",
                      "notionalUsd":25
                    }
                    """.formatted( venue, symbol ) ) )
               .andExpect( status().isCreated() );
        return armedTradeRepository.findAll()
                                   .stream()
                                   .map( ArmedTradeEntity::getId )
                                   .findFirst()
                                   .orElseThrow();
    }

    private void seedInstrument( String venue, String symbol, String venueSymbol )
    {
        InstrumentMetadataEntity entity = new InstrumentMetadataEntity();
        entity.setVenue( venue );
        entity.setCanonicalSymbol( symbol );
        entity.setVenueSymbol( venueSymbol );
        entity.setBaseAsset( symbol.substring( 0, symbol.indexOf( '/' ) ) );
        entity.setQuoteAsset( "USDT" );
        entity.setInstrumentType( "PERPETUAL" );
        entity.setStatus( InstrumentStatus.ACTIVE );
        entity.setMinOrderQty( BigDecimal.ONE );
        entity.setQtyStep( BigDecimal.ONE );
        entity.setMinNotionalValue( BigDecimal.valueOf( 5 ) );
        entity.setLastSyncedAt( Instant.parse( "2030-01-01T00:00:00Z" ) );
        instrumentMetadataRepository.save( entity );
    }
}
