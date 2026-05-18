package com.crypto.funding.engine;

import com.crypto.funding.application.port.ExecutionPort;
import com.crypto.funding.contract.engine.EngineEntryAttemptPlan;
import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.contract.engine.EngineOrderAttemptRecordRequest;
import com.crypto.funding.contract.engine.EngineOrderAttemptResponse;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import com.crypto.funding.domain.execution.ExecutionType;
import com.crypto.funding.domain.execution.OrderAttempt;
import com.crypto.funding.domain.execution.OrderAttemptStatus;
import com.crypto.funding.domain.execution.OrderIntent;
import com.crypto.funding.domain.liquidity.LiquidityScore;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.TradeSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiquidityCapTest
{
    private static final Instant NOW = Instant.parse( "2030-06-01T00:00:00Z" );

    private final EnginePlanClient client = mock( EnginePlanClient.class );
    private final ExecutionPort executionPort = mock( ExecutionPort.class );
    private final EngineTelemetryService telemetryService = new EngineTelemetryService();
    private final Clock clock = Clock.fixed( NOW, ZoneOffset.UTC );

    private EngineExecutionService service;

    @BeforeEach
    void setUp()
    {
        AtomicLong nano = new AtomicLong( 0 );
        service = new EngineExecutionService( client, executionPort, telemetryService, clock,
                                              () -> nano.getAndAdd( 25_000_000L ) );

        when( client.recordOrderAttempt( any() ) ).thenAnswer( inv -> {
            EngineOrderAttemptRecordRequest req = inv.getArgument( 0 );
            return new EngineOrderAttemptResponse(
                null, req.attemptKey(), req.armedTradeId(), req.attemptNumber(),
                req.venue(), req.symbol(), req.side(), req.executionType(),
                req.quantity(), req.limitPrice(),
                OrderAttemptStatus.FAILED, null,
                req.targetEntryAt(), req.triggerAt(), req.submittedAt(), null,
                "simulated", NOW, NOW
            );
        } );
    }

    // ── 8. Engine caps order size by maxOrderNotional when present ────────────

    @Test
    void entryAttempt_cappedToMaxOrderNotional()
    {
        BigDecimal planned = new BigDecimal( "500" );
        BigDecimal cap = new BigDecimal( "200" );

        EngineExecutionPlan plan = planWithNotional( planned, cap );
        when( client.listPlans( true ) ).thenReturn( List.of( plan ) );
        when( executionPort.submitOrder( any(), any(), eq( false ) ) )
            .thenAnswer( inv -> failedAttempt( plan, ( (OrderIntent) inv.getArgument( 1 ) ).quantity() ) );

        service.runOnce( true );

        ArgumentCaptor<OrderIntent> intentCaptor = ArgumentCaptor.forClass( OrderIntent.class );
        verify( executionPort ).submitOrder( any(), intentCaptor.capture(), eq( false ) );
        assertThat( intentCaptor.getValue().quantity() ).isEqualByComparingTo( cap );
    }

    // ── 9. Engine uses planned notional when maxOrderNotional is absent ───────

    @Test
    void entryAttempt_usesPlannedNotional_whenNoLiquidityCap()
    {
        BigDecimal planned = new BigDecimal( "300" );

        EngineExecutionPlan plan = planWithNotional( planned, null );
        when( client.listPlans( true ) ).thenReturn( List.of( plan ) );
        when( executionPort.submitOrder( any(), any(), eq( false ) ) )
            .thenAnswer( inv -> failedAttempt( plan, ( (OrderIntent) inv.getArgument( 1 ) ).quantity() ) );

        service.runOnce( true );

        ArgumentCaptor<OrderIntent> intentCaptor = ArgumentCaptor.forClass( OrderIntent.class );
        verify( executionPort ).submitOrder( any(), intentCaptor.capture(), eq( false ) );
        assertThat( intentCaptor.getValue().quantity() ).isEqualByComparingTo( planned );
    }

    // ── cappedNotional unit tests ─────────────────────────────────────────────

    @Test
    void cappedNotional_returnsPlanned_whenMaxIsNull()
    {
        assertThat( EngineExecutionService.cappedNotional( new BigDecimal( "500" ), null ) )
            .isEqualByComparingTo( "500" );
    }

    @Test
    void cappedNotional_returnsMax_whenPlannedExceedsMax()
    {
        assertThat( EngineExecutionService.cappedNotional( new BigDecimal( "500" ), new BigDecimal( "200" ) ) )
            .isEqualByComparingTo( "200" );
    }

    @Test
    void cappedNotional_returnsPlanned_whenPlannedBelowMax()
    {
        assertThat( EngineExecutionService.cappedNotional( new BigDecimal( "100" ), new BigDecimal( "500" ) ) )
            .isEqualByComparingTo( "100" );
    }

    @Test
    void cappedNotional_returnsZero_whenPlannedIsNull()
    {
        assertThat( EngineExecutionService.cappedNotional( null, new BigDecimal( "200" ) ) )
            .isEqualByComparingTo( BigDecimal.ZERO );
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static EngineExecutionPlan planWithNotional( BigDecimal notional, BigDecimal maxOrderNotional )
    {
        Instant entry = NOW.minusSeconds( 10 );
        EngineEntryAttemptPlan attempt = new EngineEntryAttemptPlan( 1, entry, entry, -10_000L, 0L, null );
        return new EngineExecutionPlan(
            42L, 1L, "gate", "BTC_USDT",
            TradeSide.SHORT, notional,
            ArmedTradeState.ARMED, NOW.plusSeconds( 3600 ),
            entry, NOW.plusSeconds( 7200 ),
            1, 0L,
            null, null, null,
            List.of( attempt ),
            EnginePlanStatus.ENTRY_WINDOW,
            entry, -10_000L, 3_600_000L,
            "test",
            "BTC_USDT",
            new BigDecimal( "0.001" ),
            new BigDecimal( "0.001" ),
            new BigDecimal( "5" ),
            NOW, NOW,
            null, null,
            null, null,
            null, null, null,
            maxOrderNotional,
            maxOrderNotional != null ? "test-assessment-id" : null,
            maxOrderNotional != null ? LiquidityScore.MEDIUM : null,
            maxOrderNotional != null ? NOW : null
        );
    }

    private static OrderAttempt failedAttempt( EngineExecutionPlan plan, BigDecimal qty )
    {
        return new OrderAttempt(
            null, "test-key",
            plan.armedTradeId(), 1,
            plan.venue(), plan.symbol(),
            plan.intendedSide(), ExecutionType.MARKET,
            qty, null,
            OrderAttemptStatus.FAILED, null,
            NOW, NOW, NOW, null, "simulated",
            NOW, NOW
        );
    }
}
