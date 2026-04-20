package com.crypto.funding.infrastructure.exchange.bybit;

import com.crypto.funding.application.port.VenueMetadataPort;
import com.crypto.funding.application.venue.VenueProfileService;
import com.crypto.funding.config.VenueHttpProperties;
import com.crypto.funding.domain.venue.InstrumentMetadata;
import com.crypto.funding.domain.venue.InstrumentStatus;
import com.crypto.funding.symbol.SymbolMapper;
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
public class BybitMetadataAdapter implements VenueMetadataPort
{
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VenueHttpProperties venueHttpProperties;
    private final Environment environment;
    private final VenueProfileService venueProfileService;

    public BybitMetadataAdapter(
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
    public List<InstrumentMetadata> fetchPerpetualInstruments() throws IOException, InterruptedException
    {
        List<InstrumentMetadata> instruments = new ArrayList<>();
        String cursor = null;
        Instant syncedAt = Instant.now();

        while( true )
        {
            String url = baseUrl() + "/v5/market/instruments-info?category=linear&limit=1000";
            if( cursor != null && !cursor.isBlank() )
            {
                url += "&cursor=" + URLEncoder.encode( cursor, StandardCharsets.UTF_8 );
            }

            HttpRequest request = HttpRequest.newBuilder()
                                             .uri( URI.create( url ) )
                                             .timeout( Duration.ofMillis( venueHttpProperties.getRequestTimeoutMs() ) )
                                             .GET()
                                             .build();

            HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString() );
            if( response.statusCode() >= 300 )
            {
                throw new IOException( "Bybit instruments-info failed: " + response.statusCode() + " body=" + response.body() );
            }

            JsonNode root = objectMapper.readTree( response.body() );
            if( root.path( "retCode" ).asInt( -1 ) != 0 )
            {
                throw new IOException( "Bybit instruments-info retCode=" + root.path( "retCode" ).asInt() +
                                       " retMsg=" + root.path( "retMsg" ).asText() );
            }

            JsonNode result = root.path( "result" );
            JsonNode list = result.path( "list" );
            for( JsonNode item : list )
            {
                String status = item.path( "status" ).asText( "" );
                if( !"Trading".equalsIgnoreCase( status ) )
                {
                    continue;
                }

                String exchangeSymbol = item.path( "symbol" ).asText( null );
                String baseAsset = item.path( "baseCoin" ).asText( null );
                String quoteAsset = item.path( "quoteCoin" ).asText( null );
                if( exchangeSymbol == null || baseAsset == null || quoteAsset == null )
                {
                    continue;
                }

                JsonNode lot = item.path( "lotSizeFilter" );
                BigDecimal minQty = lot.hasNonNull( "minOrderQty" ) ? new BigDecimal( lot.get( "minOrderQty" ).asText() ) : null;
                BigDecimal qtyStep = lot.hasNonNull( "qtyStep" ) ? new BigDecimal( lot.get( "qtyStep" ).asText() ) : null;
                BigDecimal minNotional = lot.hasNonNull( "minNotionalValue" ) && !lot.get( "minNotionalValue" ).asText().isBlank()
                                         ? new BigDecimal( lot.get( "minNotionalValue" ).asText() )
                                         : null;

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
                    qtyStep,
                    minNotional,
                    null,
                    syncedAt,
                    null,
                    null
                ) );
            }

            cursor = result.path( "nextPageCursor" ).asText( "" );
            if( cursor == null || cursor.isBlank() )
            {
                break;
            }
        }

        return instruments;
    }

    private String baseUrl()
    {
        return environment.getProperty(
            "trading.bybit.metadata-base-url",
            venueProfileService.resolveCredentials( venue() ).baseUrl()
        );
    }
}
