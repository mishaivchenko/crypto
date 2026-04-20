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
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
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
        MONITOR.stubFor( get( urlEqualTo( "/internal/v1/engine/plans" ) )
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
        mockMvc.perform( org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get( "/internal/engine/summary" ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$.module" ).value( "engine-app" ) )
               .andExpect( jsonPath( "$.version" ).value( "2.0.0" ) )
               .andExpect( jsonPath( "$.totalPlans" ).value( 0 ) );

        mockMvc.perform( org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get( "/internal/engine/plans" ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$" ).isArray() );

        MONITOR.verify( getRequestedFor( urlEqualTo( "/internal/v1/engine/plans" ) )
            .withHeader( "X-Internal-Token", equalTo( "test-internal-token" ) ) );
    }
}
