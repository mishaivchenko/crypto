package com.crypto.funding.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:sqlite:./build/test-internal-engine-plan-api.sqlite",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.datasource.hikari.maximum-pool-size=1",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
    "trading.candidate-source.enabled=false",
    "trading.metadata.sync-on-startup=false",
    "trading.metadata.schedule-enabled=false",
    "trading.metadata.require-credentials-on-startup=false",
    "security.operators.auth-enabled=false",
    "security.operators.internal-token=test-internal-token"
})
@AutoConfigureMockMvc
class InternalEnginePlanApiIntegrationTest
{
    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectsEnginePlanApiWithInternalToken() throws Exception
    {
        mockMvc.perform( get( "/internal/v1/engine/plans" ) )
               .andExpect( status().isUnauthorized() )
               .andExpect( jsonPath( "$.message" ).value( "Valid X-Internal-Token is required." ) );

        mockMvc.perform( get( "/internal/v1/engine/plans" )
                .header( "X-Internal-Token", "test-internal-token" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$" ).isArray() );
    }
}
