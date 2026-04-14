package com.crypto.funding.infrastructure.exchange.gate;

import com.crypto.funding.application.port.VenueCredentialCheckPort;
import com.crypto.funding.config.VenueHttpProperties;
import com.crypto.funding.domain.venue.VenueAccessMode;
import com.crypto.funding.domain.venue.VenueConnectionStatus;
import com.crypto.funding.infrastructure.exchange.support.CredentialCheckSupport;
import com.crypto.funding.trading.HmacSigner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Component
public class GateCredentialChecker implements VenueCredentialCheckPort
{
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VenueHttpProperties venueHttpProperties;

    public GateCredentialChecker( HttpClient httpClient, VenueHttpProperties venueHttpProperties )
    {
        this.httpClient = httpClient;
        this.venueHttpProperties = venueHttpProperties;
    }

    @Override
    public String venue()
    {
        return "gate";
    }

    @Override
    public List<VenueAccessMode> supportedModes()
    {
        return List.of( VenueAccessMode.TESTNET, VenueAccessMode.PRODUCTION );
    }

    @Override
    public Result check( Credentials credentials ) throws IOException, InterruptedException
    {
        long timestamp = System.currentTimeMillis() / 1000L;
        String requestPath = "/wallet/total_balance";
        String bodyHash = CredentialCheckSupport.sha512Hex( "" );
        String signatureString = "GET\n/api/v4" + requestPath + "\n\n" + bodyHash + "\n" + timestamp;
        String sign = HmacSigner.hmacSha512( credentials.secretKey(), signatureString );

        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( URI.create( credentials.baseUrl() + requestPath ) )
                                         .timeout( Duration.ofMillis( venueHttpProperties.getRequestTimeoutMs() ) )
                                         .header( "KEY", credentials.apiKey() )
                                         .header( "Timestamp", String.valueOf( timestamp ) )
                                         .header( "SIGN", sign )
                                         .GET()
                                         .build();

        HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString() );
        JsonNode root = parseBody( response.body() );
        String message = root.path( "message" ).asText( root.path( "label" ).asText( response.body() ) );
        if( response.statusCode() == 200 )
        {
            return new Result( VenueConnectionStatus.CONNECTED, "Ключи приняты биржей.", 200 );
        }
        if( response.statusCode() == 401 || response.statusCode() == 403 || CredentialCheckSupport.looksLikeInvalidCredentials( message ) )
        {
            return new Result( VenueConnectionStatus.INVALID_CREDENTIALS, message, response.statusCode() );
        }
        return new Result( VenueConnectionStatus.ERROR, message, response.statusCode() );
    }

    private JsonNode parseBody( String body ) throws IOException
    {
        return body == null || body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree( body );
    }
}
