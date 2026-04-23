package com.crypto.funding.application.observability;

import com.crypto.funding.contract.engine.EngineMetricsSnapshot;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EngineMetricsSnapshotNormalizerTest
{
    private final EngineMetricsSnapshotNormalizer normalizer = new EngineMetricsSnapshotNormalizer();

    @Test
    void normalizesBreakdownsAndNullMapsIntoCanonicalSnapshot()
    {
        Map<EnginePlanStatus, Long> statusBreakdown = new EnumMap<>( EnginePlanStatus.class );
        statusBreakdown.put( EnginePlanStatus.ENTRY_WINDOW, 2L );

        Map<String, Long> planVenueBreakdown = new HashMap<>();
        planVenueBreakdown.put( " Gate ", 3L );

        Map<String, Long> attemptStatusBreakdown = new HashMap<>();
        attemptStatusBreakdown.put( " FAILED ", 5L );

        EngineMetricsSnapshot normalized = normalizer.normalize( new EngineMetricsSnapshot(
            "engine-app",
            "2.0.0",
            Instant.parse( "2030-01-01T00:00:00Z" ),
            true,
            false,
            1500L,
            Instant.parse( "2030-01-01T00:00:10Z" ),
            12,
            3,
            statusBreakdown,
            planVenueBreakdown,
            null,
            11L,
            3L,
            8L,
            128L,
            164L,
            Instant.parse( "2030-01-01T00:00:30Z" ),
            Instant.parse( "2030-01-01T00:00:31Z" ),
            true,
            12,
            9,
            3,
            Instant.parse( "2030-01-01T00:00:30Z" ),
            Instant.parse( "2030-01-01T00:00:31Z" ),
            12,
            9,
            3,
            164L,
            21L,
            34L,
            17L,
            25L,
            attemptStatusBreakdown,
            null,
            null,
            null,
            null
        ) );

        assertThat( normalized.statusBreakdown().get( EnginePlanStatus.ENTRY_WINDOW ) ).isEqualTo( 2L );
        assertThat( normalized.statusBreakdown().get( EnginePlanStatus.WAITING_ENTRY ) ).isZero();
        assertThat( normalized.planVenueBreakdown() ).containsEntry( "gate", 3L );
        assertThat( normalized.actionableVenueBreakdown() ).isEmpty();
        assertThat( normalized.attemptStatusBreakdown() ).containsEntry( "failed", 5L );
    }
}
