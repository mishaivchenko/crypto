package com.crypto.funding.application.observability;

import com.crypto.funding.contract.engine.EngineMetricsSnapshot;
import org.springframework.stereotype.Component;

@Component
class EngineMetricsSnapshotNormalizer
{
    EngineMetricsSnapshot normalize( EngineMetricsSnapshot snapshot )
    {
        if( snapshot == null )
        {
            return null;
        }

        return new EngineMetricsSnapshot(
            snapshot.module(),
            snapshot.version(),
            snapshot.capturedAt(),
            snapshot.engineUp(),
            snapshot.executionLoopEnabled(),
            snapshot.executionLoopIntervalMs(),
            snapshot.runtimeUpdatedAt(),
            snapshot.totalPlans(),
            snapshot.actionablePlans(),
            snapshot.statusBreakdown(),
            snapshot.planVenueBreakdown(),
            snapshot.actionableVenueBreakdown(),
            snapshot.executionRuns(),
            snapshot.forcedExecutionRuns(),
            snapshot.scheduledExecutionRuns(),
            snapshot.averageExecutionRunDurationMs(),
            snapshot.lastExecutionRunDurationMs(),
            snapshot.lastRunStartedAt(),
            snapshot.lastRunFinishedAt(),
            snapshot.lastRunForced(),
            snapshot.lastPlansScanned(),
            snapshot.lastAttemptsSubmitted(),
            snapshot.lastAttemptsSkipped(),
            snapshot.lastForcedRunStartedAt(),
            snapshot.lastForcedRunFinishedAt(),
            snapshot.lastForcedPlansScanned(),
            snapshot.lastForcedAttemptsSubmitted(),
            snapshot.lastForcedAttemptsSkipped(),
            snapshot.lastForcedRunDurationMs(),
            snapshot.averagePlanFetchDurationMs(),
            snapshot.lastPlanFetchDurationMs(),
            snapshot.averageAttemptRecordDurationMs(),
            snapshot.lastAttemptRecordDurationMs(),
            snapshot.attemptStatusBreakdown(),
            snapshot.attemptVenueBreakdown(),
            snapshot.failedAttemptVenueBreakdown(),
            snapshot.averageSubmitDurationMsByVenue(),
            snapshot.lastSubmitDurationMsByVenue()
        );
    }
}
