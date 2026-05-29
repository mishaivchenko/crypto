package com.crypto.funding.api;

import com.crypto.funding.infrastructure.persistence.repository.OperatorExchangeCredentialJpaRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:sqlite:./build/test-operator-security.sqlite",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.datasource.hikari.maximum-pool-size=1",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
    "trading.candidate-source.enabled=false",
    "trading.metadata.sync-on-startup=false",
    "trading.metadata.schedule-enabled=false",
    "trading.metadata.require-credentials-on-startup=false",
    "security.operators.auth-enabled=true",
    "security.operators.bootstrap-users=alice:alice-token,bob:bob-token",
    "credentials.storage.enabled=true",
    "credentials.storage.master-key-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
class OperatorSecurityIntegrationTest
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
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OperatorExchangeCredentialJpaRepository credentialRepository;

    @BeforeEach
    void cleanCredentials()
    {
        BYBIT_SERVER.resetAll();
        credentialRepository.deleteAll();
    }

    @AfterAll
    static void stopWireMock()
    {
        BYBIT_SERVER.stop();
    }

    @Test
    void rejectsOperatorApiWithoutToken() throws Exception
    {
        mockMvc.perform( get( "/api/v1/candidates" ) )
               .andExpect( status().isUnauthorized() )
               .andExpect( jsonPath( "$.message" ).value( "Valid X-Operator-Token is required." ) );
    }

    @Test
    void storesEncryptedCredentialsPerOperator() throws Exception
    {
        mockMvc.perform( put( "/api/v1/operators/me/credentials/bybit/production" )
                .header( "X-Operator-Token", "alice-token" )
                .contentType( MediaType.APPLICATION_JSON )
                .content( """
                    {
                      "apiKey":"alice-public-key",
                      "secretKey":"alice-secret-key",
                      "passphrase":"alice-passphrase"
                    }
                    """ ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.configured" ).value( true ) )
            .andExpect( jsonPath( "$.apiKeyMask" ).value( "****-key" ) )
            .andExpect( jsonPath( "$.secretKeyMask" ).value( "****-key" ) );

        mockMvc.perform( get( "/api/v1/operators/me/credentials" )
                .header( "X-Operator-Token", "bob-token" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$" ).isEmpty() );

        mockMvc.perform( get( "/api/v1/operators/me/credentials" )
                .header( "X-Operator-Token", "alice-token" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$[0].venue" ).value( "bybit" ) )
            .andExpect( jsonPath( "$[0].mode" ).value( "PRODUCTION" ) );

        var stored = credentialRepository.findAll().getFirst();
        assertThat( stored.getApiKeyCiphertext() ).doesNotContain( "alice-public-key" );
        assertThat( stored.getSecretKeyCiphertext() ).doesNotContain( "alice-secret-key" );
        assertThat( stored.getPassphraseCiphertext() ).doesNotContain( "alice-passphrase" );
    }

    @Test
    void checksStoredCredentialsWithoutReturningSecrets() throws Exception
    {
        BYBIT_SERVER.stubFor( com.github.tomakehurst.wiremock.client.WireMock.get( urlPathEqualTo( "/v5/account/wallet-balance" ) )
            .willReturn( okJson( """
                {
                  "retCode": 10003,
                  "retMsg": "invalid api key"
                }
                """ ) ) );

        mockMvc.perform( put( "/api/v1/operators/me/credentials/bybit/production" )
                .header( "X-Operator-Token", "alice-token" )
                .contentType( MediaType.APPLICATION_JSON )
                .content( """
                    {
                      "apiKey":"alice-public-key",
                      "secretKey":"alice-secret-key",
                      "passphrase":""
                    }
                    """ ) )
            .andExpect( status().isOk() );

        mockMvc.perform( post( "/api/v1/operators/me/credentials/bybit/production/check" )
                .header( "X-Operator-Token", "alice-token" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.configured" ).value( true ) )
            .andExpect( jsonPath( "$.apiKeyMask" ).value( "****-key" ) )
            .andExpect( jsonPath( "$.connectionStatus" ).value( "INVALID_CREDENTIALS" ) )
            .andExpect( jsonPath( "$.connectionMessage" ).value( "invalid api key" ) )
            .andExpect( jsonPath( "$.lastConnectionHttpStatus" ).value( 200 ) );
    }
}
