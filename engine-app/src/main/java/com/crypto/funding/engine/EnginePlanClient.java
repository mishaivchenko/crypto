package com.crypto.funding.engine;

import com.crypto.funding.contract.engine.EngineExecutionPlan;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class EnginePlanClient
{
    private static final ParameterizedTypeReference<List<EngineExecutionPlan>> PLAN_LIST =
        new ParameterizedTypeReference<>()
        {
        };

    private final RestClient restClient;
    private final EngineProperties properties;

    public EnginePlanClient( RestClient.Builder builder, EngineProperties properties )
    {
        this.properties = properties;
        this.restClient = builder.baseUrl( properties.getMonitorBaseUrl() ).build();
    }

    public List<EngineExecutionPlan> listPlans()
    {
        return restClient.get()
                         .uri( "/internal/v1/engine/plans" )
                         .headers( this::internalHeaders )
                         .retrieve()
                         .body( PLAN_LIST );
    }

    public EngineExecutionPlan getPlan( Long armedTradeId )
    {
        return restClient.get()
                         .uri( "/internal/v1/engine/plans/{armedTradeId}", armedTradeId )
                         .headers( this::internalHeaders )
                         .retrieve()
                         .body( EngineExecutionPlan.class );
    }

    private void internalHeaders( HttpHeaders headers )
    {
        if( properties.getInternalToken() != null && !properties.getInternalToken().isBlank() )
        {
            headers.set( "X-Internal-Token", properties.getInternalToken() );
        }
    }
}
