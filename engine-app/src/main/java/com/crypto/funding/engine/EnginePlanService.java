package com.crypto.funding.engine;

import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import com.crypto.funding.contract.engine.EngineSummaryResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
public class EnginePlanService
{
    record PlanSnapshot(
        List<EngineExecutionPlan> plans,
        int totalPlans,
        int actionablePlans,
        Map<EnginePlanStatus, Long> statusBreakdown,
        Map<String, Long> planVenueBreakdown,
        Map<String, Long> actionableVenueBreakdown
    )
    {
    }

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
        PlanSnapshot snapshot = loadPlanSnapshot();
        return new EngineSummaryResponse(
            "engine-app",
            "2.0.0",
            snapshot.totalPlans(),
            snapshot.actionablePlans(),
            Instant.now(),
            snapshot.statusBreakdown()
        );
    }

    PlanSnapshot loadPlanSnapshot()
    {
        List<EngineExecutionPlan> plans = listPlans();
        Map<EnginePlanStatus, Long> breakdown = new EnumMap<>( EnginePlanStatus.class );
        Map<String, Long> planVenueBreakdown = new TreeMap<>();
        Map<String, Long> actionableVenueBreakdown = new TreeMap<>();
        for( EnginePlanStatus status : EnginePlanStatus.values() )
        {
            breakdown.put( status, 0L );
        }
        for( EngineExecutionPlan plan : plans )
        {
            breakdown.computeIfPresent( plan.status(), ( ignored, count ) -> count + 1L );
            String venue = normalizeVenue( plan.venue() );
            planVenueBreakdown.merge( venue, 1L, Long::sum );
            if( plan.status() == EnginePlanStatus.ENTRY_WINDOW || plan.status() == EnginePlanStatus.EXIT_WINDOW )
            {
                actionableVenueBreakdown.merge( venue, 1L, Long::sum );
            }
        }
        long actionable = plans.stream()
                               .filter( plan -> plan.status() == EnginePlanStatus.ENTRY_WINDOW || plan.status() == EnginePlanStatus.EXIT_WINDOW )
                               .count();

        return new PlanSnapshot(
            plans,
            plans.size(),
            (int) actionable,
            Map.copyOf( breakdown ),
            Map.copyOf( planVenueBreakdown ),
            Map.copyOf( actionableVenueBreakdown )
        );
    }

    private static String normalizeVenue( String venue )
    {
        if( venue == null || venue.isBlank() )
        {
            return "unknown";
        }
        return venue.trim().toLowerCase( Locale.ROOT );
    }
}
