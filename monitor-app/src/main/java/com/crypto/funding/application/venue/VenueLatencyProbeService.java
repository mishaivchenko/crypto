package com.crypto.funding.application.venue;

import com.crypto.funding.application.execution.EngineLatencyRecordService;
import com.crypto.funding.contract.engine.EngineLatencySampleRequest;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@Service
public class VenueLatencyProbeService
{
    static final String OPERATION = "latency-probe";

    private static final Map<String, String> PROBE_PATHS = Map.of(
        "gate", "/futures/usdt/tickers?contract=BTC_USDT",
        "bybit", "/v5/market/time"
    );

    private static final Map<String, String> TESTNET_BASE_URLS = Map.of(
        "gate", "https://api-testnet.gateapi.io/api/v4",
        "bybit", "https://api-testnet.bybit.com"
    );

    private static final Map<String, String> PRODUCTION_BASE_URLS = Map.of(
        "gate", "https://fx-api.gateio.ws/api/v4",
        "bybit", "https://api.bybit.com"
    );

    private final VenueProfileService venueProfileService;
    private final EngineLatencyRecordService latencyRecordService;
    private final HttpClient httpClient;

    public VenueLatencyProbeService(
        VenueProfileService venueProfileService,
        EngineLatencyRecordService latencyRecordService
    )
    {
        this.venueProfileService = venueProfileService;
        this.latencyRecordService = latencyRecordService;
        this.httpClient = HttpClient.newBuilder()
                                    .connectTimeout( Duration.ofSeconds( 5 ) )
                                    .build();
    }

    public record ProbeResult( String venue, long durationMs, Instant sampledAt )
    {
    }

    public ProbeResult probe( String rawVenue )
    {
        String venue = rawVenue == null ? "" : rawVenue.trim().toLowerCase( Locale.ROOT );
        String probePath = PROBE_PATHS.get( venue );
        if( probePath == null )
        {
            throw new IllegalArgumentException( "Latency probe not supported for venue: " + venue );
        }

        VenueProfileService.ResolvedCredentials credentials = venueProfileService.resolveCredentials( venue );
        String baseUrl = resolveBaseUrl( venue, credentials );
        String url = baseUrl + probePath;

        Instant sampledAt = Instant.now();
        long startNanos = System.nanoTime();
        try
        {
            HttpRequest request = HttpRequest.newBuilder()
                                             .uri( URI.create( url ) )
                                             .GET()
                                             .timeout( Duration.ofSeconds( 8 ) )
                                             .build();
            httpClient.send( request, HttpResponse.BodyHandlers.discarding() );
        }
        catch( Exception e )
        {
            throw new RuntimeException( "Latency probe failed for " + venue + ": " + e.getMessage(), e );
        }
        long durationMs = ( System.nanoTime() - startNanos ) / 1_000_000L;

        latencyRecordService.record( new EngineLatencySampleRequest(
            venue,
            "_all_",
            OPERATION,
            durationMs,
            sampledAt
        ) );

        return new ProbeResult( venue, durationMs, sampledAt );
    }

    private String resolveBaseUrl( String venue, VenueProfileService.ResolvedCredentials credentials )
    {
        if( credentials.baseUrl() != null && !credentials.baseUrl().isBlank() )
        {
            return credentials.baseUrl();
        }
        return credentials.mode() != null && credentials.mode().propertyValue().equals( "testnet" )
               ? TESTNET_BASE_URLS.getOrDefault( venue, PRODUCTION_BASE_URLS.getOrDefault( venue, "" ) )
               : PRODUCTION_BASE_URLS.getOrDefault( venue, "" );
    }
}
