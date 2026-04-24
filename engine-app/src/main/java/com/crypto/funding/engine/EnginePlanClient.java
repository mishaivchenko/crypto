package com.crypto.funding.engine;

import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.contract.engine.EngineMetricsSnapshot;
import com.crypto.funding.contract.engine.EngineOrderAttemptRecordRequest;
import com.crypto.funding.contract.engine.EngineOrderAttemptResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.function.LongSupplier;

@Component
public class EnginePlanClient
{
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

    private void internalHeaders( HttpHeaders headers )
    {
        if( properties.getInternalToken() != null && !properties.getInternalToken().isBlank() )
        {
            headers.set( "X-Internal-Token", properties.getInternalToken() );
        }
    }
}
