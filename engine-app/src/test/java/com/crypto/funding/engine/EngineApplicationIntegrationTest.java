package com.crypto.funding.engine;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = EngineApplication.class,
    properties = {
        "engine.internal-token=test-internal-token"
    }
)
@AutoConfigureMockMvc
class EngineApplicationIntegrationTest
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
    private MockMvc mockMvc;

    @BeforeEach
    void resetMonitor()
    {
        MONITOR.resetAll();
        MONITOR.stubFor( get( urlEqualTo( "/internal/v1/engine/plans?includeAll=false" ) )
            .willReturn( okJson( "[]" ) ) );
    }

    @AfterAll
    static void stopMonitor()
    {
        MONITOR.stop();
    }

    @Test
    void exposesEngineSummaryAndPlans() throws Exception
    {
        // REQ: ENG-ACC-001
        mockMvc.perform( org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get( "/internal/engine/summary" ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$.module" ).value( "engine-app" ) )
               .andExpect( jsonPath( "$.version" ).value( "2.0.0" ) )
               .andExpect( jsonPath( "$.totalPlans" ).value( 0 ) );

        mockMvc.perform( org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get( "/internal/engine/plans" ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$" ).isArray() );

        MONITOR.verify( getRequestedFor( urlEqualTo( "/internal/v1/engine/plans?includeAll=false" ) )
            .withHeader( "X-Internal-Token", equalTo( "test-internal-token" ) ) );
    }

    @Test
    void exposesAndUpdatesRuntimeControls() throws Exception
    {
        // REQ: ENG-ACC-002
        mockMvc.perform( org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get( "/internal/engine/runtime" ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$.executionLoopEnabled" ).value( false ) )
               .andExpect( jsonPath( "$.executionLoopIntervalMs" ).value( 1000 ) );

        mockMvc.perform( org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post( "/internal/engine/runtime" )
                .contentType( "application/json" )
                .content( """
                    {
                      "executionLoopEnabled": true,
                      "executionLoopIntervalMs": 1750
                    }
                    """ ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$.executionLoopEnabled" ).value( true ) )
               .andExpect( jsonPath( "$.executionLoopIntervalMs" ).value( 1750 ) );
    }

    @Test
    void runOnceRecordsFailedAttemptsWhenEngineCredentialsAreMissing() throws Exception
    {
        // REQ: ENG-ACC-003
        MONITOR.stubFor( get( urlEqualTo( "/internal/v1/engine/plans?includeAll=true" ) )
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
                    "entryAttempts":[
                      {
                        "attemptNumber":1,
                        "targetEntryAt":"2029-12-31T23:59:00Z",
                        "triggerAt":"2029-12-31T23:59:00Z",
                        "millisUntilTrigger":1000,
                        "offsetFromFirstEntryMs":0,
                        "effectiveLatencyMs":0
                      },
                      {
                        "attemptNumber":2,
                        "targetEntryAt":"2029-12-31T23:59:00.150Z",
                        "triggerAt":"2029-12-31T23:59:00.150Z",
                        "millisUntilTrigger":1150,
                        "offsetFromFirstEntryMs":150,
                        "effectiveLatencyMs":0
                      }
                    ],
                    "status":"WAITING_ENTRY",
                    "nextActionAt":"2029-12-31T23:59:00Z",
                    "millisUntilAction":1000,
                    "millisUntilFunding":61000,
                    "summary":"Ожидаем вход"
                  }
                ]
                """ ) ) );
        MONITOR.stubFor( post( urlEqualTo( "/internal/v1/engine/order-attempts" ) )
            .willReturn( okJson( """
                {
                  "id":101,
                  "attemptKey":"entry:5:1:2029-12-31T23:59:00Z",
                  "armedTradeId":5,
                  "attemptNumber":1,
                  "venue":"bybit",
                  "symbol":"REQ/USDT",
                  "side":"SHORT",
                  "executionType":"MARKET",
                  "quantity":25,
                  "status":"FAILED",
                  "targetEntryAt":"2029-12-31T23:59:00Z",
                  "triggerAt":"2029-12-31T23:59:00Z",
                  "submittedAt":"2029-12-31T23:59:00Z",
                  "failureReason":"Missing engine credentials for bybit.",
                  "createdAt":"2029-12-31T23:59:00Z",
                  "updatedAt":"2029-12-31T23:59:00Z"
                }
                """ ) ) );

        mockMvc.perform( org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/internal/engine/execution/run-once?force=true" ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$.force" ).value( true ) )
               .andExpect( jsonPath( "$.plansScanned" ).value( 1 ) )
               .andExpect( jsonPath( "$.attemptsSubmitted" ).value( 2 ) )
               .andExpect( jsonPath( "$.results[0].status" ).value( "FAILED" ) );

        MONITOR.verify( getRequestedFor( urlEqualTo( "/internal/v1/engine/plans?includeAll=true" ) )
            .withHeader( "X-Internal-Token", equalTo( "test-internal-token" ) ) );
        MONITOR.verify( 2, postRequestedFor( urlEqualTo( "/internal/v1/engine/order-attempts" ) )
            .withHeader( "X-Internal-Token", equalTo( "test-internal-token" ) ) );
    }
}
