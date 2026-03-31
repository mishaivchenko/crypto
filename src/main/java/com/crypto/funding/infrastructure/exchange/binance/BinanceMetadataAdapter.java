package com.crypto.funding.infrastructure.exchange.binance;

import com.crypto.funding.application.port.VenueMetadataPort;
import com.crypto.funding.config.VenueHttpProperties;
import com.crypto.funding.domain.venue.InstrumentMetadata;
import com.crypto.funding.domain.venue.InstrumentStatus;
import com.crypto.funding.utills.SymbolMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
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
import java.util.Locale;

@Component
public class BinanceMetadataAdapter implements VenueMetadataPort
{
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VenueHttpProperties venueHttpProperties;
    private final Environment environment;

    public BinanceMetadataAdapter(
        HttpClient httpClient,
        VenueHttpProperties venueHttpProperties,
        Environment environment
    )
    {
        this.httpClient = httpClient;
        this.venueHttpProperties = venueHttpProperties;
        this.environment = environment;
    }

    @Override
    public String venue()
    {
        return "binance";
    }

    @Override
    public List<InstrumentMetadata> fetchPerpetualInstruments() throws IOException, InterruptedException
    {
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( URI.create( baseUrl() + "/fapi/v1/exchangeInfo" ) )
                                         .timeout( Duration.ofMillis( venueHttpProperties.getRequestTimeoutMs() ) )
                                         .GET()
                                         .build();

        HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString() );
        if( response.statusCode() >= 300 )
        {
            throw new IOException( "Binance exchangeInfo failed: " + response.statusCode() + " body=" + response.body() );
        }

        JsonNode root = objectMapper.readTree( response.body() );
        JsonNode symbols = root.path( "symbols" );
        List<InstrumentMetadata> instruments = new ArrayList<>();
        Instant syncedAt = Instant.now();
        for( JsonNode symbolNode : symbols )
        {
            if( !"PERPETUAL".equalsIgnoreCase( symbolNode.path( "contractType" ).asText( "" ) ) )
            {
                continue;
            }
            if( !"TRADING".equalsIgnoreCase( symbolNode.path( "status" ).asText( "" ) ) )
            {
                continue;
            }

            String exchangeSymbol = symbolNode.path( "symbol" ).asText( null );
            String baseAsset = symbolNode.path( "baseAsset" ).asText( null );
            String quoteAsset = symbolNode.path( "quoteAsset" ).asText( null );
            if( exchangeSymbol == null || baseAsset == null || quoteAsset == null )
            {
                continue;
            }

            BigDecimal minQty = null;
            BigDecimal step = null;
            BigDecimal minNotional = null;
            JsonNode filters = symbolNode.path( "filters" );
            if( filters.isArray() )
            {
                for( JsonNode filter : filters )
                {
                    String type = filter.path( "filterType" ).asText( "" );
                    if( "LOT_SIZE".equalsIgnoreCase( type ) || "MARKET_LOT_SIZE".equalsIgnoreCase( type ) )
                    {
                        if( minQty == null && filter.hasNonNull( "minQty" ) )
                        {
                            minQty = new BigDecimal( filter.get( "minQty" ).asText() );
                        }
                        if( step == null && filter.hasNonNull( "stepSize" ) )
                        {
                            step = new BigDecimal( filter.get( "stepSize" ).asText() );
                        }
                    }
                    if( "MIN_NOTIONAL".equalsIgnoreCase( type ) )
                    {
                        String notional = filter.hasNonNull( "notional" )
                                          ? filter.get( "notional" ).asText()
                                          : filter.path( "minNotional" ).asText( null );
                        if( notional != null && !notional.isBlank() )
                        {
                            minNotional = new BigDecimal( notional );
                        }
                    }
                }
            }

            instruments.add( new InstrumentMetadata(
                null,
                venue(),
                SymbolMapper.toUnified( exchangeSymbol ),
                exchangeSymbol,
                baseAsset,
                quoteAsset,
                "PERPETUAL",
                InstrumentStatus.ACTIVE,
                minQty,
                step,
                minNotional,
                symbolNode.hasNonNull( "quantityPrecision" ) ? symbolNode.get( "quantityPrecision" ).asInt() : null,
                syncedAt,
                null,
                null
            ) );
        }
        return instruments;
    }

    private String baseUrl()
    {
        String mode = environment.getProperty( "trading.binance.mode", "testnet" ).trim().toLowerCase( Locale.ROOT );
        return environment.getProperty( "trading.binance." + mode + ".base-url", "https://testnet.binancefuture.com" );
    }
}
