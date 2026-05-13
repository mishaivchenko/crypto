package com.crypto.funding.engine;

import com.crypto.funding.contract.engine.EngineEntryAttemptPlan;
import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.TradeSide;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class AutonomousLoopIntegrationTest
{
    private static final Instant NOW = Instant.parse( "2030-01-01T00:00:00Z" );
    private static final String TRADE_ID = "5";

    private WireMockServer monitor;
    private WireMockServer exchange;

    @BeforeEach
    void startServers()
    {
        monitor = new WireMockServer( 0 );
        monitor.start();
        exchange = new WireMockServer( 0 );
        exchange.start();
    }

    @AfterEach
    void stopServers()
    {
        monitor.stop();
        exchange.stop();
    }

    // REQ: ENG-EXE-001
    // REQ: ENG-EXE-004
    // REQ: ENG-EXE-005
    @Test
    void autonomousLoopRunsFullCycleEntryThenExit()
    {
        stubExchangeForEntry();
        stubMonitorForEntry();

        EngineExecutionService service = buildService();

        // Tick 1: entry window — should submit entry order, record position, move trade to OPEN
        service.runOnce( false );

        exchange.verify( 1, postRequestedFor( urlEqualTo( "/futures/usdt/orders" ) )
            .withRequestBody( containing( "\"reduce_only\":false" ) ) );
        monitor.verify( 1, postRequestedFor( urlEqualTo( "/internal/v1/engine/order-attempts" ) ) );
        monitor.verify( 1, postRequestedFor( urlEqualTo( "/internal/v1/engine/positions" ) ) );
        monitor.verify( 1, postRequestedFor( urlPathEqualTo( "/internal/v1/engine/trades/" + TRADE_ID + "/state" ) )
            .withRequestBody( containing( "\"state\":\"OPEN\"" ) ) );

        // Switch plan stub to exit window with position populated
        switchMonitorToExitScenario();
        stubExchangeForExit();
        stubMonitorPostsForExit();

        // Tick 2: exit window — should submit exit order, close position, record outcome
        service.runOnce( false );

        exchange.verify( 2, postRequestedFor( urlEqualTo( "/futures/usdt/orders" ) ) );
        exchange.verify( 1, postRequestedFor( urlEqualTo( "/futures/usdt/orders" ) )
            .withRequestBody( containing( "\"reduce_only\":true" ) ) );
        monitor.verify( 2, postRequestedFor( urlEqualTo( "/internal/v1/engine/positions" ) ) );
        monitor.verify( 1, postRequestedFor( urlEqualTo( "/internal/v1/engine/outcomes" ) )
            .withRequestBody( containing( "\"outcomeCode\":\"CLOSED\"" ) ) );
        monitor.verify( 2, postRequestedFor( urlPathEqualTo( "/internal/v1/engine/trades/" + TRADE_ID + "/state" ) ) );
        monitor.verify( 1, postRequestedFor( urlPathEqualTo( "/internal/v1/engine/trades/" + TRADE_ID + "/state" ) )
            .withRequestBody( containing( "\"state\":\"CLOSED\"" ) ) );
        // latency sample reported after both entry and exit submissions
        monitor.verify( 2, postRequestedFor( urlEqualTo( "/internal/v1/engine/latency-samples" ) ) );
    }

    private void stubExchangeForEntry()
    {
        exchange.stubFor( get( urlEqualTo( "/futures/usdt/tickers?contract=REQ_USDT" ) )
            .willReturn( okJson( "[{\"contract\":\"REQ_USDT\",\"last\":\"2.5\"}]" ) ) );
        exchange.stubFor( get( urlEqualTo( "/futures/usdt/contracts/REQ_USDT" ) )
            .willReturn( okJson( "{\"name\":\"REQ_USDT\",\"quanto_multiplier\":\"0.01\"}" ) ) );
        exchange.stubFor( post( urlEqualTo( "/futures/usdt/orders" ) )
            .inScenario( "trade-cycle" )
            .whenScenarioStateIs( Scenario.STARTED )
            .willReturn( okJson( """
                {"id":"gate-entry-1","status":"finished","finish_as":"filled",\
                "contract":"REQ_USDT","size":-1000,"fill_price":"2.5",\
                "fee":"0.01","create_time":1893456000}
                """ ) )
            .willSetStateTo( "entry-done" ) );
    }

    private void stubExchangeForExit()
    {
        exchange.stubFor( post( urlEqualTo( "/futures/usdt/orders" ) )
            .inScenario( "trade-cycle" )
            .whenScenarioStateIs( "entry-done" )
            .willReturn( okJson( """
                {"id":"gate-exit-1","status":"finished","finish_as":"filled",\
                "contract":"REQ_USDT","size":1000,"fill_price":"2.6",\
                "fee":"0.01","create_time":1893456001}
                """ ) ) );
    }

    private void stubMonitorForEntry()
    {
        monitor.stubFor( get( urlPathEqualTo( "/internal/v1/engine/plans" ) )
            .inScenario( "trade-cycle" )
            .whenScenarioStateIs( Scenario.STARTED )
            .willReturn( okJson( entryPlanJson() ) )
            .willSetStateTo( "entry-done" ) );

        monitor.stubFor( post( urlEqualTo( "/internal/v1/engine/order-attempts" ) )
            .willReturn( okJson( entryAttemptResponseJson() ) ) );

        monitor.stubFor( post( urlEqualTo( "/internal/v1/engine/positions" ) )
            .willReturn( okJson( positionResponseJson( "OPEN", null ) ) ) );

        monitor.stubFor( post( urlPathEqualTo( "/internal/v1/engine/trades/" + TRADE_ID + "/state" ) )
            .willReturn( okJson( stateUpdateResponseJson( "OPEN" ) ) ) );

        monitor.stubFor( post( urlEqualTo( "/internal/v1/engine/latency-samples" ) )
            .willReturn( aResponse().withStatus( 204 ) ) );
    }

    private void switchMonitorToExitScenario()
    {
        monitor.stubFor( get( urlPathEqualTo( "/internal/v1/engine/plans" ) )
            .inScenario( "trade-cycle" )
            .whenScenarioStateIs( "entry-done" )
            .willReturn( okJson( exitPlanJson() ) ) );
    }

    private void stubMonitorPostsForExit()
    {
        monitor.stubFor( post( urlEqualTo( "/internal/v1/engine/outcomes" ) )
            .willReturn( okJson( outcomeResponseJson() ) ) );
    }

    private EngineExecutionService buildService()
    {
        MockEnvironment environment = liveEnvironment( "gate" )
            .withProperty( "engine.live-order-enabled", "true" )
            .withProperty( "engine.gate.testnet-base-url", exchange.baseUrl() );

        CredentialAwareExecutionPort executionPort = new CredentialAwareExecutionPort( environment );

        EngineProperties properties = new EngineProperties();
        properties.setMonitorBaseUrl( monitor.baseUrl() );
        properties.setInternalToken( "test-internal-token" );

        EngineTelemetryService telemetryService = new EngineTelemetryService();
        Clock clock = Clock.fixed( NOW, ZoneOffset.UTC );
        LongSupplier nanos = advancingNanos( Duration.ofSeconds( 1 ).toNanos(), Duration.ofMillis( 25 ).toNanos() );

        RestClient.Builder builder = RestClient.builder();
        EnginePlanClient client = new EnginePlanClient( builder, properties, telemetryService, nanos );

        return new EngineExecutionService( client, executionPort, telemetryService, clock, nanos );
    }

    private static MockEnvironment liveEnvironment( String venue )
    {
        return new MockEnvironment()
            .withProperty( "engine.live-enabled-venues", "bybit,gate" )
            .withProperty( "engine.kill-switch-enabled", "false" )
            .withProperty( "engine.max-notional-usd", "25" )
            .withProperty( "engine.metadata-max-age-minutes", "1440" )
            .withProperty( "engine.latency-max-age-minutes", "1440" )
            .withProperty( "engine.trading-venue-access-mode", "testnet" )
            .withProperty( "engine.credentials." + venue + ".api-key", "test-key" )
            .withProperty( "engine.credentials." + venue + ".secret-key", "test-secret" );
    }

    private static LongSupplier advancingNanos( long startNanos, long stepNanos )
    {
        AtomicLong current = new AtomicLong( startNanos );
        return () -> current.getAndAdd( stepNanos );
    }

    private static String entryPlanJson()
    {
        // Single entry attempt with triggerAt in the past (before NOW = 2030-01-01T00:00:00Z)
        return """
            [{
              "armedTradeId": 5,
              "fundingEventId": 10,
              "venue": "gate",
              "symbol": "REQ/USDT",
              "intendedSide": "SHORT",
              "notionalUsd": 25,
              "tradeState": "ARMED",
              "fundingTime": "2030-01-01T00:00:00Z",
              "plannedEntryAt": "2029-12-31T23:59:00Z",
              "plannedExitAt": "2030-01-01T00:01:00Z",
              "entryAttemptCount": 1,
              "entrySpacingMs": 0,
              "measuredEntryLatencyMs": 25,
              "manualLatencyAdjustmentMs": 0,
              "effectiveEntryLatencyMs": 25,
              "entryAttempts": [{
                "attemptNumber": 1,
                "targetEntryAt": "2029-12-31T23:59:00Z",
                "triggerAt": "2029-12-31T23:58:59.975Z",
                "offsetMs": 0,
                "spacingMs": 0,
                "effectiveLatencyMs": 25
              }],
              "status": "ENTRY_WINDOW",
              "nextActionAt": "2029-12-31T23:59:00Z",
              "millisUntilAction": 0,
              "millisUntilFunding": 0,
              "summary": "entry window",
              "venueSymbol": "REQ_USDT",
              "minOrderQty": 1,
              "qtyStep": 1,
              "minNotionalValue": 5,
              "metadataLastSyncedAt": "2029-12-31T00:00:00Z",
              "latencySampledAt": "2029-12-31T00:00:00Z",
              "positionQuantity": null,
              "positionEntryPrice": null
            }]
            """;
    }

    private static String exitPlanJson()
    {
        return """
            [{
              "armedTradeId": 5,
              "fundingEventId": 10,
              "venue": "gate",
              "symbol": "REQ/USDT",
              "intendedSide": "SHORT",
              "notionalUsd": 25,
              "tradeState": "OPEN",
              "fundingTime": "2030-01-01T00:00:00Z",
              "plannedEntryAt": "2029-12-31T23:59:00Z",
              "plannedExitAt": "2029-12-31T23:59:30Z",
              "entryAttemptCount": 1,
              "entrySpacingMs": 0,
              "measuredEntryLatencyMs": 25,
              "manualLatencyAdjustmentMs": 0,
              "effectiveEntryLatencyMs": 25,
              "entryAttempts": [{
                "attemptNumber": 1,
                "targetEntryAt": "2029-12-31T23:59:00Z",
                "triggerAt": "2029-12-31T23:58:59.975Z",
                "offsetMs": 0,
                "spacingMs": 0,
                "effectiveLatencyMs": 25
              }],
              "status": "EXIT_WINDOW",
              "nextActionAt": "2029-12-31T23:59:30Z",
              "millisUntilAction": 0,
              "millisUntilFunding": 0,
              "summary": "exit window",
              "venueSymbol": "REQ_USDT",
              "minOrderQty": 1,
              "qtyStep": 1,
              "minNotionalValue": 5,
              "metadataLastSyncedAt": "2029-12-31T00:00:00Z",
              "latencySampledAt": "2029-12-31T00:00:00Z",
              "positionQuantity": 1000,
              "positionEntryPrice": 2.5
            }]
            """;
    }

    private static String entryAttemptResponseJson()
    {
        return """
            {
              "id": 1,
              "attemptKey": "entry:5:1:2029-12-31T23:59:00Z",
              "armedTradeId": 5,
              "attemptNumber": 1,
              "venue": "gate",
              "symbol": "REQ/USDT",
              "side": "SHORT",
              "executionType": "MARKET",
              "quantity": 1000,
              "status": "FILLED",
              "externalOrderId": "gate-entry-1",
              "submittedAt": "2029-12-31T23:58:59.975Z",
              "exchangeTimestamp": "2029-12-31T23:59:00Z",
              "averageFillPrice": 2.5,
              "filledQuantity": 1000,
              "feeUsd": 0.01,
              "createdAt": "2029-12-31T23:59:00Z",
              "updatedAt": "2029-12-31T23:59:00Z"
            }
            """;
    }

    private static String positionResponseJson( String state, String closedAt )
    {
        return """
            {
              "id": 1,
              "armedTradeId": 5,
              "venue": "gate",
              "symbol": "req/usdt",
              "side": "SHORT",
              "quantity": 1000,
              "entryPrice": 2.5,
              "exitPrice": null,
              "state": "%s",
              "openedAt": "2029-12-31T23:59:00Z",
              "closedAt": %s,
              "createdAt": "2029-12-31T23:59:00Z",
              "updatedAt": "2029-12-31T23:59:00Z"
            }
            """.formatted( state, closedAt == null ? "null" : "\"" + closedAt + "\"" );
    }

    private static String stateUpdateResponseJson( String state )
    {
        return """
            {"armedTradeId": 5, "state": "%s", "updatedAt": "2030-01-01T00:00:00Z"}
            """.formatted( state );
    }

    private static String outcomeResponseJson()
    {
        return """
            {
              "id": 1,
              "armedTradeId": 5,
              "grossPnlUsd": -1.0,
              "netPnlUsd": -1.01,
              "feesUsd": 0.01,
              "outcomeCode": "CLOSED",
              "notes": null,
              "evaluatedAt": "2030-01-01T00:00:00Z",
              "createdAt": "2030-01-01T00:00:00Z",
              "updatedAt": "2030-01-01T00:00:00Z"
            }
            """;
    }
}
