package com.crypto.funding.application.monitor;

import com.crypto.funding.config.MonitorEngineControlProperties;
import com.crypto.funding.contract.engine.EngineExecutionRunResponse;
import com.crypto.funding.contract.engine.EngineExecutionTargetPhase;
import com.crypto.funding.contract.engine.EngineExecutionTargetRequest;
import com.crypto.funding.contract.engine.EngineRuntimeControlRequest;
import com.crypto.funding.contract.engine.EngineRuntimeControlResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class EngineControlService
{
    private final RestClient restClient;
    private final MonitorEngineControlProperties properties;

    public EngineControlService( RestClient.Builder builder, MonitorEngineControlProperties properties )
    {
        this.restClient = builder.baseUrl( properties.getBaseUrl() ).build();
        this.properties = properties;
    }

    public EngineExecutionRunResponse runOnce( boolean force )
    {
        try
        {
            return restClient.post()
                             .uri( uriBuilder -> uriBuilder.path( "/internal/engine/execution/run-once" )
                                                          .queryParam( "force", force )
                                                          .build() )
                             .headers( headers -> {
                                 if( properties.getInternalToken() != null && !properties.getInternalToken().isBlank() )
                                 {
                                     headers.set( "X-Internal-Token", properties.getInternalToken() );
                                 }
                             } )
                             .retrieve()
                             .body( EngineExecutionRunResponse.class );
        }
        catch( RestClientException ex )
        {
            throw new IllegalStateException( "Engine run-once is unavailable: " + ex.getMessage(), ex );
        }
    }

    public EngineRuntimeControlResponse runtime()
    {
        try
        {
            return restClient.get()
                             .uri( "/internal/engine/runtime" )
                             .headers( headers -> {
                                 if( properties.getInternalToken() != null && !properties.getInternalToken().isBlank() )
                                 {
                                     headers.set( "X-Internal-Token", properties.getInternalToken() );
                                 }
                             } )
                             .retrieve()
                             .body( EngineRuntimeControlResponse.class );
        }
        catch( RestClientException ex )
        {
            throw new IllegalStateException( "Engine runtime is unavailable: " + ex.getMessage(), ex );
        }
    }

    public EngineExecutionRunResponse runTarget( Long armedTradeId, EngineExecutionTargetPhase phase, boolean force )
    {
        try
        {
            return restClient.post()
                             .uri( "/internal/engine/execution/target" )
                             .headers( this::applyInternalToken )
                             .body( new EngineExecutionTargetRequest( armedTradeId, phase, force ) )
                             .retrieve()
                             .body( EngineExecutionRunResponse.class );
        }
        catch( RestClientException ex )
        {
            throw new IllegalStateException( "Engine targeted execution is unavailable: " + ex.getMessage(), ex );
        }
    }

    public EngineRuntimeControlResponse updateRuntime( EngineRuntimeControlRequest request )
    {
        try
        {
            return restClient.post()
                             .uri( "/internal/engine/runtime" )
                             .headers( headers -> {
                                 if( properties.getInternalToken() != null && !properties.getInternalToken().isBlank() )
                                 {
                                     headers.set( "X-Internal-Token", properties.getInternalToken() );
                                 }
                             } )
                             .body( request )
                             .retrieve()
                             .body( EngineRuntimeControlResponse.class );
        }
        catch( RestClientException ex )
        {
            throw new IllegalStateException( "Engine runtime update failed: " + ex.getMessage(), ex );
        }
    }

    private void applyInternalToken( org.springframework.http.HttpHeaders headers )
    {
        if( properties.getInternalToken() != null && !properties.getInternalToken().isBlank() )
        {
            headers.set( "X-Internal-Token", properties.getInternalToken() );
        }
    }
}
