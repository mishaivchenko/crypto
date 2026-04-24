package com.crypto.funding.engine;

import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.TradeSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnginePlanServiceTest
{
    private static final Instant NOW = Instant.parse( "2030-01-01T00:00:00Z" );

    // REQ: ENG-PLAN-001
    @Test
    void countsTotalAndActionablePlansInSummary()
    {
        EnginePlanClient client = mock( EnginePlanClient.class );
        when( client.listPlans() ).thenReturn( List.of(
            plan( 1L, "bybit", EnginePlanStatus.ENTRY_WINDOW ),
            plan( 2L, "gate", EnginePlanStatus.EXIT_WINDOW ),
            plan( 3L, "kucoin", EnginePlanStatus.WAITING_ENTRY )
        ) );
        EnginePlanService service = new EnginePlanService( client );

        var summary = service.summary();

        assertThat( summary.totalPlans() ).isEqualTo( 3 );
        assertThat( summary.actionablePlans() ).isEqualTo( 2 );
        assertThat( summary.statusBreakdown() )
            .containsEntry( EnginePlanStatus.ENTRY_WINDOW, 1L )
            .containsEntry( EnginePlanStatus.EXIT_WINDOW, 1L )
            .containsEntry( EnginePlanStatus.WAITING_ENTRY, 1L );
    }

    // REQ: ENG-PLAN-001
    @Test
    void returnsDirectClientPlanById()
    {
        EnginePlanClient client = mock( EnginePlanClient.class );
        EngineExecutionPlan expected = plan( 5L, "bybit", EnginePlanStatus.ENTRY_WINDOW );
        when( client.getPlan( 5L ) ).thenReturn( expected );
        EnginePlanService service = new EnginePlanService( client );

        assertThat( service.getPlan( 5L ) ).isSameAs( expected );
    }

    // REQ: ENG-PLAN-002
    // REQ: ENG-PLAN-003
    @Test
    void zeroFillsStatusesAndNormalizesBlankVenues()
    {
        EnginePlanClient client = mock( EnginePlanClient.class );
        when( client.listPlans() ).thenReturn( List.of(
            plan( 1L, "  ", EnginePlanStatus.ENTRY_WINDOW ),
            plan( 2L, null, EnginePlanStatus.WAITING_ENTRY ),
            plan( 3L, " ByBit ", EnginePlanStatus.OVERDUE )
        ) );
        EnginePlanService service = new EnginePlanService( client );

        var snapshot = service.loadPlanSnapshot();

        assertThat( snapshot.statusBreakdown() ).hasSize( EnginePlanStatus.values().length );
        assertThat( snapshot.statusBreakdown() ).containsEntry( EnginePlanStatus.EXIT_WINDOW, 0L );
        assertThat( snapshot.statusBreakdown() ).containsEntry( EnginePlanStatus.ENTRY_WINDOW, 1L );
        assertThat( snapshot.planVenueBreakdown() )
            .containsEntry( "unknown", 2L )
            .containsEntry( "bybit", 1L );
        assertThat( snapshot.actionableVenueBreakdown() ).containsEntry( "unknown", 1L );
        assertThat( snapshot.actionableVenueBreakdown() ).doesNotContainKey( "bybit" );
    }

    // REQ: ENG-PLAN-001
    // REQ: ENG-PLAN-002
    // REQ: ENG-PLAN-003
    @Test
    void treatsOnlyEntryAndExitWindowStatusesAsActionableAcrossFullStatusMatrix()
    {
        EnginePlanClient client = mock( EnginePlanClient.class );
        when( client.listPlans() ).thenReturn( List.of(
            plan( 1L, "alpha", EnginePlanStatus.WAITING_ENTRY ),
            plan( 2L, "bybit", EnginePlanStatus.ENTRY_WINDOW ),
            plan( 3L, "charlie", EnginePlanStatus.WAITING_EXIT ),
            plan( 4L, "gate", EnginePlanStatus.EXIT_WINDOW ),
            plan( 5L, "echo", EnginePlanStatus.OVERDUE ),
            plan( 6L, "foxtrot", EnginePlanStatus.CLOSED ),
            plan( 7L, null, EnginePlanStatus.INVALID )
        ) );
        EnginePlanService service = new EnginePlanService( client );

        var snapshot = service.loadPlanSnapshot();

        assertThat( snapshot.totalPlans() ).isEqualTo( EnginePlanStatus.values().length );
        assertThat( snapshot.actionablePlans() ).isEqualTo( 2 );
        assertThat( snapshot.statusBreakdown() ).containsExactlyInAnyOrderEntriesOf( java.util.Map.of(
            EnginePlanStatus.WAITING_ENTRY, 1L,
            EnginePlanStatus.ENTRY_WINDOW, 1L,
            EnginePlanStatus.WAITING_EXIT, 1L,
            EnginePlanStatus.EXIT_WINDOW, 1L,
            EnginePlanStatus.OVERDUE, 1L,
            EnginePlanStatus.CLOSED, 1L,
            EnginePlanStatus.INVALID, 1L
        ) );
        assertThat( snapshot.actionableVenueBreakdown() ).containsExactlyInAnyOrderEntriesOf( java.util.Map.of(
            "bybit", 1L,
            "gate", 1L
        ) );
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
            NOW.plusSeconds( 60 ),
            NOW.minusSeconds( 60 ),
            NOW.plusSeconds( 60 ),
            1,
            150L,
            0L,
            0L,
            0L,
            List.of(),
            status,
            NOW,
            0L,
            60_000L,
            "summary"
        );
    }
}
