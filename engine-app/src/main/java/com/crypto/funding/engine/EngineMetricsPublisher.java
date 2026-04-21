package com.crypto.funding.engine;

import com.crypto.funding.contract.engine.EngineMetricsSnapshot;
import com.crypto.funding.contract.engine.EngineSummaryResponse;
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
    private final EnginePlanService enginePlanService;
    private final EnginePlanClient enginePlanClient;
    private final EngineProperties engineProperties;
    private final EngineTelemetryService telemetryService;
    private final Clock clock;

    @Autowired
    public EngineMetricsPublisher(
        EnginePlanService enginePlanService,
        EnginePlanClient enginePlanClient,
        EngineProperties engineProperties,
        EngineTelemetryService telemetryService
    )
    {
        this( enginePlanService, enginePlanClient, engineProperties, telemetryService, Clock.systemUTC() );
    }

    EngineMetricsPublisher(
        EnginePlanService enginePlanService,
        EnginePlanClient enginePlanClient,
        EngineProperties engineProperties,
        EngineTelemetryService telemetryService,
        Clock clock
    )
    {
        this.enginePlanService = enginePlanService;
        this.enginePlanClient = enginePlanClient;
        this.engineProperties = engineProperties;
        this.telemetryService = telemetryService;
        this.clock = clock;
    }

    @Scheduled(
        initialDelayString = "${engine.metrics-publish.interval-ms:15000}",
        fixedDelayString = "${engine.metrics-publish.interval-ms:15000}"
    )
    public void publishOnSchedule()
    {
        publishSnapshot();
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
        enginePlanClient.publishMetricsSnapshot( new EngineMetricsSnapshot(
            summary.module(),
            summary.version(),
            Instant.now( clock ),
            true,
            engineProperties.isExecutionLoopEnabled(),
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
