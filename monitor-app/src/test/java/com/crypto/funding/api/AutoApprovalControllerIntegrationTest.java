package com.crypto.funding.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:sqlite:./build/test-auto-approval-api.sqlite",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.datasource.hikari.maximum-pool-size=1",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
    "trading.candidate-source.enabled=false",
    "trading.metadata.sync-on-startup=false",
    "trading.metadata.schedule-enabled=false",
    "trading.metadata.require-credentials-on-startup=false",
    "security.operators.auth-enabled=false",
    "trading.auto-approval.enabled=false",
    "trading.auto-approval.max-notional-usd=500"
})
@AutoConfigureMockMvc
class AutoApprovalControllerIntegrationTest
{
    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void cleanRules() throws Exception
    {
        // delete all rules created by previous tests via list + delete
        var result = mockMvc.perform( get( "/api/v1/auto-approval/rules" ) )
                            .andReturn();
        String body = result.getResponse().getContentAsString();
        // extract ids by simple pattern — avoids ObjectMapper dependency in test
        var matcher = java.util.regex.Pattern.compile( "\"id\":(\\d+)" ).matcher( body );
        while( matcher.find() )
        {
            mockMvc.perform( delete( "/api/v1/auto-approval/rules/" + matcher.group( 1 ) ) );
        }
        // reset global toggle to disabled
        mockMvc.perform( post( "/api/v1/auto-approval/disable" ) );
    }

    @Test
    void statusReturnsDisabledByDefault() throws Exception
    {
        mockMvc.perform( get( "/api/v1/auto-approval/status" ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$.enabled" ).value( false ) );
    }

    @Test
    void enableAndDisableToggleGlobalFlag() throws Exception
    {
        mockMvc.perform( post( "/api/v1/auto-approval/enable" ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$.enabled" ).value( true ) );

        mockMvc.perform( get( "/api/v1/auto-approval/status" ) )
               .andExpect( jsonPath( "$.enabled" ).value( true ) );

        mockMvc.perform( post( "/api/v1/auto-approval/disable" ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$.enabled" ).value( false ) );
    }

    @Test
    void createRuleReturns201WithPersistedData() throws Exception
    {
        mockMvc.perform( post( "/api/v1/auto-approval/rules" )
                   .contentType( MediaType.APPLICATION_JSON )
                   .content( validRuleJson( "my-rule", "100", "AUTO_EXECUTE" ) ) )
               .andExpect( status().isCreated() )
               .andExpect( jsonPath( "$.id" ).isNumber() )
               .andExpect( jsonPath( "$.name" ).value( "my-rule" ) )
               .andExpect( jsonPath( "$.action" ).value( "AUTO_EXECUTE" ) )
               .andExpect( jsonPath( "$.defaultNotionalUsd" ).value( 100.0 ) )
               .andExpect( jsonPath( "$.enabled" ).value( true ) );
    }

    @Test
    void createRuleValidationRejects400WhenNameMissing() throws Exception
    {
        mockMvc.perform( post( "/api/v1/auto-approval/rules" )
                   .contentType( MediaType.APPLICATION_JSON )
                   .content( "{\"defaultNotionalUsd\":100,\"defaultSide\":\"SHORT\",\"action\":\"AUTO_EXECUTE\"}" ) )
               .andExpect( status().isBadRequest() );
    }

    @Test
    void createRuleValidationRejects400WhenNotionalMissing() throws Exception
    {
        mockMvc.perform( post( "/api/v1/auto-approval/rules" )
                   .contentType( MediaType.APPLICATION_JSON )
                   .content( "{\"name\":\"x\",\"defaultSide\":\"SHORT\",\"action\":\"AUTO_EXECUTE\"}" ) )
               .andExpect( status().isBadRequest() );
    }

    @Test
    void listRulesReturnsCreatedRule() throws Exception
    {
        mockMvc.perform( post( "/api/v1/auto-approval/rules" )
                   .contentType( MediaType.APPLICATION_JSON )
                   .content( validRuleJson( "list-test-rule", "200", "AUTO_REJECT" ) ) )
               .andExpect( status().isCreated() );

        mockMvc.perform( get( "/api/v1/auto-approval/rules" ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$[?(@.name == 'list-test-rule')]" ).exists() );
    }

    @Test
    void updateRuleChangesName() throws Exception
    {
        String createResponse = mockMvc.perform( post( "/api/v1/auto-approval/rules" )
                   .contentType( MediaType.APPLICATION_JSON )
                   .content( validRuleJson( "original-name", "100", "AUTO_EXECUTE" ) ) )
               .andExpect( status().isCreated() )
               .andReturn().getResponse().getContentAsString();

        long id = extractId( createResponse );

        mockMvc.perform( put( "/api/v1/auto-approval/rules/" + id )
                   .contentType( MediaType.APPLICATION_JSON )
                   .content( validRuleJson( "updated-name", "100", "AUTO_EXECUTE" ) ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$.name" ).value( "updated-name" ) );
    }

    @Test
    void deleteRuleRemovesIt() throws Exception
    {
        String createResponse = mockMvc.perform( post( "/api/v1/auto-approval/rules" )
                   .contentType( MediaType.APPLICATION_JSON )
                   .content( validRuleJson( "delete-me", "100", "AUTO_EXECUTE" ) ) )
               .andExpect( status().isCreated() )
               .andReturn().getResponse().getContentAsString();

        long id = extractId( createResponse );

        mockMvc.perform( delete( "/api/v1/auto-approval/rules/" + id ) )
               .andExpect( status().isNoContent() );

        mockMvc.perform( get( "/api/v1/auto-approval/rules" ) )
               .andExpect( jsonPath( "$[?(@.id == " + id + ")]" ).doesNotExist() );
    }

    @Test
    void enableDisableRuleTogglesEnabledFlag() throws Exception
    {
        String createResponse = mockMvc.perform( post( "/api/v1/auto-approval/rules" )
                   .contentType( MediaType.APPLICATION_JSON )
                   .content( validRuleJson( "toggle-me", "100", "AUTO_EXECUTE" ) ) )
               .andExpect( status().isCreated() )
               .andReturn().getResponse().getContentAsString();

        long id = extractId( createResponse );

        mockMvc.perform( post( "/api/v1/auto-approval/rules/" + id + "/disable" ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$.enabled" ).value( false ) );

        mockMvc.perform( post( "/api/v1/auto-approval/rules/" + id + "/enable" ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$.enabled" ).value( true ) );
    }

    @Test
    void statusReflectsActiveRuleCount() throws Exception
    {
        mockMvc.perform( post( "/api/v1/auto-approval/rules" )
                   .contentType( MediaType.APPLICATION_JSON )
                   .content( validRuleJson( "active-rule", "100", "AUTO_EXECUTE" ) ) )
               .andExpect( status().isCreated() );

        mockMvc.perform( get( "/api/v1/auto-approval/status" ) )
               .andExpect( jsonPath( "$.activeRulesCount" ).value( 1 ) );
    }

    @Test
    void returns404ForUnknownRuleUpdate() throws Exception
    {
        mockMvc.perform( put( "/api/v1/auto-approval/rules/99999" )
                   .contentType( MediaType.APPLICATION_JSON )
                   .content( validRuleJson( "x", "100", "AUTO_EXECUTE" ) ) )
               .andExpect( status().isNotFound() );
    }

    // --- helpers ---

    private static String validRuleJson( String name, String notional, String action )
    {
        return "{\"name\":\"" + name + "\",\"enabled\":true,\"mode\":\"BOTH\","
            + "\"defaultNotionalUsd\":" + notional + ",\"defaultSide\":\"SHORT\","
            + "\"action\":\"" + action + "\",\"priority\":1}";
    }

    private static long extractId( String json )
    {
        var m = java.util.regex.Pattern.compile( "\"id\":(\\d+)" ).matcher( json );
        if( m.find() )
        {
            return Long.parseLong( m.group( 1 ) );
        }
        throw new IllegalArgumentException( "No id in: " + json );
    }
}
