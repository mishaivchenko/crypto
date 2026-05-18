package com.crypto.funding.infrastructure.exchange.gate;

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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class GateOrderBookAdapter implements VenueOrderBookPort
{
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VenueHttpProperties venueHttpProperties;
    private final VenueProfileService venueProfileService;

    public GateOrderBookAdapter(
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
        return "gate";
    }

    @Override
    public OrderBookSnapshot fetchOrderBook( String venueSymbol, int depth ) throws IOException, InterruptedException
    {
        String url = venueProfileService.resolveProductionBaseUrl( venue() ) + "/futures/usdt/order_book?contract=" + venueSymbol + "&limit=" + depth;
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( URI.create( url ) )
                                         .timeout( Duration.ofMillis( venueHttpProperties.getRequestTimeoutMs() ) )
                                         .GET()
                                         .build();
        HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString() );
        if( response.statusCode() >= 300 )
        {
            throw new IOException( "Gate order book failed: " + response.statusCode() + " body=" + response.body() );
        }
        Instant sampledAt = Instant.now();
        JsonNode root = objectMapper.readTree( response.body() );
        List<OrderBookLevel> bids = parseLevels( root.path( "bids" ) );
        List<OrderBookLevel> asks = parseLevels( root.path( "asks" ) );
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
            // Gate format: {"p": "price", "s": size_contracts}
            String priceStr = item.path( "p" ).asText( null );
            long sizeContracts = item.path( "s" ).asLong( 0 );
            if( priceStr == null || priceStr.isBlank() || sizeContracts <= 0 )
            {
                continue;
            }
            BigDecimal price = new BigDecimal( priceStr );
            // Gate futures: contract size = 1 USD in USDT-margined, quantity expressed as USD notional contracts
            BigDecimal quantity = price.signum() > 0
                                  ? new BigDecimal( sizeContracts ).divide( price, 8, java.math.RoundingMode.HALF_UP )
                                  : BigDecimal.ZERO;
            if( price.signum() > 0 && quantity.signum() > 0 )
            {
                levels.add( new OrderBookLevel( price, quantity ) );
            }
        }
        return levels;
    }
}
