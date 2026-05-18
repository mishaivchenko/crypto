package com.crypto.funding.infrastructure.exchange.kucoin;

import com.crypto.funding.application.port.VenueOrderBookPort;
import com.crypto.funding.application.venue.VenueProfileService;
import com.crypto.funding.config.VenueHttpProperties;
import com.crypto.funding.domain.liquidity.OrderBookLevel;
import com.crypto.funding.domain.liquidity.OrderBookSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class KucoinOrderBookAdapter implements VenueOrderBookPort
{
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VenueHttpProperties venueHttpProperties;
    private final VenueProfileService venueProfileService;

    public KucoinOrderBookAdapter(
        HttpClient httpClient,
        VenueHttpProperties venueHttpProperties,
        VenueProfileService venueProfileService
    )
    {
        this.httpClient = httpClient;
        this.venueHttpProperties = venueHttpProperties;
        this.venueProfileService = venueProfileService;
    }

    @Override
    public String venue()
    {
        return "kucoin";
    }

    @Override
    public OrderBookSnapshot fetchOrderBook( String venueSymbol, int depth ) throws IOException, InterruptedException
    {
        // KuCoin futures order book: /api/v1/level2/depth20 or /depth100
        int clampedDepth = depth <= 20 ? 20 : 100;
        String url = baseUrl() + "/api/v1/level2/depth" + clampedDepth + "?symbol="
                     + URLEncoder.encode( venueSymbol, StandardCharsets.UTF_8 );
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( URI.create( url ) )
                                         .timeout( Duration.ofMillis( venueHttpProperties.getRequestTimeoutMs() ) )
                                         .GET()
                                         .build();
        HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString() );
        if( response.statusCode() >= 300 )
        {
            throw new IOException( "KuCoin order book failed: " + response.statusCode() + " body=" + response.body() );
        }
        Instant sampledAt = Instant.now();
        JsonNode root = objectMapper.readTree( response.body() );
        if( !"200000".equals( root.path( "code" ).asText() ) )
        {
            throw new IOException( "KuCoin order book code=" + root.path( "code" ).asText() );
        }
        JsonNode data = root.path( "data" );
        // KuCoin format: {bids: [["price", "qty"], ...], asks: [...]}
        List<OrderBookLevel> bids = parseLevels( data.path( "bids" ) );
        List<OrderBookLevel> asks = parseLevels( data.path( "asks" ) );
        return new OrderBookSnapshot( venue(), venueSymbol, bids, asks, sampledAt );
    }

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
        return venueProfileService.resolveCredentials( venue() ).baseUrl();
    }
}
