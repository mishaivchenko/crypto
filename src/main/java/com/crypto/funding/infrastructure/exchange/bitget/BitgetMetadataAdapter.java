package com.crypto.funding.infrastructure.exchange.bitget;

import com.crypto.funding.application.port.VenueMetadataPort;
import com.crypto.funding.application.venue.VenueProfileService;
import com.crypto.funding.config.VenueHttpProperties;
import com.crypto.funding.domain.venue.InstrumentMetadata;
import com.crypto.funding.domain.venue.InstrumentStatus;
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

@Component
public class BitgetMetadataAdapter implements VenueMetadataPort
{
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VenueHttpProperties venueHttpProperties;
    private final Environment environment;
    private final VenueProfileService venueProfileService;

    public BitgetMetadataAdapter(
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
        return "bitget";
    }

    @Override
    public List<InstrumentMetadata> fetchPerpetualInstruments() throws IOException, InterruptedException
    {
        String baseUrl = environment.getProperty(
            "trading.bitget.metadata-base-url",
            venueProfileService.resolveCredentials( venue() ).baseUrl()
        );
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( URI.create( baseUrl + "/api/v2/mix/market/contracts?productType=USDT-FUTURES" ) )
                                         .timeout( Duration.ofMillis( venueHttpProperties.getRequestTimeoutMs() ) )
                                         .GET()
                                         .build();
        HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString() );
        if( response.statusCode() >= 300 )
        {
            throw new IOException( "Bitget contracts failed: " + response.statusCode() + " body=" + response.body() );
        }

        JsonNode root = objectMapper.readTree( response.body() );
        if( root.hasNonNull( "code" ) && !"00000".equals( root.path( "code" ).asText() ) )
        {
            throw new IOException( "Bitget contracts code=" + root.path( "code" ).asText() + " msg=" + root.path( "msg" ).asText() );
        }

        JsonNode data = root.path( "data" );
        List<InstrumentMetadata> instruments = new ArrayList<>();
        Instant syncedAt = Instant.now();
        for( JsonNode item : data )
        {
            String baseAsset = item.path( "baseCoin" ).asText( null );
            String quoteAsset = item.path( "quoteCoin" ).asText( null );
            String venueSymbol = item.path( "symbol" ).asText( null );
            if( baseAsset == null || quoteAsset == null || venueSymbol == null )
            {
                continue;
            }
            if( !"USDT".equalsIgnoreCase( quoteAsset ) )
            {
                continue;
            }
            String status = item.path( "symbolStatus" ).asText( "normal" );
            instruments.add( new InstrumentMetadata(
                null,
                venue(),
                canonicalSymbol( baseAsset, quoteAsset ),
                venueSymbol,
                baseAsset,
                quoteAsset,
                "PERPETUAL",
                "normal".equalsIgnoreCase( status ) ? InstrumentStatus.ACTIVE : InstrumentStatus.INACTIVE,
                decimalOrNull( item, "minTradeNum" ),
                decimalOrNull( item, "sizeMultiplier" ),
                decimalOrNull( item, "minTradeUSDT" ),
                null,
                syncedAt,
                null,
                null
            ) );
        }
        return instruments;
    }

    private static String canonicalSymbol( String baseAsset, String quoteAsset )
    {
        return baseAsset.toUpperCase() + "/" + quoteAsset.toUpperCase();
    }

    private static BigDecimal decimalOrNull( JsonNode item, String field )
    {
        if( !item.hasNonNull( field ) )
        {
            return null;
        }
        String raw = item.get( field ).asText();
        return raw == null || raw.isBlank() ? null : new BigDecimal( raw );
    }
}
