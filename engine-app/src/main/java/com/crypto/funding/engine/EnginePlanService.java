package com.crypto.funding.engine;

import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import com.crypto.funding.contract.engine.EngineSummaryResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class EnginePlanService
{
    private final EnginePlanClient client;

    public EnginePlanService( EnginePlanClient client )
    {
        this.client = client;
    }

    public List<EngineExecutionPlan> listPlans()
    {
        return client.listPlans();
    }

    public EngineExecutionPlan getPlan( Long armedTradeId )
    {
        return client.getPlan( armedTradeId );
    }

    public EngineSummaryResponse summary()
    {
        List<EngineExecutionPlan> plans = listPlans();
        Map<EnginePlanStatus, Long> breakdown = new EnumMap<>( EnginePlanStatus.class );
        for( EnginePlanStatus status : EnginePlanStatus.values() )
        {
            breakdown.put( status, 0L );
        }
        for( EngineExecutionPlan plan : plans )
        {
            breakdown.computeIfPresent( plan.status(), ( ignored, count ) -> count + 1L );
        }
        long actionable = plans.stream()
                               .filter( plan -> plan.status() == EnginePlanStatus.ENTRY_WINDOW || plan.status() == EnginePlanStatus.EXIT_WINDOW )
                               .count();
        return new EngineSummaryResponse(
            "engine-app",
            "2.0.0",
            plans.size(),
            (int) actionable,
            Instant.now(),
            breakdown
        );
    }
}
