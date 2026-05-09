package com.crypto.funding.engine;

import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.contract.engine.EngineExecutionRunResponse;
import com.crypto.funding.contract.engine.EngineExecutionTargetPhase;
import com.crypto.funding.contract.engine.EngineExecutionTargetRequest;
import com.crypto.funding.contract.engine.EngineRuntimeControlRequest;
import com.crypto.funding.contract.engine.EngineRuntimeControlResponse;
import com.crypto.funding.contract.engine.EngineSummaryResponse;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.TradeSide;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EngineControllerTest
{
    private static final Instant NOW = Instant.parse( "2030-01-01T00:00:00Z" );

    // REQ: ENG-ACC-008
    @Test
    void delegatesSummaryAndPlanReadsWithoutReshapingResponses()
    {
        EnginePlanService planService = mock( EnginePlanService.class );
        EngineExecutionService executionService = mock( EngineExecutionService.class );
        EngineRuntimeControlService runtimeControlService = mock( EngineRuntimeControlService.class );
        EngineController controller = new EngineController( planService, executionService, runtimeControlService );
        EngineSummaryResponse summary = new EngineSummaryResponse( "engine-app", "2.0.0", 2, 1, NOW, Map.of( EnginePlanStatus.ENTRY_WINDOW, 1L ) );
        List<EngineExecutionPlan> plans = List.of( plan( 5L ) );
        EngineExecutionPlan plan = plan( 7L );
        when( planService.summary() ).thenReturn( summary );
        when( planService.listPlans() ).thenReturn( plans );
        when( planService.getPlan( 7L ) ).thenReturn( plan );

        assertThat( controller.summary() ).isSameAs( summary );
        assertThat( controller.plans() ).isSameAs( plans );
        assertThat( controller.plan( 7L ) ).isSameAs( plan );
        verify( planService ).summary();
        verify( planService ).listPlans();
        verify( planService ).getPlan( 7L );
    }

    // REQ: ENG-ACC-009
    @Test
    void delegatesRunOnceWithForceFlag()
    {
        EnginePlanService planService = mock( EnginePlanService.class );
        EngineExecutionService executionService = mock( EngineExecutionService.class );
        EngineRuntimeControlService runtimeControlService = mock( EngineRuntimeControlService.class );
        EngineController controller = new EngineController( planService, executionService, runtimeControlService );
        EngineExecutionRunResponse response = new EngineExecutionRunResponse( NOW, NOW, true, 1, 1, 0, List.of() );
        when( executionService.runOnce( true ) ).thenReturn( response );

        assertThat( controller.runOnce( true ) ).isSameAs( response );
        verify( executionService ).runOnce( true );
    }

    @Test
    void delegatesTargetedExecutionRequest()
    {
        EnginePlanService planService = mock( EnginePlanService.class );
        EngineExecutionService executionService = mock( EngineExecutionService.class );
        EngineRuntimeControlService runtimeControlService = mock( EngineRuntimeControlService.class );
        EngineController controller = new EngineController( planService, executionService, runtimeControlService );
        EngineExecutionTargetRequest request = new EngineExecutionTargetRequest( 7L, EngineExecutionTargetPhase.EXIT, true );
        EngineExecutionRunResponse response = new EngineExecutionRunResponse( NOW, NOW, true, 1, 1, 0, List.of() );
        when( executionService.runTarget( 7L, EngineExecutionTargetPhase.EXIT, true ) ).thenReturn( response );

        assertThat( controller.runTarget( request ) ).isSameAs( response );
        verify( executionService ).runTarget( 7L, EngineExecutionTargetPhase.EXIT, true );
    }

    // REQ: ENG-ACC-010
    @Test
    void delegatesRuntimeReadAndUpdateWithoutChangingPayloads()
    {
        EnginePlanService planService = mock( EnginePlanService.class );
        EngineExecutionService executionService = mock( EngineExecutionService.class );
        EngineRuntimeControlService runtimeControlService = mock( EngineRuntimeControlService.class );
        EngineController controller = new EngineController( planService, executionService, runtimeControlService );
        EngineRuntimeControlRequest request = new EngineRuntimeControlRequest( true, 2_000L );
        EngineRuntimeControlResponse snapshot = new EngineRuntimeControlResponse(
            "engine-app",
            "2.0.0",
            "testnet",
            false,
            true,
            List.of( "bybit", "gate" ),
            BigDecimal.valueOf( 25 ),
            true,
            2_000L,
            100L,
            NOW,
            NOW.minusSeconds( 5 ),
            NOW.minusSeconds( 4 ),
            false,
            1,
            1,
            0,
            15L,
            NOW.minusSeconds( 10 ),
            NOW.minusSeconds( 9 ),
            0,
            0,
            0,
            0L
        );
        when( runtimeControlService.snapshot() ).thenReturn( snapshot );
        when( runtimeControlService.update( request ) ).thenReturn( snapshot );

        assertThat( controller.runtime() ).isSameAs( snapshot );
        assertThat( controller.updateRuntime( request ) ).isSameAs( snapshot );
        verify( runtimeControlService ).snapshot();
        verify( runtimeControlService ).update( request );
    }

    private static EngineExecutionPlan plan( Long armedTradeId )
    {
        return new EngineExecutionPlan(
            armedTradeId,
            13L,
            "bybit",
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
            EnginePlanStatus.ENTRY_WINDOW,
            NOW,
            0L,
            60_000L,
            "ready"
        );
    }
}
