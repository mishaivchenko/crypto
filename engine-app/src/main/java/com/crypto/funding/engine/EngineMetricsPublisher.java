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
    private final Clock clock;

    @Autowired
    public EngineMetricsPublisher(
        EnginePlanService enginePlanService,
        EnginePlanClient enginePlanClient,
        EngineProperties engineProperties
    )
    {
        this( enginePlanService, enginePlanClient, engineProperties, Clock.systemUTC() );
    }

    EngineMetricsPublisher(
        EnginePlanService enginePlanService,
        EnginePlanClient enginePlanClient,
        EngineProperties engineProperties,
        Clock clock
    )
    {
        this.enginePlanService = enginePlanService;
        this.enginePlanClient = enginePlanClient;
        this.engineProperties = engineProperties;
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
        EngineSummaryResponse summary = enginePlanService.summary();
        enginePlanClient.publishMetricsSnapshot( new EngineMetricsSnapshot(
            summary.module(),
            summary.version(),
            Instant.now( clock ),
            true,
            engineProperties.isExecutionLoopEnabled(),
            summary.totalPlans(),
            summary.actionablePlans(),
            summary.statusBreakdown()
        ) );
    }
}
