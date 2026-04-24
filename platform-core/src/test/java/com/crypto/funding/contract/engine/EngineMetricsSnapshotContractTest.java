package com.crypto.funding.contract.engine;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EngineMetricsSnapshotContractTest
{
    // REQ: ENG-CORE-003
    @Test
    void zeroFillsStatusBreakdownForAllKnownStatuses()
    {
        EngineMetricsSnapshot snapshot = new EngineMetricsSnapshot(
            "engine-app",
            "2.0.0",
            Instant.parse( "2030-01-01T00:00:00Z" ),
            true,
            false,
            1000L,
            Instant.parse( "2030-01-01T00:00:01Z" ),
            1,
            0,
            Map.of( EnginePlanStatus.ENTRY_WINDOW, 2L ),
            Map.of(),
            Map.of(),
            0L,
            0L,
            0L,
            0L,
            0L,
            null,
            null,
            false,
            0,
            0,
            0,
            null,
            null,
            0,
            0,
            0,
            0L,
            0L,
            0L,
            0L,
            0L,
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of()
        );

        assertThat( snapshot.statusBreakdown() ).hasSize( EnginePlanStatus.values().length );
        assertThat( snapshot.statusBreakdown() ).containsEntry( EnginePlanStatus.ENTRY_WINDOW, 2L );
        assertThat( snapshot.statusBreakdown() ).containsEntry( EnginePlanStatus.OVERDUE, 0L );
    }

    // REQ: ENG-CORE-003
    // REQ: ENG-CORE-004
    @Test
    void normalizesStringMapsToLowercaseAndZeroForNullValues()
    {
        Map<String, Long> actionableVenueBreakdown = new LinkedHashMap<>();
        actionableVenueBreakdown.put( "Gate", null );
        Map<String, Long> attemptStatusBreakdown = new LinkedHashMap<>();
        attemptStatusBreakdown.put( " FAILED ", null );

        EngineMetricsSnapshot snapshot = new EngineMetricsSnapshot(
            "engine-app",
            "2.0.0",
            Instant.parse( "2030-01-01T00:00:00Z" ),
            true,
            false,
            1000L,
            Instant.parse( "2030-01-01T00:00:01Z" ),
            1,
            0,
            Map.of(),
            Map.of( " ByBit ", 1L, "  ", 9L ),
            actionableVenueBreakdown,
            0L,
            0L,
            0L,
            0L,
            0L,
            null,
            null,
            false,
            0,
            0,
            0,
            null,
            null,
            0,
            0,
            0,
            0L,
            0L,
            0L,
            0L,
            0L,
            attemptStatusBreakdown,
            Map.of( "BYBIT", 2L ),
            Map.of( " Gate ", 3L ),
            Map.of( " BYBIT ", 7L ),
            Map.of( " Gate ", 8L )
        );

        assertThat( snapshot.planVenueBreakdown() ).containsExactlyEntriesOf( Map.of( "bybit", 1L ) );
        assertThat( snapshot.actionableVenueBreakdown() ).containsExactlyEntriesOf( Map.of( "gate", 0L ) );
        assertThat( snapshot.attemptStatusBreakdown() ).containsExactlyEntriesOf( Map.of( "failed", 0L ) );
        assertThat( snapshot.attemptVenueBreakdown() ).containsExactlyEntriesOf( Map.of( "bybit", 2L ) );
        assertThat( snapshot.failedAttemptVenueBreakdown() ).containsExactlyEntriesOf( Map.of( "gate", 3L ) );
        assertThat( snapshot.averageSubmitDurationMsByVenue() ).containsExactlyEntriesOf( Map.of( "bybit", 7L ) );
        assertThat( snapshot.lastSubmitDurationMsByVenue() ).containsExactlyEntriesOf( Map.of( "gate", 8L ) );
    }

    // REQ: ENG-CORE-003
    // REQ: ENG-CORE-004
    @Test
    void toleratesNullStatusEntriesAndNullMaps()
    {
        Map<EnginePlanStatus, Long> statusBreakdown = new HashMap<>();
        statusBreakdown.put( null, 9L );
        statusBreakdown.put( EnginePlanStatus.ENTRY_WINDOW, null );
        Map<String, Long> planVenueBreakdown = new HashMap<>();
        planVenueBreakdown.put( null, 9L );
        planVenueBreakdown.put( " ByBit ", 2L );

        EngineMetricsSnapshot snapshot = new EngineMetricsSnapshot(
            "engine-app",
            "2.0.0",
            Instant.parse( "2030-01-01T00:00:00Z" ),
            true,
            false,
            1000L,
            Instant.parse( "2030-01-01T00:00:01Z" ),
            1,
            0,
            statusBreakdown,
            planVenueBreakdown,
            null,
            0L,
            0L,
            0L,
            0L,
            0L,
            null,
            null,
            false,
            0,
            0,
            0,
            null,
            null,
            0,
            0,
            0,
            0L,
            0L,
            0L,
            0L,
            0L,
            null,
            null,
            null,
            null,
            null
        );

        assertThat( snapshot.statusBreakdown() ).containsEntry( EnginePlanStatus.ENTRY_WINDOW, 0L );
        assertThat( snapshot.planVenueBreakdown() ).containsExactlyEntriesOf( Map.of( "bybit", 2L ) );
        assertThat( snapshot.actionableVenueBreakdown() ).isEmpty();
        assertThat( snapshot.attemptStatusBreakdown() ).isEmpty();
    }
}
