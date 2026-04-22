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
    long executionLoopIntervalMs,
    Instant runtimeUpdatedAt,
    int totalPlans,
    int actionablePlans,
    Map<EnginePlanStatus, Long> statusBreakdown,
    Map<String, Long> planVenueBreakdown,
    Map<String, Long> actionableVenueBreakdown,
    long executionRuns,
    long forcedExecutionRuns,
    long scheduledExecutionRuns,
    long averageExecutionRunDurationMs,
    long lastExecutionRunDurationMs,
    Instant lastRunStartedAt,
    Instant lastRunFinishedAt,
    boolean lastRunForced,
    int lastPlansScanned,
    int lastAttemptsSubmitted,
    int lastAttemptsSkipped,
    Instant lastForcedRunStartedAt,
    Instant lastForcedRunFinishedAt,
    int lastForcedPlansScanned,
    int lastForcedAttemptsSubmitted,
    int lastForcedAttemptsSkipped,
    long lastForcedRunDurationMs,
    long averagePlanFetchDurationMs,
    long lastPlanFetchDurationMs,
    long averageAttemptRecordDurationMs,
    long lastAttemptRecordDurationMs,
    Map<String, Long> attemptStatusBreakdown,
    Map<String, Long> attemptVenueBreakdown,
    Map<String, Long> failedAttemptVenueBreakdown,
    Map<String, Long> averageSubmitDurationMsByVenue,
    Map<String, Long> lastSubmitDurationMsByVenue
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
        planVenueBreakdown = normalizedLongMap( planVenueBreakdown );
        actionableVenueBreakdown = normalizedLongMap( actionableVenueBreakdown );
        attemptStatusBreakdown = normalizedLongMap( attemptStatusBreakdown );
        attemptVenueBreakdown = normalizedLongMap( attemptVenueBreakdown );
        failedAttemptVenueBreakdown = normalizedLongMap( failedAttemptVenueBreakdown );
        averageSubmitDurationMsByVenue = normalizedLongMap( averageSubmitDurationMsByVenue );
        lastSubmitDurationMsByVenue = normalizedLongMap( lastSubmitDurationMsByVenue );
    }

    private static Map<String, Long> normalizedLongMap( Map<String, Long> raw )
    {
        if( raw == null || raw.isEmpty() )
        {
            return Collections.emptyMap();
        }

        Map<String, Long> normalized = new java.util.TreeMap<>();
        raw.forEach( ( key, value ) -> {
            if( key != null && !key.isBlank() )
            {
                normalized.put( key.trim().toLowerCase( java.util.Locale.ROOT ), value == null ? 0L : value );
            }
        } );
        return Collections.unmodifiableMap( normalized );
    }
}
