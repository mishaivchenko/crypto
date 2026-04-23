package com.crypto.funding.application.observability;

import com.crypto.funding.contract.engine.EngineMetricsSnapshot;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
@ConditionalOnProperty(prefix = "monitor.engine-metrics", name = "enabled", havingValue = "true")
public class EngineMetricsSnapshotView
{
    private final EngineMetricsSnapshotStore snapshotStore;
    private final Clock clock;

    @Autowired
    public EngineMetricsSnapshotView( EngineMetricsSnapshotStore snapshotStore )
    {
        this( snapshotStore, Clock.systemUTC() );
    }

    EngineMetricsSnapshotView( EngineMetricsSnapshotStore snapshotStore, Clock clock )
    {
        this.snapshotStore = snapshotStore;
        this.clock = clock;
    }

    public double engineUp()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        return snapshot != null && snapshot.engineUp() ? 1D : 0D;
    }

    public double executionLoopEnabled()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        return snapshot != null && snapshot.executionLoopEnabled() ? 1D : 0D;
    }

    public double executionLoopIntervalMs()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        return snapshot == null ? 0D : snapshot.executionLoopIntervalMs();
    }

    public double runtimeUpdatedAtEpochSeconds()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        if( snapshot == null || snapshot.runtimeUpdatedAt() == null )
        {
            return 0D;
        }
        return snapshot.runtimeUpdatedAt().getEpochSecond();
    }

    public double totalPlans()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        return snapshot == null ? 0D : snapshot.totalPlans();
    }

    public double actionablePlans()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        return snapshot == null ? 0D : snapshot.actionablePlans();
    }

    public double planCount( EnginePlanStatus status )
    {
        EngineMetricsSnapshot snapshot = snapshot();
        if( snapshot == null )
        {
            return 0D;
        }
        return snapshot.statusBreakdown().getOrDefault( status, 0L );
    }

    public double planCountForVenue( String venue )
    {
        EngineMetricsSnapshot snapshot = snapshot();
        if( snapshot == null )
        {
            return 0D;
        }
        return snapshot.planVenueBreakdown().getOrDefault( normalize( venue ), 0L );
    }

    public double actionablePlanCountForVenue( String venue )
    {
        EngineMetricsSnapshot snapshot = snapshot();
        if( snapshot == null )
        {
            return 0D;
        }
        return snapshot.actionableVenueBreakdown().getOrDefault( normalize( venue ), 0L );
    }

    public double executionRuns()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        return snapshot == null ? 0D : snapshot.executionRuns();
    }

    public double forcedExecutionRuns()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        return snapshot == null ? 0D : snapshot.forcedExecutionRuns();
    }

    public double scheduledExecutionRuns()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        return snapshot == null ? 0D : snapshot.scheduledExecutionRuns();
    }

    public double averageExecutionRunDurationMs()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        return snapshot == null ? 0D : snapshot.averageExecutionRunDurationMs();
    }

    public double lastExecutionRunDurationMs()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        return snapshot == null ? 0D : snapshot.lastExecutionRunDurationMs();
    }

    public double lastRunStartedAtEpochSeconds()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        if( snapshot == null || snapshot.lastRunStartedAt() == null )
        {
            return 0D;
        }
        return snapshot.lastRunStartedAt().getEpochSecond();
    }

    public double lastRunFinishedAtEpochSeconds()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        if( snapshot == null || snapshot.lastRunFinishedAt() == null )
        {
            return 0D;
        }
        return snapshot.lastRunFinishedAt().getEpochSecond();
    }

    public double lastRunAgeSeconds()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        if( snapshot == null || snapshot.lastRunFinishedAt() == null )
        {
            return 0D;
        }
        return Math.max( 0D, Duration.between( snapshot.lastRunFinishedAt(), Instant.now( clock ) ).toMillis() / 1000D );
    }

    public double lastRunForced()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        return snapshot != null && snapshot.lastRunForced() ? 1D : 0D;
    }

    public double lastPlansScanned()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        return snapshot == null ? 0D : snapshot.lastPlansScanned();
    }

    public double lastAttemptsSubmitted()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        return snapshot == null ? 0D : snapshot.lastAttemptsSubmitted();
    }

    public double lastAttemptsSkipped()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        return snapshot == null ? 0D : snapshot.lastAttemptsSkipped();
    }

    public double lastForcedRunStartedAtEpochSeconds()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        if( snapshot == null || snapshot.lastForcedRunStartedAt() == null )
        {
            return 0D;
        }
        return snapshot.lastForcedRunStartedAt().getEpochSecond();
    }

    public double lastForcedRunFinishedAtEpochSeconds()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        if( snapshot == null || snapshot.lastForcedRunFinishedAt() == null )
        {
            return 0D;
        }
        return snapshot.lastForcedRunFinishedAt().getEpochSecond();
    }

    public double lastForcedRunAgeSeconds()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        if( snapshot == null || snapshot.lastForcedRunFinishedAt() == null )
        {
            return 0D;
        }
        return Math.max( 0D, Duration.between( snapshot.lastForcedRunFinishedAt(), Instant.now( clock ) ).toMillis() / 1000D );
    }

    public double lastForcedPlansScanned()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        return snapshot == null ? 0D : snapshot.lastForcedPlansScanned();
    }

    public double lastForcedAttemptsSubmitted()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        return snapshot == null ? 0D : snapshot.lastForcedAttemptsSubmitted();
    }

    public double lastForcedAttemptsSkipped()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        return snapshot == null ? 0D : snapshot.lastForcedAttemptsSkipped();
    }

    public double lastForcedRunDurationMs()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        return snapshot == null ? 0D : snapshot.lastForcedRunDurationMs();
    }

    public double averagePlanFetchDurationMs()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        return snapshot == null ? 0D : snapshot.averagePlanFetchDurationMs();
    }

    public double lastPlanFetchDurationMs()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        return snapshot == null ? 0D : snapshot.lastPlanFetchDurationMs();
    }

    public double averageAttemptRecordDurationMs()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        return snapshot == null ? 0D : snapshot.averageAttemptRecordDurationMs();
    }

    public double lastAttemptRecordDurationMs()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        return snapshot == null ? 0D : snapshot.lastAttemptRecordDurationMs();
    }

    public double attemptStatusCount( String status )
    {
        EngineMetricsSnapshot snapshot = snapshot();
        if( snapshot == null )
        {
            return 0D;
        }
        return snapshot.attemptStatusBreakdown().getOrDefault( normalize( status ), 0L );
    }

    public double attemptCountForVenue( String venue )
    {
        EngineMetricsSnapshot snapshot = snapshot();
        if( snapshot == null )
        {
            return 0D;
        }
        return snapshot.attemptVenueBreakdown().getOrDefault( normalize( venue ), 0L );
    }

    public double failedAttemptCountForVenue( String venue )
    {
        EngineMetricsSnapshot snapshot = snapshot();
        if( snapshot == null )
        {
            return 0D;
        }
        return snapshot.failedAttemptVenueBreakdown().getOrDefault( normalize( venue ), 0L );
    }

    public double averageSubmitDurationMs( String venue )
    {
        EngineMetricsSnapshot snapshot = snapshot();
        if( snapshot == null )
        {
            return 0D;
        }
        return snapshot.averageSubmitDurationMsByVenue().getOrDefault( normalize( venue ), 0L );
    }

    public double lastSubmitDurationMs( String venue )
    {
        EngineMetricsSnapshot snapshot = snapshot();
        if( snapshot == null )
        {
            return 0D;
        }
        return snapshot.lastSubmitDurationMsByVenue().getOrDefault( normalize( venue ), 0L );
    }

    public double snapshotAgeSeconds()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        if( snapshot == null || snapshot.capturedAt() == null )
        {
            return 0D;
        }
        return Math.max( 0D, Duration.between( snapshot.capturedAt(), Instant.now( clock ) ).toMillis() / 1000D );
    }

    public double snapshotCapturedAtEpochSeconds()
    {
        EngineMetricsSnapshot snapshot = snapshot();
        if( snapshot == null || snapshot.capturedAt() == null )
        {
            return 0D;
        }
        return snapshot.capturedAt().getEpochSecond();
    }

    private EngineMetricsSnapshot snapshot()
    {
        return snapshotStore.current();
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
