package com.crypto.funding.api;

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

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    "security.operators.auth-enabled=true",
    "security.operators.bootstrap-users=alice:alice-token",
    "monitor.engine-control.internal-token=test-internal-token"
})
@AutoConfigureMockMvc
class MonitorDevToolsApiIntegrationTest
{
    private static final WireMockServer ENGINE = new WireMockServer( options().dynamicPort() );

    static
    {
        ENGINE.start();
    }

    @DynamicPropertySource
    static void configureProperties( DynamicPropertyRegistry registry )
    {
        registry.add( "monitor.engine-control.base-url", ENGINE::baseUrl );
    }

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetEngine()
    {
        ENGINE.resetAll();
        ENGINE.stubFor( com.github.tomakehurst.wiremock.client.WireMock.get( urlPathEqualTo( "/internal/engine/runtime" ) )
            .willReturn( okJson( """
                {
                  "module":"engine-app",
                  "version":"2.0.0",
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
    static void stopEngine()
    {
        ENGINE.stop();
    }

    @Test
    void requiresOperatorTokenForDevTools() throws Exception
    {
        mockMvc.perform( post( "/api/v2/monitor/dev/engine/run-once" ) )
               .andExpect( status().isUnauthorized() )
               .andExpect( jsonPath( "$.message" ).value( "Valid X-Operator-Token is required." ) );
    }

    @Test
    void proxiesRunOnceToEngineWithInternalToken() throws Exception
    {
        mockMvc.perform( post( "/api/v2/monitor/dev/engine/run-once" )
                .header( "X-Operator-Token", "alice-token" )
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
        mockMvc.perform( get( "/api/v2/monitor/dev/engine/runtime" )
                .header( "X-Operator-Token", "alice-token" ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$.executionLoopEnabled" ).value( true ) )
               .andExpect( jsonPath( "$.executionLoopIntervalMs" ).value( 1000 ) );

        mockMvc.perform( post( "/api/v2/monitor/dev/engine/runtime" )
                .header( "X-Operator-Token", "alice-token" )
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
}
