package com.crypto.funding.infrastructure.telemetry;

import com.crypto.funding.application.observability.EngineMetricsSnapshotStore;
import com.crypto.funding.config.MetadataSyncProperties;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import com.crypto.funding.domain.execution.OrderAttemptStatus;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "monitor.engine-metrics", name = "enabled", havingValue = "true")
public class EngineMetricsMeterBinder implements MeterBinder
{
    private final EngineMetricsSnapshotStore snapshotStore;
    private final MetadataSyncProperties metadataSyncProperties;

    public EngineMetricsMeterBinder( EngineMetricsSnapshotStore snapshotStore, MetadataSyncProperties metadataSyncProperties )
    {
        this.snapshotStore = snapshotStore;
        this.metadataSyncProperties = metadataSyncProperties;
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

        FunctionCounter.builder( "funding_engine_execution_runs", snapshotStore, EngineMetricsSnapshotStore::executionRuns )
                       .description( "Total engine execution runs reported by the engine runtime." )
                       .register( registry );

        FunctionCounter.builder( "funding_engine_execution_runs_by_mode", snapshotStore, EngineMetricsSnapshotStore::forcedExecutionRuns )
                       .description( "Total forced engine execution runs reported by the engine runtime." )
                       .tags( List.of( Tag.of( "mode", "forced" ) ) )
                       .register( registry );

        FunctionCounter.builder( "funding_engine_execution_runs_by_mode", snapshotStore, EngineMetricsSnapshotStore::scheduledExecutionRuns )
                       .description( "Total scheduled engine execution runs reported by the engine runtime." )
                       .tags( List.of( Tag.of( "mode", "scheduled" ) ) )
                       .register( registry );

        Gauge.builder( "funding_engine_execution_run_duration_avg_ms", snapshotStore, EngineMetricsSnapshotStore::averageExecutionRunDurationMs )
             .description( "Average engine execution run duration in milliseconds." )
             .register( registry );

        Gauge.builder( "funding_engine_execution_run_duration_last_ms", snapshotStore, EngineMetricsSnapshotStore::lastExecutionRunDurationMs )
             .description( "Last engine execution run duration in milliseconds." )
             .register( registry );

        Gauge.builder( "funding_engine_plan_fetch_duration_avg_ms", snapshotStore, EngineMetricsSnapshotStore::averagePlanFetchDurationMs )
             .description( "Average duration of fetching engine plans from monitor in milliseconds." )
             .register( registry );

        Gauge.builder( "funding_engine_plan_fetch_duration_last_ms", snapshotStore, EngineMetricsSnapshotStore::lastPlanFetchDurationMs )
             .description( "Last duration of fetching engine plans from monitor in milliseconds." )
             .register( registry );

        Gauge.builder( "funding_engine_attempt_record_duration_avg_ms", snapshotStore, EngineMetricsSnapshotStore::averageAttemptRecordDurationMs )
             .description( "Average duration of recording order attempts back to monitor in milliseconds." )
             .register( registry );

        Gauge.builder( "funding_engine_attempt_record_duration_last_ms", snapshotStore, EngineMetricsSnapshotStore::lastAttemptRecordDurationMs )
             .description( "Last duration of recording order attempts back to monitor in milliseconds." )
             .register( registry );

        for( EnginePlanStatus status : EnginePlanStatus.values() )
        {
            Gauge.builder( "funding_engine_plan_status", snapshotStore, store -> store.planCount( status ) )
                 .description( "Execution plan count by engine status from the last snapshot." )
                 .tags( List.of( Tag.of( "status", status.name() ) ) )
                 .register( registry );
        }

        for( OrderAttemptStatus status : OrderAttemptStatus.values() )
        {
            FunctionCounter.builder( "funding_engine_attempt_status", snapshotStore, store -> store.attemptStatusCount( status.name() ) )
                           .description( "Order attempts recorded by engine status from the latest snapshot." )
                           .tags( List.of( Tag.of( "status", status.name().toLowerCase( Locale.ROOT ) ) ) )
                           .register( registry );
        }

        for( String venue : venues() )
        {
            Gauge.builder( "funding_engine_plan_venue", snapshotStore, store -> store.planCountForVenue( venue ) )
                 .description( "Execution plan count by venue from the latest engine snapshot." )
                 .tags( List.of( Tag.of( "venue", venue ) ) )
                 .register( registry );

            Gauge.builder( "funding_engine_actionable_plan_venue", snapshotStore, store -> store.actionablePlanCountForVenue( venue ) )
                 .description( "Actionable execution plan count by venue from the latest engine snapshot." )
                 .tags( List.of( Tag.of( "venue", venue ) ) )
                 .register( registry );

            FunctionCounter.builder( "funding_engine_attempt_by_venue", snapshotStore, store -> store.attemptCountForVenue( venue ) )
                           .description( "Order attempts recorded by venue from the latest engine snapshot." )
                           .tags( List.of( Tag.of( "venue", venue ) ) )
                           .register( registry );

            FunctionCounter.builder( "funding_engine_failed_attempt_by_venue", snapshotStore, store -> store.failedAttemptCountForVenue( venue ) )
                           .description( "Failed order attempts recorded by venue from the latest engine snapshot." )
                           .tags( List.of( Tag.of( "venue", venue ) ) )
                           .register( registry );

            Gauge.builder( "funding_engine_submit_duration_avg_ms", snapshotStore, store -> store.averageSubmitDurationMs( venue ) )
                 .description( "Average execution port submit duration in milliseconds by venue." )
                 .tags( List.of( Tag.of( "venue", venue ) ) )
                 .register( registry );

            Gauge.builder( "funding_engine_submit_duration_last_ms", snapshotStore, store -> store.lastSubmitDurationMs( venue ) )
                 .description( "Last execution port submit duration in milliseconds by venue." )
                 .tags( List.of( Tag.of( "venue", venue ) ) )
                 .register( registry );
        }
    }

    private Set<String> venues()
    {
        Set<String> venues = new LinkedHashSet<>();
        metadataSyncProperties.getEnabledVenues()
                              .stream()
                              .map( venue -> venue.trim().toLowerCase( Locale.ROOT ) )
                              .filter( value -> !value.isBlank() )
                              .forEach( venues::add );
        return venues;
    }
}
