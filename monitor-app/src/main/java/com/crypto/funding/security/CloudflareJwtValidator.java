package com.crypto.funding.security;

import com.crypto.funding.config.CloudflareAccessProperties;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class CloudflareJwtValidator
{
    private static final Logger log = LoggerFactory.getLogger( CloudflareJwtValidator.class );
    private static final Duration REFRESH_COOLDOWN = Duration.ofMinutes( 5 );

    private final CloudflareAccessProperties properties;
    private final AtomicReference<CachedJwks> cache = new AtomicReference<>();

    public CloudflareJwtValidator( CloudflareAccessProperties properties )
    {
        this.properties = properties;
    }

    public Optional<String> validateAndExtractEmail( String rawJwt )
    {
        if( rawJwt == null || rawJwt.isBlank() )
        {
            return Optional.empty();
        }
        try
        {
            SignedJWT jwt = SignedJWT.parse( rawJwt );
            String kid = jwt.getHeader().getKeyID();

            JWKSet jwks = resolveJwks( kid );
            if( jwks == null )
            {
                return Optional.empty();
            }

            JWK jwk = jwks.getKeyByKeyId( kid );
            if( jwk == null )
            {
                return Optional.empty();
            }

            JWSVerifier verifier = buildVerifier( jwk );
            if( !jwt.verify( verifier ) )
            {
                log.debug( "CF JWT signature verification failed" );
                return Optional.empty();
            }

            var claims = jwt.getJWTClaimsSet();

            Date exp = claims.getExpirationTime();
            if( exp == null || exp.toInstant().isBefore( Instant.now() ) )
            {
                log.debug( "CF JWT expired" );
                return Optional.empty();
            }

            List<String> aud = claims.getAudience();
            if( aud == null || !aud.contains( properties.getAudience() ) )
            {
                log.debug( "CF JWT audience mismatch" );
                return Optional.empty();
            }

            String issuer = claims.getIssuer();
            String expectedIssuer = "https://" + properties.getTeamDomain();
            if( !expectedIssuer.equals( issuer ) )
            {
                log.debug( "CF JWT issuer mismatch: expected={} actual={}", expectedIssuer, issuer );
                return Optional.empty();
            }

            String email = claims.getStringClaim( "email" );
            if( email == null || email.isBlank() )
            {
                email = claims.getSubject();
            }
            return Optional.ofNullable( email ).filter( e -> !e.isBlank() );
        }
        catch( Exception ex )
        {
            log.debug( "CF JWT validation error: {}", ex.getMessage() );
            return Optional.empty();
        }
    }

    private JWKSet resolveJwks( String kid )
    {
        CachedJwks current = cache.get();
        if( current != null && current.hasKey( kid ) )
        {
            return current.jwks();
        }
        // Unknown kid: always refresh — handles key rotation regardless of how recently
        // the cache was last populated. refreshJwks() updates the cache timestamp, so the
        // next call for a kid that was known will hit the hasKey fast-path above.
        return refreshJwks();
    }

    private JWKSet refreshJwks()
    {
        try
        {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout( Duration.ofSeconds( 5 ) )
                .build();
            HttpRequest req = HttpRequest.newBuilder()
                .uri( URI.create( properties.getCertsUrl() ) )
                .timeout( Duration.ofSeconds( 10 ) )
                .GET()
                .build();
            HttpResponse<String> resp = client.send( req, HttpResponse.BodyHandlers.ofString() );
            JWKSet jwks = JWKSet.parse( resp.body() );
            cache.set( new CachedJwks( jwks, Instant.now() ) );
            return jwks;
        }
        catch( Exception ex )
        {
            log.warn( "Failed to fetch CF JWKS from {}: {}", properties.getCertsUrl(), ex.getMessage() );
            CachedJwks stale = cache.get();
            return stale != null ? stale.jwks() : null;
        }
    }

    private JWSVerifier buildVerifier( JWK jwk ) throws Exception
    {
        if( jwk instanceof RSAKey rsaKey )
        {
            return new RSASSAVerifier( rsaKey.toRSAPublicKey() );
        }
        if( jwk instanceof ECKey ecKey )
        {
            return new ECDSAVerifier( ecKey.toECPublicKey() );
        }
        throw new IllegalArgumentException( "Unsupported JWK type: " + jwk.getKeyType() );
    }

    private record CachedJwks(JWKSet jwks, Instant fetchedAt)
    {
        boolean hasKey( String kid )
        {
            return kid != null && jwks.getKeyByKeyId( kid ) != null;
        }
    }
}
