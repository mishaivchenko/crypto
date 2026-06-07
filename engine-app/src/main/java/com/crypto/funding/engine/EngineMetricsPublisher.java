package com.crypto.funding.engine;

import com.crypto.funding.contract.engine.EngineMetricsSnapshot;
import com.crypto.funding.contract.engine.EngineSummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
@ConditionalOnProperty(prefix = "engine.metrics-publish", name = "enabled", havingValue = "true")
public class EngineMetricsPublisher
{
    private static final Logger log = LoggerFactory.getLogger( EngineMetricsPublisher.class );

    private final EnginePlanService enginePlanService;
    private final EnginePlanClient enginePlanClient;
    private final EngineRuntimeControlService engineRuntimeControlService;
    private final EngineTelemetryService telemetryService;
    private final Clock clock;

    @Autowired
    public EngineMetricsPublisher(
        EnginePlanService enginePlanService,
        EnginePlanClient enginePlanClient,
        EngineRuntimeControlService engineRuntimeControlService,
        EngineTelemetryService telemetryService
    )
    {
        this( enginePlanService, enginePlanClient, engineRuntimeControlService, telemetryService, Clock.systemUTC() );
    }

    EngineMetricsPublisher(
        EnginePlanService enginePlanService,
        EnginePlanClient enginePlanClient,
        EngineRuntimeControlService engineRuntimeControlService,
        EngineTelemetryService telemetryService,
        Clock clock
    )
    {
        this.enginePlanService = enginePlanService;
        this.enginePlanClient = enginePlanClient;
        this.engineRuntimeControlService = engineRuntimeControlService;
        this.telemetryService = telemetryService;
        this.clock = clock;
    }

    @Scheduled(
        initialDelayString = "${engine.metrics-publish.interval-ms:15000}",
        fixedDelayString = "${engine.metrics-publish.interval-ms:15000}"
    )
    public void publishOnSchedule()
    {
        try
        {
            publishSnapshot();
        }
        catch( Exception e )
        {
            log.warn( "Engine metrics publish failed (will retry next interval): {}", e.getMessage() );
        }
    }

    public void publishSnapshot()
    {
        EnginePlanService.PlanSnapshot planSnapshot = enginePlanService.loadPlanSnapshot();
        EngineSummaryResponse summary = new EngineSummaryResponse(
            "engine-app",
            "2.0.0",
            planSnapshot.totalPlans(),
            planSnapshot.actionablePlans(),
            Instant.now( clock ),
            planSnapshot.statusBreakdown()
        );
        EngineTelemetryService.RuntimeSnapshot telemetrySnapshot = telemetryService.snapshot();
        var runtimeSnapshot = engineRuntimeControlService.snapshot();
        enginePlanClient.publishMetricsSnapshot( new EngineMetricsSnapshot(
            summary.module(),
            summary.version(),
            Instant.now( clock ),
            true,
            runtimeSnapshot.executionLoopEnabled(),
            runtimeSnapshot.executionLoopIntervalMs(),
            runtimeSnapshot.runtimeUpdatedAt(),
            summary.totalPlans(),
            summary.actionablePlans(),
            summary.statusBreakdown(),
            planSnapshot.planVenueBreakdown(),
            planSnapshot.actionableVenueBreakdown(),
            telemetrySnapshot.executionRuns(),
            telemetrySnapshot.forcedExecutionRuns(),
            telemetrySnapshot.scheduledExecutionRuns(),
            telemetrySnapshot.averageExecutionRunDurationMs(),
            telemetrySnapshot.lastExecutionRunDurationMs(),
            telemetrySnapshot.lastRunStartedAt(),
            telemetrySnapshot.lastRunFinishedAt(),
            telemetrySnapshot.lastRunForced(),
            telemetrySnapshot.lastPlansScanned(),
            telemetrySnapshot.lastAttemptsSubmitted(),
            telemetrySnapshot.lastAttemptsSkipped(),
            telemetrySnapshot.lastForcedRunStartedAt(),
            telemetrySnapshot.lastForcedRunFinishedAt(),
            telemetrySnapshot.lastForcedPlansScanned(),
            telemetrySnapshot.lastForcedAttemptsSubmitted(),
            telemetrySnapshot.lastForcedAttemptsSkipped(),
            telemetrySnapshot.lastForcedRunDurationMs(),
            telemetrySnapshot.averagePlanFetchDurationMs(),
            telemetrySnapshot.lastPlanFetchDurationMs(),
            telemetrySnapshot.averageAttemptRecordDurationMs(),
            telemetrySnapshot.lastAttemptRecordDurationMs(),
            telemetrySnapshot.attemptStatusBreakdown(),
            telemetrySnapshot.attemptVenueBreakdown(),
            telemetrySnapshot.failedAttemptVenueBreakdown(),
            telemetrySnapshot.averageSubmitDurationMsByVenue(),
            telemetrySnapshot.lastSubmitDurationMsByVenue()
        ) );
    }
}
