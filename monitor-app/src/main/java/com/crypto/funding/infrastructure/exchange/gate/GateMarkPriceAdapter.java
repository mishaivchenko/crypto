package com.crypto.funding.infrastructure.exchange.gate;

import com.crypto.funding.application.port.VenueMarkPricePort;
import com.crypto.funding.config.VenueHttpProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class GateMarkPriceAdapter implements VenueMarkPricePort
{
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VenueHttpProperties venueHttpProperties;
    private final String contractsBaseUrl;

    public GateMarkPriceAdapter(
        HttpClient httpClient,
        VenueHttpProperties venueHttpProperties,
        @Value( "${trading.gate.contracts-base-url:${GATE_CONTRACTS_BASE_URL:https://fx-api.gateio.ws/api/v4}}" ) String contractsBaseUrl
    )
    {
        this.httpClient = httpClient;
        this.venueHttpProperties = venueHttpProperties;
        this.contractsBaseUrl = contractsBaseUrl;
    }

    @Override
    public String venue()
    {
        return "gate";
    }

    @Override
    public BigDecimal getMarkPrice( String venueSymbol ) throws IOException, InterruptedException
    {
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( URI.create( contractsBaseUrl + "/futures/usdt/contracts/" + venueSymbol ) )
                                         .timeout( Duration.ofMillis( venueHttpProperties.getRequestTimeoutMs() ) )
                                         .GET()
                                         .build();
        HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString() );
        if( response.statusCode() >= 300 )
        {
            throw new IOException( "Gate mark price failed: " + response.statusCode() + " body=" + response.body() );
        }
        JsonNode root = objectMapper.readTree( response.body() );
        String raw = root.path( "mark_price" ).asText( null );
        if( raw == null || raw.isBlank() )
        {
            return null;
        }
        return new BigDecimal( raw );
    }
}
