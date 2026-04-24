package com.crypto.funding.engine;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = EngineApplication.class, properties = {
    "engine.internal-token=test-internal-token",
    "engine.execution-loop-enabled=false",
    "engine.metrics-publish.enabled=true",
    "engine.metrics-publish.interval-ms=600000"
})
class EngineMetricsPublisherIntegrationTest
{
    private static final WireMockServer MONITOR = new WireMockServer( options().dynamicPort() );

    static
    {
        MONITOR.start();
    }

    @DynamicPropertySource
    static void configureProperties( DynamicPropertyRegistry registry )
    {
        registry.add( "engine.monitor-base-url", MONITOR::baseUrl );
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private EngineMetricsPublisher publisher;

    @BeforeEach
    void resetMonitor()
    {
        MONITOR.resetAll();
        MONITOR.stubFor( get( urlEqualTo( "/internal/v1/engine/plans?includeAll=false" ) )
            .willReturn( okJson( """
                [
                  {
                    "armedTradeId":5,
                    "fundingEventId":13,
                    "venue":"bybit",
                    "symbol":"REQ/USDT",
                    "intendedSide":"SHORT",
                    "notionalUsd":25,
                    "tradeState":"ARMED",
                    "fundingTime":"2030-01-01T00:00:00Z",
                    "plannedEntryAt":"2029-12-31T23:59:00Z",
                    "plannedExitAt":"2030-01-01T00:01:00Z",
                    "entryAttemptCount":2,
                    "entrySpacingMs":150,
                    "manualLatencyAdjustmentMs":0,
                    "effectiveEntryLatencyMs":0,
                    "entryAttempts":[],
                    "status":"ENTRY_WINDOW",
                    "nextActionAt":"2029-12-31T23:59:00Z",
                    "millisUntilAction":1000,
                    "millisUntilFunding":61000,
                    "summary":"Ожидаем вход"
                  }
                ]
                """ ) ) );
        MONITOR.stubFor( post( urlEqualTo( "/internal/v1/engine/metrics-snapshot" ) )
            .willReturn( okJson( "{}" ) ) );
    }

    @AfterAll
    static void stopMonitor()
    {
        MONITOR.stop();
    }

    @Test
    void publishesLowCardinalityMetricsSnapshotToMonitor()
    {
        // REQ: ENG-ACC-004
        assertThat( applicationContext.getBeansOfType( EngineMetricsPublisher.class ) ).hasSize( 1 );

        publisher.publishSnapshot();

        MONITOR.verify( getRequestedFor( urlEqualTo( "/internal/v1/engine/plans?includeAll=false" ) )
            .withHeader( "X-Internal-Token", equalTo( "test-internal-token" ) ) );
        MONITOR.verify( postRequestedFor( urlEqualTo( "/internal/v1/engine/metrics-snapshot" ) )
            .withHeader( "X-Internal-Token", equalTo( "test-internal-token" ) )
            .withRequestBody( matchingJsonPath( "$.module", equalTo( "engine-app" ) ) )
            .withRequestBody( matchingJsonPath( "$.engineUp", equalTo( "true" ) ) )
            .withRequestBody( matchingJsonPath( "$.executionLoopEnabled", equalTo( "false" ) ) )
            .withRequestBody( matchingJsonPath( "$.totalPlans", equalTo( "1" ) ) )
            .withRequestBody( matchingJsonPath( "$.actionablePlans", equalTo( "1" ) ) )
            .withRequestBody( matchingJsonPath( "$.statusBreakdown.ENTRY_WINDOW", equalTo( "1" ) ) )
            .withRequestBody( matchingJsonPath( "$.planVenueBreakdown.bybit", equalTo( "1" ) ) )
            .withRequestBody( matchingJsonPath( "$.executionRuns", equalTo( "0" ) ) ) );
    }
}
