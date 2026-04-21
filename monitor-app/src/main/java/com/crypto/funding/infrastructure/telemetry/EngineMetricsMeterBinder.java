package com.crypto.funding.infrastructure.telemetry;

import com.crypto.funding.application.observability.EngineMetricsSnapshotStore;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "monitor.engine-metrics", name = "enabled", havingValue = "true")
public class EngineMetricsMeterBinder implements MeterBinder
{
    private final EngineMetricsSnapshotStore snapshotStore;

    public EngineMetricsMeterBinder( EngineMetricsSnapshotStore snapshotStore )
    {
        this.snapshotStore = snapshotStore;
    }

    @Override
    public void bindTo( MeterRegistry registry )
    {
        Gauge.builder( "funding_engine_up", snapshotStore, EngineMetricsSnapshotStore::engineUp )
             .description( "Last reported engine availability." )
             .register( registry );

        Gauge.builder( "funding_engine_execution_loop_enabled", snapshotStore, EngineMetricsSnapshotStore::executionLoopEnabled )
             .description( "Last reported engine execution loop flag." )
             .register( registry );

        Gauge.builder( "funding_engine_plans", snapshotStore, EngineMetricsSnapshotStore::totalPlans )
             .description( "Total execution plans in the last engine snapshot." )
             .register( registry );

        Gauge.builder( "funding_engine_actionable_plans", snapshotStore, EngineMetricsSnapshotStore::actionablePlans )
             .description( "Actionable execution plans in the last engine snapshot." )
             .register( registry );

        Gauge.builder( "funding_engine_snapshot_age_seconds", snapshotStore, EngineMetricsSnapshotStore::snapshotAgeSeconds )
             .description( "Age of the last ingested engine snapshot in seconds." )
             .register( registry );

        Gauge.builder( "funding_engine_snapshot_captured_at_epoch_seconds", snapshotStore, EngineMetricsSnapshotStore::snapshotCapturedAtEpochSeconds )
             .description( "Unix epoch seconds when the last engine snapshot was captured." )
             .register( registry );

        for( EnginePlanStatus status : EnginePlanStatus.values() )
        {
            Gauge.builder( "funding_engine_plan_status", snapshotStore, store -> store.planCount( status ) )
                 .description( "Execution plan count by engine status from the last snapshot." )
                 .tags( List.of( Tag.of( "status", status.name() ) ) )
                 .register( registry );
        }
    }
}
