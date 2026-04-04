package com.crypto.funding.engine;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = EngineApplication.class,
    properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:sqlite:./build/test-engine-app.sqlite",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect"
    }
)
@AutoConfigureMockMvc
class EngineApplicationIntegrationTest
{
    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesEngineSummaryAndPlans() throws Exception
    {
        mockMvc.perform( get( "/internal/engine/summary" ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$.module" ).value( "engine-app" ) )
               .andExpect( jsonPath( "$.version" ).value( "2.0.0" ) )
               .andExpect( jsonPath( "$.totalPlans" ).value( 0 ) );

        mockMvc.perform( get( "/internal/engine/plans" ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$" ).isArray() );
    }
}
