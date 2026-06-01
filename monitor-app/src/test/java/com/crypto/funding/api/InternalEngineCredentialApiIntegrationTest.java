package com.crypto.funding.api;

import com.crypto.funding.domain.venue.VenueAccessMode;
import com.crypto.funding.infrastructure.persistence.model.OperatorExchangeCredentialEntity;
import com.crypto.funding.infrastructure.persistence.repository.OperatorExchangeCredentialJpaRepository;
import org.junit.jupiter.api.BeforeEach;
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
    "spring.datasource.url=jdbc:sqlite:./build/test-internal-engine-credential-api.sqlite",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.datasource.hikari.maximum-pool-size=1",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
    "trading.candidate-source.enabled=false",
    "trading.metadata.sync-on-startup=false",
    "trading.metadata.schedule-enabled=false",
    "trading.metadata.require-credentials-on-startup=false",
    "security.operators.auth-enabled=false",
    "security.operators.internal-token=test-internal-token",
    "credentials.storage.enabled=true",
    "credentials.storage.master-key-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
class InternalEngineCredentialApiIntegrationTest
{
    private static final String TOKEN = "test-internal-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OperatorExchangeCredentialJpaRepository credentialRepository;

    @Autowired
    private com.crypto.funding.application.port.CredentialCipherPort cipher;

    @BeforeEach
    void clean()
    {
        credentialRepository.deleteAll();
    }

    @Test
    void returnsDecryptedCredentialsForKnownVenue() throws Exception
    {
        saveCredential( "gate", VenueAccessMode.TESTNET, "my-api-key", "my-secret", null );

        mockMvc.perform( get( "/internal/v1/engine/credentials/gate" )
                .header( "X-Internal-Token", TOKEN )
                .queryParam( "mode", "testnet" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.apiKey" ).value( "my-api-key" ) )
            .andExpect( jsonPath( "$.secretKey" ).value( "my-secret" ) )
            .andExpect( jsonPath( "$.passphrase" ).isEmpty() );
    }

    @Test
    void returnsDecryptedCredentialsWithPassphrase() throws Exception
    {
        saveCredential( "okx", VenueAccessMode.TESTNET, "okx-key", "okx-secret", "okx-pass" );

        mockMvc.perform( get( "/internal/v1/engine/credentials/okx" )
                .header( "X-Internal-Token", TOKEN )
                .queryParam( "mode", "testnet" ) )
            .andExpect( status().isOk() )
            .andExpect( jsonPath( "$.apiKey" ).value( "okx-key" ) )
            .andExpect( jsonPath( "$.secretKey" ).value( "okx-secret" ) )
            .andExpect( jsonPath( "$.passphrase" ).value( "okx-pass" ) );
    }

    @Test
    void returns404WhenCredentialsMissing() throws Exception
    {
        mockMvc.perform( get( "/internal/v1/engine/credentials/bybit" )
                .header( "X-Internal-Token", TOKEN )
                .queryParam( "mode", "testnet" ) )
            .andExpect( status().isNotFound() );
    }

    @Test
    void returns400ForInvalidMode() throws Exception
    {
        mockMvc.perform( get( "/internal/v1/engine/credentials/gate" )
                .header( "X-Internal-Token", TOKEN )
                .queryParam( "mode", "invalid" ) )
            .andExpect( status().isBadRequest() );
    }

    @Test
    void returns401WithoutInternalToken() throws Exception
    {
        mockMvc.perform( get( "/internal/v1/engine/credentials/gate" )
                .queryParam( "mode", "testnet" ) )
            .andExpect( status().isUnauthorized() );
    }

    @Test
    void returns401WithWrongInternalToken() throws Exception
    {
        mockMvc.perform( get( "/internal/v1/engine/credentials/gate" )
                .header( "X-Internal-Token", "wrong-token" )
                .queryParam( "mode", "testnet" ) )
            .andExpect( status().isUnauthorized() );
    }

    private void saveCredential( String venue, VenueAccessMode mode, String apiKey, String secret, String passphrase )
    {
        OperatorExchangeCredentialEntity entity = new OperatorExchangeCredentialEntity();
        entity.setOperatorId( 1L );
        entity.setVenue( venue );
        entity.setMode( mode );
        entity.setApiKeyCiphertext( cipher.encrypt( apiKey ) );
        entity.setSecretKeyCiphertext( cipher.encrypt( secret ) );
        entity.setPassphraseCiphertext( passphrase != null ? cipher.encrypt( passphrase ) : null );
        entity.setApiKeyMask( "****" );
        entity.setSecretKeyMask( "****" );
        entity.setPassphraseMask( passphrase != null ? "****" : null );
        entity.setConnectionStatus( com.crypto.funding.domain.venue.VenueConnectionStatus.CONNECTED );
        credentialRepository.save( entity );
    }
}
