package com.crypto.funding.application.observability;

import com.crypto.funding.contract.engine.EngineMetricsSnapshot;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

@Service
@ConditionalOnProperty(prefix = "monitor.engine-metrics", name = "enabled", havingValue = "true")
public class EngineMetricsSnapshotStore
{
    private final AtomicReference<EngineMetricsSnapshot> snapshotRef = new AtomicReference<>();
    private final Clock clock;

    public EngineMetricsSnapshotStore()
    {
        this( Clock.systemUTC() );
    }

    EngineMetricsSnapshotStore( Clock clock )
    {
        this.clock = clock;
    }

    public void update( EngineMetricsSnapshot snapshot )
    {
        snapshotRef.set( snapshot );
    }

    public double engineUp()
    {
        EngineMetricsSnapshot snapshot = snapshotRef.get();
        return snapshot != null && snapshot.engineUp() ? 1D : 0D;
    }

    public double executionLoopEnabled()
    {
        EngineMetricsSnapshot snapshot = snapshotRef.get();
        return snapshot != null && snapshot.executionLoopEnabled() ? 1D : 0D;
    }

    public double totalPlans()
    {
        EngineMetricsSnapshot snapshot = snapshotRef.get();
        return snapshot == null ? 0D : snapshot.totalPlans();
    }

    public double actionablePlans()
    {
        EngineMetricsSnapshot snapshot = snapshotRef.get();
        return snapshot == null ? 0D : snapshot.actionablePlans();
    }

    public double planCount( EnginePlanStatus status )
    {
        EngineMetricsSnapshot snapshot = snapshotRef.get();
        if( snapshot == null )
        {
            return 0D;
        }
        return snapshot.statusBreakdown().getOrDefault( status, 0L );
    }

    public double planCountForVenue( String venue )
    {
        EngineMetricsSnapshot snapshot = snapshotRef.get();
        if( snapshot == null )
        {
            return 0D;
        }
        return snapshot.planVenueBreakdown().getOrDefault( normalize( venue ), 0L );
    }

    public double actionablePlanCountForVenue( String venue )
    {
        EngineMetricsSnapshot snapshot = snapshotRef.get();
        if( snapshot == null )
        {
            return 0D;
        }
        return snapshot.actionableVenueBreakdown().getOrDefault( normalize( venue ), 0L );
    }

    public double executionRuns()
    {
        EngineMetricsSnapshot snapshot = snapshotRef.get();
        return snapshot == null ? 0D : snapshot.executionRuns();
    }

    public double forcedExecutionRuns()
    {
        EngineMetricsSnapshot snapshot = snapshotRef.get();
        return snapshot == null ? 0D : snapshot.forcedExecutionRuns();
    }

    public double scheduledExecutionRuns()
    {
        EngineMetricsSnapshot snapshot = snapshotRef.get();
        return snapshot == null ? 0D : snapshot.scheduledExecutionRuns();
    }

    public double averageExecutionRunDurationMs()
    {
        EngineMetricsSnapshot snapshot = snapshotRef.get();
        return snapshot == null ? 0D : snapshot.averageExecutionRunDurationMs();
    }

    public double lastExecutionRunDurationMs()
    {
        EngineMetricsSnapshot snapshot = snapshotRef.get();
        return snapshot == null ? 0D : snapshot.lastExecutionRunDurationMs();
    }

    public double averagePlanFetchDurationMs()
    {
        EngineMetricsSnapshot snapshot = snapshotRef.get();
        return snapshot == null ? 0D : snapshot.averagePlanFetchDurationMs();
    }

    public double lastPlanFetchDurationMs()
    {
        EngineMetricsSnapshot snapshot = snapshotRef.get();
        return snapshot == null ? 0D : snapshot.lastPlanFetchDurationMs();
    }

    public double averageAttemptRecordDurationMs()
    {
        EngineMetricsSnapshot snapshot = snapshotRef.get();
        return snapshot == null ? 0D : snapshot.averageAttemptRecordDurationMs();
    }

    public double lastAttemptRecordDurationMs()
    {
        EngineMetricsSnapshot snapshot = snapshotRef.get();
        return snapshot == null ? 0D : snapshot.lastAttemptRecordDurationMs();
    }

    public double attemptStatusCount( String status )
    {
        EngineMetricsSnapshot snapshot = snapshotRef.get();
        if( snapshot == null )
        {
            return 0D;
        }
        return snapshot.attemptStatusBreakdown().getOrDefault( normalize( status ), 0L );
    }

    public double attemptCountForVenue( String venue )
    {
        EngineMetricsSnapshot snapshot = snapshotRef.get();
        if( snapshot == null )
        {
            return 0D;
        }
        return snapshot.attemptVenueBreakdown().getOrDefault( normalize( venue ), 0L );
    }

    public double failedAttemptCountForVenue( String venue )
    {
        EngineMetricsSnapshot snapshot = snapshotRef.get();
        if( snapshot == null )
        {
            return 0D;
        }
        return snapshot.failedAttemptVenueBreakdown().getOrDefault( normalize( venue ), 0L );
    }

    public double averageSubmitDurationMs( String venue )
    {
        EngineMetricsSnapshot snapshot = snapshotRef.get();
        if( snapshot == null )
        {
            return 0D;
        }
        return snapshot.averageSubmitDurationMsByVenue().getOrDefault( normalize( venue ), 0L );
    }

    public double lastSubmitDurationMs( String venue )
    {
        EngineMetricsSnapshot snapshot = snapshotRef.get();
        if( snapshot == null )
        {
            return 0D;
        }
        return snapshot.lastSubmitDurationMsByVenue().getOrDefault( normalize( venue ), 0L );
    }

    public double snapshotAgeSeconds()
    {
        EngineMetricsSnapshot snapshot = snapshotRef.get();
        if( snapshot == null || snapshot.capturedAt() == null )
        {
            return 0D;
        }
        return Math.max( 0D, Duration.between( snapshot.capturedAt(), Instant.now( clock ) ).toMillis() / 1000D );
    }

    public double snapshotCapturedAtEpochSeconds()
    {
        EngineMetricsSnapshot snapshot = snapshotRef.get();
        if( snapshot == null || snapshot.capturedAt() == null )
        {
            return 0D;
        }
        return snapshot.capturedAt().getEpochSecond();
    }

    private static String normalize( String value )
    {
        if( value == null || value.isBlank() )
        {
            return "";
        }
        return value.trim().toLowerCase( Locale.ROOT );
    }
}
