package com.crypto.funding.engine;

import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.contract.engine.EngineLatencySampleRequest;
import com.crypto.funding.contract.engine.EngineVenueCredentials;
import com.crypto.funding.contract.engine.WarmupCalibrationRequest;
import com.crypto.funding.contract.engine.EngineMetricsSnapshot;
import com.crypto.funding.contract.engine.EngineOrderAttemptRecordRequest;
import com.crypto.funding.contract.engine.EngineOrderAttemptResponse;
import com.crypto.funding.contract.engine.MarkPriceResponse;
import com.crypto.funding.contract.engine.EnginePositionRecordRequest;
import com.crypto.funding.contract.engine.EnginePositionResponse;
import com.crypto.funding.contract.engine.EngineTradeOutcomeRecordRequest;
import com.crypto.funding.contract.engine.EngineTradeOutcomeResponse;
import com.crypto.funding.contract.engine.EngineTradeStateUpdateRequest;
import com.crypto.funding.contract.engine.EngineTradeStateUpdateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;

@Component
public class EnginePlanClient
{
    private static final Logger log = LoggerFactory.getLogger( EnginePlanClient.class );
    private static final ParameterizedTypeReference<List<EngineExecutionPlan>> PLAN_LIST =
        new ParameterizedTypeReference<>()
        {
        };

    private final RestClient restClient;
    private final EngineProperties properties;
    private final EngineTelemetryService telemetryService;
    private final LongSupplier nanoTimeSupplier;

    @Autowired
    public EnginePlanClient( RestClient.Builder builder, EngineProperties properties, EngineTelemetryService telemetryService )
    {
        this( builder, properties, telemetryService, System::nanoTime );
    }

    EnginePlanClient(
        RestClient.Builder builder,
        EngineProperties properties,
        EngineTelemetryService telemetryService,
        LongSupplier nanoTimeSupplier
    )
    {
        this.properties = properties;
        this.telemetryService = telemetryService;
        this.nanoTimeSupplier = nanoTimeSupplier;
        this.restClient = builder.baseUrl( properties.getMonitorBaseUrl() ).build();
    }

    public List<EngineExecutionPlan> listPlans()
    {
        return listPlans( false );
    }

    public List<EngineExecutionPlan> listPlans( boolean includeAll )
    {
        long startedAt = nanoTimeSupplier.getAsLong();
        try
        {
            return restClient.get()
                             .uri( uriBuilder -> uriBuilder.path( "/internal/v1/engine/plans" )
                                                           .queryParam( "includeAll", includeAll )
                                                           .build() )
                             .headers( this::internalHeaders )
                             .retrieve()
                             .body( PLAN_LIST );
        }
        finally
        {
            telemetryService.recordPlanFetch( ( nanoTimeSupplier.getAsLong() - startedAt ) / 1_000_000L );
        }
    }

    public EngineExecutionPlan getPlan( Long armedTradeId )
    {
        return restClient.get()
                         .uri( "/internal/v1/engine/plans/{armedTradeId}", armedTradeId )
                         .headers( this::internalHeaders )
                         .retrieve()
                         .body( EngineExecutionPlan.class );
    }

    public EngineOrderAttemptResponse recordOrderAttempt( EngineOrderAttemptRecordRequest request )
    {
        long startedAt = nanoTimeSupplier.getAsLong();
        try
        {
            return restClient.post()
                             .uri( "/internal/v1/engine/order-attempts" )
                             .contentType( MediaType.APPLICATION_JSON )
                             .headers( this::internalHeaders )
                             .body( request )
                             .retrieve()
                             .body( EngineOrderAttemptResponse.class );
        }
        finally
        {
            telemetryService.recordAttemptRecord( ( nanoTimeSupplier.getAsLong() - startedAt ) / 1_000_000L );
        }
    }

    public EnginePositionResponse recordPosition( EnginePositionRecordRequest request )
    {
        return restClient.post()
                         .uri( "/internal/v1/engine/positions" )
                         .contentType( MediaType.APPLICATION_JSON )
                         .headers( this::internalHeaders )
                         .body( request )
                         .retrieve()
                         .body( EnginePositionResponse.class );
    }

    public EngineTradeStateUpdateResponse updateTradeState( Long armedTradeId, EngineTradeStateUpdateRequest request )
    {
        return restClient.post()
                         .uri( "/internal/v1/engine/trades/{armedTradeId}/state", armedTradeId )
                         .contentType( MediaType.APPLICATION_JSON )
                         .headers( this::internalHeaders )
                         .body( request )
                         .retrieve()
                         .body( EngineTradeStateUpdateResponse.class );
    }

    public EngineTradeOutcomeResponse recordTradeOutcome( EngineTradeOutcomeRecordRequest request )
    {
        return restClient.post()
                         .uri( "/internal/v1/engine/outcomes" )
                         .contentType( MediaType.APPLICATION_JSON )
                         .headers( this::internalHeaders )
                         .body( request )
                         .retrieve()
                         .body( EngineTradeOutcomeResponse.class );
    }

    public void publishMetricsSnapshot( EngineMetricsSnapshot snapshot )
    {
        restClient.post()
                  .uri( "/internal/v1/engine/metrics-snapshot" )
                  .contentType( MediaType.APPLICATION_JSON )
                  .headers( this::internalHeaders )
                  .body( snapshot )
                  .retrieve()
                  .toBodilessEntity();
    }

    public void recordLatencySample( EngineLatencySampleRequest request )
    {
        restClient.post()
                  .uri( "/internal/v1/engine/latency-samples" )
                  .contentType( MediaType.APPLICATION_JSON )
                  .headers( this::internalHeaders )
                  .body( request )
                  .retrieve()
                  .toBodilessEntity();
    }

    public void recordWarmupCalibration( Long armedTradeId, WarmupCalibrationRequest request )
    {
        restClient.post()
                  .uri( "/internal/v1/engine/trades/{armedTradeId}/warmup-calibration", armedTradeId )
                  .contentType( MediaType.APPLICATION_JSON )
                  .headers( this::internalHeaders )
                  .body( request )
                  .retrieve()
                  .toBodilessEntity();
    }

    public Optional<EngineVenueCredentials> fetchCredentials( String venue, String mode )
    {
        try
        {
            EngineVenueCredentials credentials = restClient.get()
                                                           .uri( uriBuilder -> uriBuilder
                                                               .path( "/internal/v1/engine/credentials/{venue}" )
                                                               .queryParam( "mode", mode )
                                                               .build( venue ) )
                                                           .headers( this::internalHeaders )
                                                           .retrieve()
                                                           .body( EngineVenueCredentials.class );
            return Optional.ofNullable( credentials );
        }
        catch( org.springframework.web.client.HttpClientErrorException.NotFound e )
        {
            return Optional.empty();
        }
        catch( Exception e )
        {
            log.warn( "Failed to fetch credentials for venue={} mode={}: {}", venue, mode, e.getMessage() );
            return Optional.empty();
        }
    }

    public Optional<MarkPriceResponse> fetchMarkPrice( String venue, String symbol )
    {
        try
        {
            MarkPriceResponse response = restClient.get()
                                                   .uri( uriBuilder -> uriBuilder.path( "/internal/v1/engine/mark-price" )
                                                                                 .queryParam( "venue", venue )
                                                                                 .queryParam( "symbol", symbol )
                                                                                 .build() )
                                                   .headers( this::internalHeaders )
                                                   .retrieve()
                                                   .body( MarkPriceResponse.class );
            return Optional.ofNullable( response );
        }
        catch( Exception ignored )
        {
            return Optional.empty();
        }
    }

    private void internalHeaders( HttpHeaders headers )
    {
        if( properties.getInternalToken() != null && !properties.getInternalToken().isBlank() )
        {
            headers.set( "X-Internal-Token", properties.getInternalToken() );
        }
    }
}
