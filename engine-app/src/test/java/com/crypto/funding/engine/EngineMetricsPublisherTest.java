package com.crypto.funding.engine;

import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.contract.engine.EngineMetricsSnapshot;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import com.crypto.funding.contract.engine.EngineRuntimeControlResponse;
import com.crypto.funding.domain.execution.OrderAttemptStatus;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.TradeSide;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EngineMetricsPublisherTest
{
    // REQ: ENG-PUB-001
    @Test
    void assemblesMetricsSnapshotFromPlanRuntimeAndTelemetry()
    {
        Instant now = Instant.parse( "2030-01-01T00:00:00Z" );
        EnginePlanService planService = mock( EnginePlanService.class );
        EnginePlanClient client = mock( EnginePlanClient.class );
        EngineRuntimeControlService runtimeControlService = mock( EngineRuntimeControlService.class );
        EngineTelemetryService telemetryService = new EngineTelemetryService();
        telemetryService.recordExecutionRun(
            true,
            now.minusSeconds( 10 ),
            now.minusSeconds( 9 ),
            4,
            3,
            1,
            100
        );
        telemetryService.recordPlanFetch( 30 );
        telemetryService.recordAttemptRecord( 40 );
        telemetryService.recordOrderSubmission( "Bybit", OrderAttemptStatus.FAILED, 50 );

        when( planService.loadPlanSnapshot() ).thenReturn( new EnginePlanService.PlanSnapshot(
            List.of( plan( 5L, "bybit", EnginePlanStatus.ENTRY_WINDOW ) ),
            1,
            1,
            Map.of( EnginePlanStatus.ENTRY_WINDOW, 1L, EnginePlanStatus.WAITING_ENTRY, 0L ),
            Map.of( "bybit", 1L ),
            Map.of( "bybit", 1L )
        ) );
        when( runtimeControlService.snapshot() ).thenReturn( new EngineRuntimeControlResponse(
            "engine-app",
            "2.0.0",
            "testnet",
            false,
            true,
            List.of( "bybit", "gate" ),
            java.math.BigDecimal.valueOf( 25 ),
            false,
            1500L,
            100L,
            now.minusSeconds( 1 ),
            now.minusSeconds( 10 ),
            now.minusSeconds( 9 ),
            true,
            4,
            3,
            1,
            100L,
            now.minusSeconds( 10 ),
            now.minusSeconds( 9 ),
            4,
            3,
            1,
            100L
        ) );
        doNothing().when( client ).publishMetricsSnapshot( any( EngineMetricsSnapshot.class ) );

        EngineMetricsPublisher publisher = new EngineMetricsPublisher(
            planService,
            client,
            runtimeControlService,
            telemetryService,
            Clock.fixed( now, ZoneOffset.UTC )
        );

        publisher.publishSnapshot();

        ArgumentCaptor<EngineMetricsSnapshot> snapshotCaptor = ArgumentCaptor.forClass( EngineMetricsSnapshot.class );
        verify( client ).publishMetricsSnapshot( snapshotCaptor.capture() );
        EngineMetricsSnapshot snapshot = snapshotCaptor.getValue();

        assertThat( snapshot.module() ).isEqualTo( "engine-app" );
        assertThat( snapshot.capturedAt() ).isEqualTo( now );
        assertThat( snapshot.executionLoopEnabled() ).isFalse();
        assertThat( snapshot.executionLoopIntervalMs() ).isEqualTo( 1500L );
        assertThat( snapshot.totalPlans() ).isEqualTo( 1 );
        assertThat( snapshot.actionablePlans() ).isEqualTo( 1 );
        assertThat( snapshot.statusBreakdown() ).containsEntry( EnginePlanStatus.ENTRY_WINDOW, 1L );
        assertThat( snapshot.planVenueBreakdown() ).containsEntry( "bybit", 1L );
        assertThat( snapshot.executionRuns() ).isEqualTo( 1L );
        assertThat( snapshot.forcedExecutionRuns() ).isEqualTo( 1L );
        assertThat( snapshot.averagePlanFetchDurationMs() ).isEqualTo( 30L );
        assertThat( snapshot.averageAttemptRecordDurationMs() ).isEqualTo( 40L );
        assertThat( snapshot.failedAttemptVenueBreakdown() ).containsEntry( "bybit", 1L );
        assertThat( snapshot.averageSubmitDurationMsByVenue() ).containsEntry( "bybit", 50L );
    }

    // REQ: ENG-PUB-003
    @Test
    void scheduledPublisherDelegatesToSnapshotPublishing()
    {
        class CapturingPublisher extends EngineMetricsPublisher
        {
            private boolean published;

            private CapturingPublisher()
            {
                super(
                    mock( EnginePlanService.class ),
                    mock( EnginePlanClient.class ),
                    mock( EngineRuntimeControlService.class ),
                    new EngineTelemetryService(),
                    Clock.fixed( Instant.parse( "2030-01-01T00:00:00Z" ), ZoneOffset.UTC )
                );
            }

            @Override
            public void publishSnapshot()
            {
                published = true;
            }
        }

        CapturingPublisher publisher = new CapturingPublisher();

        publisher.publishOnSchedule();

        assertThat( publisher.published ).isTrue();
    }

    private static EngineExecutionPlan plan( Long armedTradeId, String venue, EnginePlanStatus status )
    {
        return new EngineExecutionPlan(
            armedTradeId,
            13L,
            venue,
            "REQ/USDT",
            TradeSide.SHORT,
            BigDecimal.valueOf( 25 ),
            ArmedTradeState.ARMED,
            Instant.parse( "2030-01-01T00:01:00Z" ),
            Instant.parse( "2029-12-31T23:59:00Z" ),
            Instant.parse( "2030-01-01T00:01:00Z" ),
            1,
            150L,
            0L,
            0L,
            0L,
            List.of(),
            status,
            Instant.parse( "2029-12-31T23:59:00Z" ),
            1_000L,
            61_000L,
            "ready"
        );
    }
}
