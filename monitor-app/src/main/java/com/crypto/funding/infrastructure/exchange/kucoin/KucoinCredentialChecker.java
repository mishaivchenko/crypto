package com.crypto.funding.infrastructure.exchange.kucoin;

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
public class KucoinCredentialChecker implements VenueCredentialCheckPort
{
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VenueHttpProperties venueHttpProperties;

    public KucoinCredentialChecker( HttpClient httpClient, VenueHttpProperties venueHttpProperties )
    {
        this.httpClient = httpClient;
        this.venueHttpProperties = venueHttpProperties;
    }

    @Override
    public String venue()
    {
        return "kucoin";
    }

    @Override
    public List<VenueAccessMode> supportedModes()
    {
        return List.of( VenueAccessMode.PRODUCTION );
    }

    @Override
    public Result check( Credentials credentials ) throws IOException, InterruptedException
    {
        String requestPath = "/api/v1/account-overview";
        String query = "currency=USDT";
        String endpointWithQuery = requestPath + "?" + query;
        String timestamp = String.valueOf( System.currentTimeMillis() );
        String signPayload = timestamp + "GET" + endpointWithQuery;
        String sign = HmacSigner.hmacSha256Base64( credentials.secretKey(), signPayload );
        String passphrase = HmacSigner.hmacSha256Base64( credentials.secretKey(), credentials.passphrase() == null ? "" : credentials.passphrase() );

        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( URI.create( credentials.baseUrl() + endpointWithQuery ) )
                                         .timeout( Duration.ofMillis( venueHttpProperties.getRequestTimeoutMs() ) )
                                         .header( "KC-API-KEY", credentials.apiKey() )
                                         .header( "KC-API-SIGN", sign )
                                         .header( "KC-API-TIMESTAMP", timestamp )
                                         .header( "KC-API-PASSPHRASE", passphrase )
                                         .header( "KC-API-KEY-VERSION", "2" )
                                         .GET()
                                         .build();

        HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString() );
        JsonNode root = parseBody( response.body() );
        String code = root.path( "code" ).asText();
        String message = root.path( "msg" ).asText( response.body() );
        if( response.statusCode() == 200 && "200000".equals( code ) )
        {
            return new Result( VenueConnectionStatus.CONNECTED, "Ключи приняты биржей.", 200 );
        }
        if( response.statusCode() == 401 || response.statusCode() == 403 || CredentialCheckSupport.looksLikeInvalidCredentials( message ) || !"200000".equals( code ) )
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
