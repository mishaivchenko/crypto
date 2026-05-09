package com.crypto.funding.infrastructure.exchange.gate;

import com.crypto.funding.application.port.VenueMetadataPort;
import com.crypto.funding.application.venue.VenueProfileService;
import com.crypto.funding.config.VenueHttpProperties;
import com.crypto.funding.domain.venue.InstrumentMetadata;
import com.crypto.funding.domain.venue.InstrumentStatus;
import com.crypto.funding.symbol.SymbolMapper;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class GateMetadataAdapter implements VenueMetadataPort
{
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VenueHttpProperties venueHttpProperties;
    private final String contractsBaseUrl;
    private final VenueProfileService venueProfileService;

    public GateMetadataAdapter(
        HttpClient httpClient,
        VenueHttpProperties venueHttpProperties,
        @Value( "${trading.gate.contracts-base-url:${GATE_CONTRACTS_BASE_URL:https://fx-api.gateio.ws/api/v4}}" ) String contractsBaseUrl,
        VenueProfileService venueProfileService
    )
    {
        this.httpClient = httpClient;
        this.venueHttpProperties = venueHttpProperties;
        this.contractsBaseUrl = contractsBaseUrl;
        this.venueProfileService = venueProfileService;
    }

    @Override
    public String venue()
    {
        return "gate";
    }

    @Override
    public List<InstrumentMetadata> fetchPerpetualInstruments() throws IOException, InterruptedException
    {
        venueProfileService.resolveCredentials( venue() );
        HttpRequest request = HttpRequest.newBuilder()
                                         .uri( URI.create( contractsBaseUrl + "/futures/usdt/contracts" ) )
                                         .timeout( Duration.ofMillis( venueHttpProperties.getRequestTimeoutMs() ) )
                                         .GET()
                                         .build();

        HttpResponse<String> response = httpClient.send( request, HttpResponse.BodyHandlers.ofString() );
        if( response.statusCode() >= 300 )
        {
            throw new IOException( "Gate contracts list failed: " + response.statusCode() + " body=" + response.body() );
        }

        JsonNode root = objectMapper.readTree( response.body() );
        List<InstrumentMetadata> instruments = new ArrayList<>();
        Instant syncedAt = Instant.now();
        for( JsonNode item : root )
        {
            String exchangeSymbol = item.path( "name" ).asText( null );
            if( exchangeSymbol == null || !exchangeSymbol.endsWith( "_USDT" ) )
            {
                continue;
            }

            boolean inDelisting = item.path( "in_delisting" ).asBoolean( false );
            String baseAsset = exchangeSymbol.substring( 0, exchangeSymbol.indexOf( '_' ) );
            String quoteAsset = "USDT";
            BigDecimal minOrderQty = decimalOrNull( item, "order_size_min" );
            BigDecimal qtyStep = decimalOrNull( item, "order_size_round" );
            if( qtyStep == null && !item.path( "enable_decimal" ).asBoolean( false ) )
            {
                qtyStep = BigDecimal.ONE;
            }
            BigDecimal minNotionalValue = minNotionalValue( item, minOrderQty );

            instruments.add( new InstrumentMetadata(
                null,
                venue(),
                SymbolMapper.toUnified( exchangeSymbol.replace( "_", "" ) ),
                exchangeSymbol,
                baseAsset,
                quoteAsset,
                "PERPETUAL",
                inDelisting ? InstrumentStatus.INACTIVE : InstrumentStatus.ACTIVE,
                minOrderQty,
                qtyStep,
                minNotionalValue,
                null,
                syncedAt,
                null,
                null
            ) );
        }
        return instruments;
    }

    private BigDecimal decimalOrNull( JsonNode item, String field )
    {
        if( !item.hasNonNull( field ) )
        {
            return null;
        }
        String raw = item.get( field ).asText();
        return raw == null || raw.isBlank() ? null : new BigDecimal( raw );
    }

    private BigDecimal minNotionalValue( JsonNode item, BigDecimal minOrderQty )
    {
        BigDecimal multiplier = decimalOrNull( item, "quanto_multiplier" );
        BigDecimal price = firstDecimalOrNull( item, "last_price", "mark_price", "index_price" );
        if( minOrderQty == null || multiplier == null || price == null )
        {
            return null;
        }
        return minOrderQty.multiply( multiplier ).multiply( price );
    }

    private BigDecimal firstDecimalOrNull( JsonNode item, String... fields )
    {
        for( String field : fields )
        {
            BigDecimal decimal = decimalOrNull( item, field );
            if( decimal != null && decimal.signum() > 0 )
            {
                return decimal;
            }
        }
        return null;
    }
}
