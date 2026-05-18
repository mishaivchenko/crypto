package com.crypto.funding.infrastructure.exchange.bybit;

import com.crypto.funding.application.port.VenueOrderBookPort;
import com.crypto.funding.application.venue.VenueProfileService;
import com.crypto.funding.config.VenueHttpProperties;
import com.crypto.funding.domain.liquidity.OrderBookLevel;
import com.crypto.funding.domain.liquidity.OrderBookSnapshot;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class BybitOrderBookAdapter implements VenueOrderBookPort
{
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VenueHttpProperties venueHttpProperties;
    private final Environment environment;
    private final VenueProfileService venueProfileService;

    public BybitOrderBookAdapter(
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
        return "bybit";
    }

    @Override
    public OrderBookSnapshot fetchOrderBook( String venueSymbol, int depth ) throws IOException, InterruptedException
    {
        // Bybit supports depth 1,50,200,500 for linear; clamp to 200 if depth > 200
        int clampedDepth = Math.min( depth, 200 );
        String url = baseUrl() + "/v5/market/orderbook?category=linear&symbol="
                     + URLEncoder.encode( venueSymbol, StandardCharsets.UTF_8 )
                     + "&limit=" + clampedDepth;
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( URI.create( url ) )
                                         .timeout( Duration.ofMillis( venueHttpProperties.getRequestTimeoutMs() ) )
                                         .GET()
                                         .build();
        HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString() );
        if( response.statusCode() >= 300 )
        {
            throw new IOException( "Bybit order book failed: " + response.statusCode() + " body=" + response.body() );
        }
        Instant sampledAt = Instant.now();
        JsonNode root = objectMapper.readTree( response.body() );
        if( root.path( "retCode" ).asInt( -1 ) != 0 )
        {
            throw new IOException( "Bybit orderbook retCode=" + root.path( "retCode" ).asInt() );
        }
        JsonNode result = root.path( "result" );
        List<OrderBookLevel> bids = parseLevels( result.path( "b" ) );
        List<OrderBookLevel> asks = parseLevels( result.path( "a" ) );
        return new OrderBookSnapshot( venue(), venueSymbol, bids, asks, sampledAt );
    }

    // Bybit format: [["price", "qty"], ...]
    private static List<OrderBookLevel> parseLevels( JsonNode array )
    {
        List<OrderBookLevel> levels = new ArrayList<>();
        if( !array.isArray() )
        {
            return levels;
        }
        for( JsonNode item : array )
        {
            if( !item.isArray() || item.size() < 2 )
            {
                continue;
            }
            String priceStr = item.get( 0 ).asText( null );
            String qtyStr = item.get( 1 ).asText( null );
            if( priceStr == null || qtyStr == null )
            {
                continue;
            }
            BigDecimal price = new BigDecimal( priceStr );
            BigDecimal qty = new BigDecimal( qtyStr );
            if( price.signum() > 0 && qty.signum() > 0 )
            {
                levels.add( new OrderBookLevel( price, qty ) );
            }
        }
        return levels;
    }

    private String baseUrl()
    {
        return environment.getProperty(
            "trading.bybit.metadata-base-url",
            venueProfileService.resolveCredentials( venue() ).baseUrl()
        );
    }
}
