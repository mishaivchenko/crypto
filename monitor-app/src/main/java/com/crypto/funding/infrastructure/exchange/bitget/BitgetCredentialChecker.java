package com.crypto.funding.infrastructure.exchange.bitget;

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
public class BitgetCredentialChecker implements VenueCredentialCheckPort
{
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VenueHttpProperties venueHttpProperties;

    public BitgetCredentialChecker( HttpClient httpClient, VenueHttpProperties venueHttpProperties )
    {
        this.httpClient = httpClient;
        this.venueHttpProperties = venueHttpProperties;
    }

    @Override
    public String venue()
    {
        return "bitget";
    }

    @Override
    public List<VenueAccessMode> supportedModes()
    {
        return List.of( VenueAccessMode.TESTNET, VenueAccessMode.PRODUCTION );
    }

    @Override
    public Result check( Credentials credentials ) throws IOException, InterruptedException
    {
        String requestPath = "/api/v2/mix/account/accounts";
        String query = "productType=USDT-FUTURES";
        String timestamp = String.valueOf( System.currentTimeMillis() );
        String signPayload = timestamp + "GET" + requestPath + "?" + query;
        String sign = HmacSigner.hmacSha256Base64( credentials.secretKey(), signPayload );

        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( URI.create( credentials.baseUrl() + requestPath + "?" + query ) )
                                         .timeout( Duration.ofMillis( venueHttpProperties.getRequestTimeoutMs() ) )
                                         .header( "ACCESS-KEY", credentials.apiKey() )
                                         .header( "ACCESS-SIGN", sign )
                                         .header( "ACCESS-TIMESTAMP", timestamp )
                                         .header( "ACCESS-PASSPHRASE", credentials.passphrase() == null ? "" : credentials.passphrase() )
                                         .header( "locale", "en-US" )
                                         .GET()
                                         .build();

        HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString() );
        JsonNode root = parseBody( response.body() );
        String code = root.path( "code" ).asText();
        String message = root.path( "msg" ).asText( response.body() );
        if( response.statusCode() == 200 && "00000".equals( code ) )
        {
            return new Result( VenueConnectionStatus.CONNECTED, "Ключи приняты биржей.", 200 );
        }
        if( response.statusCode() == 401 || response.statusCode() == 403 || CredentialCheckSupport.looksLikeInvalidCredentials( message ) || !"00000".equals( code ) )
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
