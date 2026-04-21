package com.crypto.funding.contract.engine;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record EngineMetricsSnapshot(
    String module,
    String version,
    Instant capturedAt,
    boolean engineUp,
    boolean executionLoopEnabled,
    int totalPlans,
    int actionablePlans,
    Map<EnginePlanStatus, Long> statusBreakdown
)
{
    public EngineMetricsSnapshot
    {
        Map<EnginePlanStatus, Long> normalizedBreakdown = new EnumMap<>( EnginePlanStatus.class );
        for( EnginePlanStatus status : EnginePlanStatus.values() )
        {
            normalizedBreakdown.put( status, 0L );
        }
        if( statusBreakdown != null )
        {
            statusBreakdown.forEach( ( status, count ) -> {
                if( status != null )
                {
                    normalizedBreakdown.put( status, count == null ? 0L : count );
                }
            } );
        }
        statusBreakdown = Collections.unmodifiableMap( normalizedBreakdown );
    }
}
