package com.crypto.funding.api;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.crypto.funding.infrastructure.persistence.repository.OperatorExchangeCredentialJpaRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Date;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    "security.cloudflare.audience=test-aud",
    "security.cloudflare.team-domain=test-team.cloudflareaccess.com",
    "credentials.storage.enabled=true",
    "credentials.storage.master-key-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
class OperatorSecurityIntegrationTest
{
    private static final WireMockServer JWKS_SERVER = new WireMockServer( options().dynamicPort() );
    private static final WireMockServer BYBIT_SERVER = new WireMockServer( options().dynamicPort() );

    private static RSAKey RSA_KEY;

    @BeforeAll
    static void startServers() throws Exception
    {
        RSA_KEY = new RSAKeyGenerator( 2048 )
            .keyUse( KeyUse.SIGNATURE )
            .keyID( "test-kid-1" )
            .generate();

        String jwksBody = new com.nimbusds.jose.jwk.JWKSet( RSA_KEY.toPublicJWK() ).toString();

        JWKS_SERVER.start();
        JWKS_SERVER.stubFor( com.github.tomakehurst.wiremock.client.WireMock.get( urlPathEqualTo( "/cdn-cgi/access/certs" ) )
            .willReturn( okJson( jwksBody ) ) );

        BYBIT_SERVER.start();
    }

    @AfterAll
    static void stopServers()
    {
        JWKS_SERVER.stop();
        BYBIT_SERVER.stop();
    }

    @DynamicPropertySource
    static void configureProperties( DynamicPropertyRegistry registry )
    {
        registry.add( "security.cloudflare.certs-url",
            () -> JWKS_SERVER.baseUrl() + "/cdn-cgi/access/certs" );
        registry.add( "trading.bybit.production.base-url", BYBIT_SERVER::baseUrl );
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OperatorExchangeCredentialJpaRepository credentialRepository;

    @BeforeEach
    void clean()
    {
        BYBIT_SERVER.resetAll();
        credentialRepository.deleteAll();
    }

    @Test
    void rejectsApiWithoutJwt() throws Exception
    {
        mockMvc.perform( get( "/api/v1/candidates" ) )
               .andExpect( status().isUnauthorized() )
               .andExpect( jsonPath( "$.message" ).value( "Valid Cloudflare Access session required." ) );
    }

    @Test
    void rejectsApiWithInvalidJwt() throws Exception
    {
        mockMvc.perform( get( "/api/v1/candidates" )
                .header( "Cf-Access-Jwt-Assertion", "not.a.jwt" ) )
               .andExpect( status().isUnauthorized() );
    }

    @Test
    void acceptsValidJwtAndAutoProvisions() throws Exception
    {
        String jwt = buildJwt( "alice@example.com", "test-aud" );

        mockMvc.perform( get( "/api/v1/candidates" )
                .header( "Cf-Access-Jwt-Assertion", jwt ) )
               .andExpect( status().isOk() );
    }

    @Test
    void isolatesCredentialsByEmail() throws Exception
    {
        String aliceJwt = buildJwt( "alice@example.com", "test-aud" );
        String bobJwt = buildJwt( "bob@example.com", "test-aud" );

        mockMvc.perform( put( "/api/v1/operators/me/credentials/bybit/production" )
                .header( "Cf-Access-Jwt-Assertion", aliceJwt )
                .contentType( MediaType.APPLICATION_JSON )
                .content( """
                    {
                      "apiKey":"alice-public-key",
                      "secretKey":"alice-secret-key",
                      "passphrase":""
                    }
                    """ ) )
               .andExpect( status().isOk() );

        mockMvc.perform( get( "/api/v1/operators/me/credentials" )
                .header( "Cf-Access-Jwt-Assertion", bobJwt ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$" ).isEmpty() );

        mockMvc.perform( get( "/api/v1/operators/me/credentials" )
                .header( "Cf-Access-Jwt-Assertion", aliceJwt ) )
               .andExpect( status().isOk() )
               .andExpect( jsonPath( "$[0].venue" ).value( "bybit" ) )
               .andExpect( jsonPath( "$[0].mode" ).value( "PRODUCTION" ) );
    }

    @Test
    void rejectsExpiredJwt() throws Exception
    {
        String jwt = buildJwtExpiredAt( "alice@example.com", "test-aud",
            new Date( System.currentTimeMillis() - 60_000 ) );

        mockMvc.perform( get( "/api/v1/candidates" )
                .header( "Cf-Access-Jwt-Assertion", jwt ) )
               .andExpect( status().isUnauthorized() );
    }

    @Test
    void rejectsWrongAudience() throws Exception
    {
        String jwt = buildJwt( "alice@example.com", "wrong-aud" );

        mockMvc.perform( get( "/api/v1/candidates" )
                .header( "Cf-Access-Jwt-Assertion", jwt ) )
               .andExpect( status().isUnauthorized() );
    }

    private static String buildJwt( String email, String audience ) throws Exception
    {
        return buildJwtExpiredAt( email, audience,
            new Date( System.currentTimeMillis() + 3_600_000 ) );
    }

    private static String buildJwtExpiredAt( String email, String audience, Date exp ) throws Exception
    {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .subject( email )
            .claim( "email", email )
            .issuer( "https://test-team.cloudflareaccess.com" )
            .audience( audience )
            .expirationTime( exp )
            .jwtID( UUID.randomUUID().toString() )
            .build();

        SignedJWT jwt = new SignedJWT(
            new JWSHeader.Builder( JWSAlgorithm.RS256 ).keyID( RSA_KEY.getKeyID() ).build(),
            claims
        );
        jwt.sign( new RSASSASigner( RSA_KEY ) );
        return jwt.serialize();
    }
}
