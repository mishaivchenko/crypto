package com.crypto.funding.application.observability;

import com.crypto.funding.contract.engine.EngineMetricsSnapshot;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EngineMetricsSnapshotViewTest
{
    @Test
    void returnsZeroesWhenNoSnapshotWasIngested()
    {
        EngineMetricsSnapshotStore store = new EngineMetricsSnapshotStore();
        EngineMetricsSnapshotView view = new EngineMetricsSnapshotView( store, fixedClock( "2030-01-01T00:01:00Z" ) );

        assertThat( view.engineUp() ).isZero();
        assertThat( view.totalPlans() ).isZero();
        assertThat( view.snapshotAgeSeconds() ).isZero();
        assertThat( view.planCount( EnginePlanStatus.ENTRY_WINDOW ) ).isZero();
    }

    @Test
    void exposesSnapshotValuesAndDerivedAges()
    {
        EngineMetricsSnapshotStore store = new EngineMetricsSnapshotStore();
        EngineMetricsSnapshotView view = new EngineMetricsSnapshotView( store, fixedClock( "2030-01-01T00:02:00Z" ) );
        store.update( new EngineMetricsSnapshot(
            "engine-app",
            "2.0.0",
            Instant.parse( "2030-01-01T00:00:00Z" ),
            true,
            false,
            1500L,
            Instant.parse( "2030-01-01T00:00:10Z" ),
            12,
            3,
            Map.of( EnginePlanStatus.WAITING_ENTRY, 8L, EnginePlanStatus.ENTRY_WINDOW, 2L ),
            Map.of( "bybit", 4L ),
            Map.of( "bybit", 1L ),
            11L,
            3L,
            8L,
            128L,
            164L,
            Instant.parse( "2030-01-01T00:00:30Z" ),
            Instant.parse( "2030-01-01T00:01:00Z" ),
            true,
            12,
            9,
            3,
            Instant.parse( "2030-01-01T00:00:30Z" ),
            Instant.parse( "2030-01-01T00:01:10Z" ),
            12,
            9,
            3,
            164L,
            21L,
            34L,
            17L,
            25L,
            Map.of( "failed", 9L ),
            Map.of( "bybit", 4L ),
            Map.of( "bybit", 4L ),
            Map.of( "bybit", 42L ),
            Map.of( "bybit", 51L )
        ) );

        assertThat( view.engineUp() ).isEqualTo( 1D );
        assertThat( view.executionLoopEnabled() ).isZero();
        assertThat( view.planCount( EnginePlanStatus.ENTRY_WINDOW ) ).isEqualTo( 2D );
        assertThat( view.planCountForVenue( "BYBIT" ) ).isEqualTo( 4D );
        assertThat( view.attemptStatusCount( "FAILED" ) ).isEqualTo( 9D );
        assertThat( view.snapshotAgeSeconds() ).isEqualTo( 120D );
        assertThat( view.lastRunAgeSeconds() ).isEqualTo( 60D );
        assertThat( view.lastForcedRunAgeSeconds() ).isEqualTo( 50D );
    }

    private static Clock fixedClock( String instant )
    {
        return Clock.fixed( Instant.parse( instant ), ZoneOffset.UTC );
    }
}
