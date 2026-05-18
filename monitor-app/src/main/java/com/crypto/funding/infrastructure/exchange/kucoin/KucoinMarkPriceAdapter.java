package com.crypto.funding.infrastructure.exchange.kucoin;

import com.crypto.funding.application.port.VenueMarkPricePort;
import com.crypto.funding.application.venue.VenueProfileService;
import com.crypto.funding.config.VenueHttpProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class KucoinMarkPriceAdapter implements VenueMarkPricePort
{
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VenueHttpProperties venueHttpProperties;
    private final Environment environment;
    private final VenueProfileService venueProfileService;

    public KucoinMarkPriceAdapter(
        HttpClient httpClient,
        VenueHttpProperties venueHttpProperties,
        Environment environment,
        VenueProfileService venueProfileService
    )
    {
        this.httpClient = httpClient;
        this.venueHttpProperties = venueHttpProperties;
        this.environment = environment;
        this.venueProfileService = venueProfileService;
    }

    @Override
    public String venue()
    {
        return "kucoin";
    }

    @Override
    public BigDecimal getMarkPrice( String venueSymbol ) throws IOException, InterruptedException
    {
        String url = baseUrl() + "/api/v1/mark-price/" + URLEncoder.encode( venueSymbol, StandardCharsets.UTF_8 ) + "/current";
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( URI.create( url ) )
                                         .timeout( Duration.ofMillis( venueHttpProperties.getRequestTimeoutMs() ) )
                                         .GET()
                                         .build();
        HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString() );
        if( response.statusCode() >= 300 )
        {
            throw new IOException( "Kucoin mark price failed: " + response.statusCode() + " body=" + response.body() );
        }
        JsonNode root = objectMapper.readTree( response.body() );
        if( root.hasNonNull( "code" ) && !"200000".equals( root.path( "code" ).asText() ) )
        {
            throw new IOException( "Kucoin mark price code=" + root.path( "code" ).asText() + " msg=" + root.path( "msg" ).asText() );
        }
        String raw = root.path( "data" ).path( "value" ).asText( null );
        if( raw == null || raw.isBlank() )
        {
            return null;
        }
        return new BigDecimal( raw );
    }

    private String baseUrl()
    {
        return environment.getProperty(
            "trading.kucoin.metadata-base-url",
            venueProfileService.resolveCredentials( venue() ).baseUrl()
        );
    }
}
