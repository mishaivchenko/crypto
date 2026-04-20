package com.crypto.funding.infrastructure.exchange.bybit;

import com.crypto.funding.application.port.VenueCredentialCheckPort;
import com.crypto.funding.config.VenueHttpProperties;
import com.crypto.funding.domain.venue.VenueAccessMode;
import com.crypto.funding.domain.venue.VenueConnectionStatus;
import com.crypto.funding.infrastructure.exchange.support.CredentialCheckSupport;
import com.crypto.funding.crypto.HmacSigner;
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
public class BybitCredentialChecker implements VenueCredentialCheckPort
{
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VenueHttpProperties venueHttpProperties;

    public BybitCredentialChecker( HttpClient httpClient, VenueHttpProperties venueHttpProperties )
    {
        this.httpClient = httpClient;
        this.venueHttpProperties = venueHttpProperties;
    }

    @Override
    public String venue()
    {
        return "bybit";
    }

    @Override
    public List<VenueAccessMode> supportedModes()
    {
        return List.of( VenueAccessMode.TESTNET, VenueAccessMode.PRODUCTION );
    }

    @Override
    public Result check( Credentials credentials ) throws IOException, InterruptedException
    {
        String query = "accountType=UNIFIED";
        long timestamp = System.currentTimeMillis();
        long recvWindow = 5000L;
        String signaturePayload = timestamp + credentials.apiKey() + recvWindow + query;
        String sign = HmacSigner.hmacSha256( credentials.secretKey(), signaturePayload );

        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( URI.create( credentials.baseUrl() + "/v5/account/wallet-balance?" + query ) )
                                         .timeout( Duration.ofMillis( venueHttpProperties.getRequestTimeoutMs() ) )
                                         .header( "X-BAPI-API-KEY", credentials.apiKey() )
                                         .header( "X-BAPI-TIMESTAMP", String.valueOf( timestamp ) )
                                         .header( "X-BAPI-RECV-WINDOW", String.valueOf( recvWindow ) )
                                         .header( "X-BAPI-SIGN", sign )
                                         .GET()
                                         .build();

        HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString() );
        JsonNode root = parseBody( response.body() );
        int retCode = root.path( "retCode" ).asInt( -1 );
        String retMsg = root.path( "retMsg" ).asText( response.body() );
        if( response.statusCode() == 200 && retCode == 0 )
        {
            return new Result( VenueConnectionStatus.CONNECTED, "Ключи приняты биржей.", 200 );
        }
        if( response.statusCode() == 401 || response.statusCode() == 403 || CredentialCheckSupport.looksLikeInvalidCredentials( retMsg ) || retCode != 0 )
        {
            return new Result( VenueConnectionStatus.INVALID_CREDENTIALS, retMsg, response.statusCode() );
        }
        return new Result( VenueConnectionStatus.ERROR, retMsg, response.statusCode() );
    }

    private JsonNode parseBody( String body ) throws IOException
    {
        return body == null || body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree( body );
    }
}
