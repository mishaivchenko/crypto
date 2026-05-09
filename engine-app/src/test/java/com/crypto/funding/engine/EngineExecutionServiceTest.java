package com.crypto.funding.engine;

import com.crypto.funding.application.port.ExecutionPort;
import com.crypto.funding.contract.engine.EngineEntryAttemptPlan;
import com.crypto.funding.contract.engine.EngineExecutionAttemptResult;
import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.contract.engine.EnginePositionRecordRequest;
import com.crypto.funding.contract.engine.EnginePositionResponse;
import com.crypto.funding.contract.engine.EngineOrderAttemptRecordRequest;
import com.crypto.funding.contract.engine.EngineOrderAttemptResponse;
import com.crypto.funding.contract.engine.EngineExecutionTargetPhase;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import com.crypto.funding.contract.engine.EngineTradeOutcomeRecordRequest;
import com.crypto.funding.contract.engine.EngineTradeOutcomeResponse;
import com.crypto.funding.contract.engine.EngineTradeStateUpdateRequest;
import com.crypto.funding.domain.execution.ExecutionType;
import com.crypto.funding.domain.execution.OrderAttempt;
import com.crypto.funding.domain.execution.OrderAttemptStatus;
import com.crypto.funding.domain.execution.OrderIntent;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.PositionState;
import com.crypto.funding.domain.trade.TradeSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EngineExecutionServiceTest
{
    private static final Instant NOW = Instant.parse( "2030-01-01T00:00:00Z" );

    private final EnginePlanClient client = mock( EnginePlanClient.class );
    private final ExecutionPort executionPort = mock( ExecutionPort.class );
    private final EngineTelemetryService telemetryService = spy( new EngineTelemetryService() );
    private final Clock clock = Clock.fixed( NOW, ZoneOffset.UTC );

    private EngineExecutionService service;

    @BeforeEach
    void setUp()
    {
        service = new EngineExecutionService(
            client,
            executionPort,
            telemetryService,
            clock,
            advancingNanos( Duration.ofSeconds( 1 ).toNanos(), Duration.ofMillis( 25 ).toNanos() )
        );
        when( client.recordOrderAttempt( any() ) ).thenAnswer( invocation -> recordedAttempt( invocation.getArgument( 0 ) ) );
        when( client.recordPosition( any() ) ).thenAnswer( invocation -> recordedPosition( invocation.getArgument( 0 ) ) );
        when( client.recordTradeOutcome( any() ) ).thenAnswer( invocation -> recordedOutcome( invocation.getArgument( 0 ) ) );
        when( executionPort.submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( false ) ) )
            .thenReturn( failedAttempt( 5L, "bybit", "REQ/USDT", NOW.minusSeconds( 1 ) ) );
        when( executionPort.submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( true ) ) )
            .thenReturn( filledAttempt( 5L, "bybit", "REQ/USDT", NOW ) );
    }

    // REQ: ENG-EXE-001
    // REQ: ENG-EXE-007
    @Test
    void processesOnlyEntryWindowPlansWhenNotForced()
    {
        when( client.listPlans( false ) ).thenReturn( List.of(
            plan( 1L, EnginePlanStatus.WAITING_ENTRY, List.of( pastAttempt( 1, 0 ) ) ),
            plan( 2L, EnginePlanStatus.ENTRY_WINDOW, List.of( pastAttempt( 1, 0 ) ) ),
            plan( 3L, EnginePlanStatus.OVERDUE, List.of( pastAttempt( 1, 0 ) ) )
        ) );

        var response = service.runOnce( false );

        assertThat( response.plansScanned() ).isEqualTo( 3 );
        assertThat( response.attemptsSubmitted() ).isEqualTo( 1 );
        assertThat( response.attemptsSkipped() ).isEqualTo( 2 );
        assertThat( response.results() ).hasSize( 1 );
        verify( executionPort, times( 1 ) ).submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( false ) );
        assertThat( telemetryService.snapshot().scheduledExecutionRuns() ).isEqualTo( 1 );
        assertThat( telemetryService.snapshot().forcedExecutionRuns() ).isZero();
    }

    // REQ: ENG-EXE-002
    // REQ: ENG-EXE-007
    @Test
    void forceModeIncludesWaitingEntryAndOverduePlans()
    {
        when( client.listPlans( true ) ).thenReturn( List.of(
            plan( 1L, EnginePlanStatus.WAITING_ENTRY, List.of( pastAttempt( 1, 0 ) ) ),
            plan( 2L, EnginePlanStatus.ENTRY_WINDOW, List.of( pastAttempt( 1, 100 ) ) ),
            plan( 3L, EnginePlanStatus.OVERDUE, List.of( pastAttempt( 1, 200 ) ) )
        ) );

        var response = service.runOnce( true );

        assertThat( response.force() ).isTrue();
        assertThat( response.attemptsSubmitted() ).isEqualTo( 3 );
        assertThat( response.attemptsSkipped() ).isZero();
        verify( executionPort, times( 3 ) ).submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( false ) );
        assertThat( telemetryService.snapshot().forcedExecutionRuns() ).isEqualTo( 1 );
        assertThat( telemetryService.snapshot().lastForcedAttemptsSubmitted() ).isEqualTo( 3 );
    }

    // REQ: ENG-EXE-003
    @Test
    void forceModeStillRejectsExitClosedAndInvalidPlans()
    {
        when( client.listPlans( true ) ).thenReturn( List.of(
            plan( 1L, EnginePlanStatus.WAITING_ENTRY, List.of( pastAttempt( 1, 0 ) ) ),
            plan( 2L, EnginePlanStatus.ENTRY_WINDOW, List.of( pastAttempt( 1, 100 ) ) ),
            plan( 3L, EnginePlanStatus.OVERDUE, List.of( pastAttempt( 1, 200 ) ) ),
            plan( 4L, EnginePlanStatus.WAITING_EXIT, List.of( pastAttempt( 1, 300 ) ) ),
            plan( 5L, EnginePlanStatus.EXIT_WINDOW, List.of( pastAttempt( 1, 400 ) ) ),
            plan( 6L, EnginePlanStatus.CLOSED, List.of( pastAttempt( 1, 500 ) ) ),
            plan( 7L, EnginePlanStatus.INVALID, List.of( pastAttempt( 1, 600 ) ) )
        ) );

        var response = service.runOnce( true );

        assertThat( response.attemptsSubmitted() ).isEqualTo( 3 );
        assertThat( response.attemptsSkipped() ).isEqualTo( 4 );
        verify( executionPort, times( 3 ) ).submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( false ) );
    }

    // REQ: ENG-EXE-003
    @Test
    void skipsPlansWithNullOrEmptyAttempts()
    {
        when( client.listPlans( true ) ).thenReturn( List.of(
            plan( 1L, EnginePlanStatus.ENTRY_WINDOW, null ),
            plan( 2L, EnginePlanStatus.ENTRY_WINDOW, List.of() )
        ) );

        var response = service.runOnce( true );

        assertThat( response.attemptsSubmitted() ).isZero();
        assertThat( response.attemptsSkipped() ).isEqualTo( 2 );
        assertThat( response.results() ).isEmpty();
        verify( executionPort, never() ).submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( false ) );
        verify( client, never() ).recordOrderAttempt( any() );
    }

    // REQ: ENG-EXE-004
    @Test
    void skipsFutureTriggersWhenNotForced()
    {
        when( client.listPlans( false ) ).thenReturn( List.of(
            plan( 5L, EnginePlanStatus.ENTRY_WINDOW, List.of( futureAttempt( 1, 1_000 ) ) )
        ) );

        var response = service.runOnce( false );

        assertThat( response.plansScanned() ).isEqualTo( 1 );
        assertThat( response.attemptsSubmitted() ).isZero();
        assertThat( response.attemptsSkipped() ).isEqualTo( 1 );
        verify( executionPort, never() ).submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( false ) );
    }

    @Test
    void targetedEntryFetchesOnlyRequestedTradeAndRecordsLifecycle()
    {
        when( client.getPlan( 5L ) ).thenReturn( plan( 5L, EnginePlanStatus.WAITING_ENTRY, List.of( futureAttempt( 1, 1_000 ) ) ) );
        when( executionPort.submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( false ) ) )
            .thenReturn( filledAttempt( 5L, "bybit", "REQ/USDT", NOW ) );
        ArgumentCaptor<EngineTradeStateUpdateRequest> stateCaptor = ArgumentCaptor.forClass( EngineTradeStateUpdateRequest.class );

        var response = service.runTarget( 5L, EngineExecutionTargetPhase.ENTRY, true );

        assertThat( response.plansScanned() ).isEqualTo( 1 );
        assertThat( response.attemptsSubmitted() ).isEqualTo( 1 );
        assertThat( response.attemptsSkipped() ).isZero();
        assertThat( response.results() ).singleElement().satisfies( result -> {
            assertThat( result.armedTradeId() ).isEqualTo( 5L );
            assertThat( result.status() ).isEqualTo( OrderAttemptStatus.FILLED );
        } );
        verify( client ).getPlan( 5L );
        verify( client, never() ).listPlans( true );
        verify( executionPort ).submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( false ) );
        verify( client ).recordPosition( any() );
        verify( client ).updateTradeState( eq( 5L ), stateCaptor.capture() );
        verify( telemetryService ).recordExecutionRun( eq( true ), eq( NOW ), eq( NOW ), eq( 1 ), eq( 1 ), eq( 0 ), eq( 0L ) );
        assertThat( stateCaptor.getValue().state() ).isEqualTo( ArmedTradeState.OPEN );
    }

    @Test
    void targetedExitFetchesOnlyRequestedTradeAndRecordsOutcome()
    {
        when( client.getPlan( 5L ) ).thenReturn( openPlan( 5L ) );
        ArgumentCaptor<EngineTradeOutcomeRecordRequest> outcomeCaptor = ArgumentCaptor.forClass( EngineTradeOutcomeRecordRequest.class );

        var response = service.runTarget( 5L, EngineExecutionTargetPhase.EXIT, true );

        assertThat( response.plansScanned() ).isEqualTo( 1 );
        assertThat( response.attemptsSubmitted() ).isEqualTo( 1 );
        assertThat( response.attemptsSkipped() ).isZero();
        verify( client ).getPlan( 5L );
        verify( client, never() ).listPlans( true );
        verify( executionPort ).submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( true ) );
        verify( client ).recordTradeOutcome( outcomeCaptor.capture() );
        assertThat( outcomeCaptor.getValue().outcomeCode() ).isEqualTo( "CLOSED" );
    }

    @Test
    void targetedExecutionSkipsInvalidRequestWithoutFetchingPlan()
    {
        var response = service.runTarget( null, EngineExecutionTargetPhase.ENTRY, true );

        assertThat( response.attemptsSubmitted() ).isZero();
        assertThat( response.attemptsSkipped() ).isEqualTo( 1 );
        verify( client, never() ).getPlan( any() );
        verify( executionPort, never() ).submitOrder( any(), any(), eq( false ) );
    }

    @Test
    void targetedEntryWithoutForceWaitsForTrigger()
    {
        when( client.getPlan( 5L ) ).thenReturn( plan( 5L, EnginePlanStatus.ENTRY_WINDOW, List.of( futureAttempt( 1, 1_000 ) ) ) );

        var response = service.runTarget( 5L, EngineExecutionTargetPhase.ENTRY, false );

        assertThat( response.attemptsSubmitted() ).isZero();
        assertThat( response.attemptsSkipped() ).isEqualTo( 1 );
        verify( executionPort, never() ).submitOrder( any(), any(), eq( false ) );
        verify( client, never() ).recordOrderAttempt( any() );
    }

    @Test
    void targetedEntryWithoutForceProcessesDueEntryWindow()
    {
        when( client.getPlan( 5L ) ).thenReturn( plan( 5L, EnginePlanStatus.ENTRY_WINDOW, List.of( pastAttempt( 1, 0 ) ) ) );

        var response = service.runTarget( 5L, EngineExecutionTargetPhase.ENTRY, false );

        assertThat( response.attemptsSubmitted() ).isEqualTo( 1 );
        assertThat( response.attemptsSkipped() ).isZero();
        verify( executionPort ).submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( false ) );
    }

    @Test
    void targetedEntrySkipsMissingAttemptAndInvalidStatus()
    {
        when( client.getPlan( 5L ) ).thenReturn( plan( 5L, EnginePlanStatus.ENTRY_WINDOW, List.of() ) );
        when( client.getPlan( 6L ) ).thenReturn( plan( 6L, EnginePlanStatus.CLOSED, List.of( pastAttempt( 1, 0 ) ) ) );

        var missingAttempt = service.runTarget( 5L, EngineExecutionTargetPhase.ENTRY, true );
        var invalidStatus = service.runTarget( 6L, EngineExecutionTargetPhase.ENTRY, true );

        assertThat( missingAttempt.attemptsSkipped() ).isEqualTo( 1 );
        assertThat( invalidStatus.attemptsSkipped() ).isEqualTo( 1 );
        verify( executionPort, never() ).submitOrder( any(), any(), eq( false ) );
    }

    @Test
    void targetedExitForceAllowsWaitingExit()
    {
        when( client.getPlan( 5L ) ).thenReturn( openPlan( 5L, EnginePlanStatus.WAITING_EXIT ) );

        var response = service.runTarget( 5L, EngineExecutionTargetPhase.EXIT, true );

        assertThat( response.attemptsSubmitted() ).isEqualTo( 1 );
        assertThat( response.attemptsSkipped() ).isZero();
        verify( executionPort ).submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( true ) );
    }

    @Test
    void targetedExitWithoutForceProcessesOnlyExitWindow()
    {
        when( client.getPlan( 5L ) ).thenReturn( openPlan( 5L ) );
        when( client.getPlan( 6L ) ).thenReturn( openPlan( 6L, EnginePlanStatus.WAITING_EXIT ) );

        var due = service.runTarget( 5L, EngineExecutionTargetPhase.EXIT, false );
        var waiting = service.runTarget( 6L, EngineExecutionTargetPhase.EXIT, false );

        assertThat( due.attemptsSubmitted() ).isEqualTo( 1 );
        assertThat( waiting.attemptsSkipped() ).isEqualTo( 1 );
        verify( executionPort, times( 1 ) ).submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( true ) );
    }

    @Test
    void targetedExitSkipsMissingPositionQuantity()
    {
        when( client.getPlan( 5L ) ).thenReturn( openPlan( 5L, TradeSide.SHORT, null, BigDecimal.valueOf( 2.5 ) ) );
        when( client.getPlan( 6L ) ).thenReturn( openPlan( 6L, TradeSide.SHORT, BigDecimal.ZERO, BigDecimal.valueOf( 2.5 ) ) );

        var missing = service.runTarget( 5L, EngineExecutionTargetPhase.EXIT, true );
        var zero = service.runTarget( 6L, EngineExecutionTargetPhase.EXIT, true );

        assertThat( missing.attemptsSubmitted() ).isZero();
        assertThat( missing.attemptsSkipped() ).isEqualTo( 1 );
        assertThat( zero.attemptsSubmitted() ).isZero();
        assertThat( zero.attemptsSkipped() ).isEqualTo( 1 );
        verify( executionPort, never() ).submitOrder( any(), any(), eq( true ) );
    }

    @Test
    void targetedExitForceSkipsClosedTrade()
    {
        when( client.getPlan( 5L ) ).thenReturn( openPlan( 5L, EnginePlanStatus.CLOSED ) );

        var response = service.runTarget( 5L, EngineExecutionTargetPhase.EXIT, true );

        assertThat( response.attemptsSubmitted() ).isZero();
        assertThat( response.attemptsSkipped() ).isEqualTo( 1 );
        verify( executionPort, never() ).submitOrder( any(), any(), eq( true ) );
    }

    @Test
    void duplicateTargetedEntryClicksDoNotDuplicateOrder()
    {
        EngineExecutionPlan plan = plan( 5L, EnginePlanStatus.ENTRY_WINDOW, List.of( pastAttempt( 1, 0 ) ) );
        when( client.getPlan( 5L ) ).thenReturn( plan );

        var first = service.runTarget( 5L, EngineExecutionTargetPhase.ENTRY, true );
        var second = service.runTarget( 5L, EngineExecutionTargetPhase.ENTRY, true );

        assertThat( first.attemptsSubmitted() ).isEqualTo( 1 );
        assertThat( second.attemptsSubmitted() ).isZero();
        assertThat( second.attemptsSkipped() ).isEqualTo( 1 );
        verify( executionPort, times( 1 ) ).submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( false ) );
    }

    @Test
    void duplicateTargetedExitClicksDoNotDuplicateOrder()
    {
        EngineExecutionPlan plan = openPlan( 5L );
        when( client.getPlan( 5L ) ).thenReturn( plan );

        var first = service.runTarget( 5L, EngineExecutionTargetPhase.EXIT, true );
        var second = service.runTarget( 5L, EngineExecutionTargetPhase.EXIT, true );

        assertThat( first.attemptsSubmitted() ).isEqualTo( 1 );
        assertThat( second.attemptsSubmitted() ).isZero();
        assertThat( second.attemptsSkipped() ).isEqualTo( 1 );
        verify( executionPort, times( 1 ) ).submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( true ) );
    }

    @Test
    void targetedEntryFailureIsIsolated()
    {
        when( client.getPlan( 5L ) ).thenReturn( plan( 5L, EnginePlanStatus.ENTRY_WINDOW, List.of( pastAttempt( 1, 0 ) ) ) );
        doThrow( new IllegalStateException( "monitor write failed" ) ).when( client ).recordOrderAttempt( any() );

        var response = service.runTarget( 5L, EngineExecutionTargetPhase.ENTRY, true );

        assertThat( response.attemptsSubmitted() ).isEqualTo( 1 );
        assertThat( response.results() ).singleElement().satisfies( result -> {
            assertThat( result.status() ).isEqualTo( OrderAttemptStatus.FAILED );
            assertThat( result.failureReason() ).contains( "monitor write failed" );
        } );
    }

    @Test
    void targetedExitFailureIsIsolated()
    {
        when( client.getPlan( 5L ) ).thenReturn( openPlan( 5L ) );
        doThrow( new IllegalStateException( "monitor write failed" ) ).when( client ).recordOrderAttempt( any() );

        var response = service.runTarget( 5L, EngineExecutionTargetPhase.EXIT, true );

        assertThat( response.attemptsSubmitted() ).isEqualTo( 1 );
        assertThat( response.results() ).singleElement().satisfies( result -> {
            assertThat( result.status() ).isEqualTo( OrderAttemptStatus.FAILED );
            assertThat( result.failureReason() ).contains( "monitor write failed" );
        } );
    }

    // REQ: ENG-EXE-005
    // REQ: ENG-EXE-006
    // REQ: ENG-EXE-007
    @Test
    void recordsDeterministicAttemptKeysPersistsExecutionResultAndMeasuresSubmitDuration()
    {
        EngineEntryAttemptPlan attemptPlan = new EngineEntryAttemptPlan(
            2,
            Instant.parse( "2029-12-31T23:59:00.150Z" ),
            NOW.minusSeconds( 2 ),
            0L,
            150L,
            0L
        );
        when( client.listPlans( true ) ).thenReturn( List.of( plan( 5L, EnginePlanStatus.ENTRY_WINDOW, List.of( attemptPlan ) ) ) );
        when( executionPort.submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( false ) ) )
            .thenReturn( failedAttempt( 5L, "bybit", "REQ/USDT", NOW.minusSeconds( 1 ) ) );

        var response = service.runOnce( true );
        ArgumentCaptor<EngineOrderAttemptRecordRequest> requestCaptor =
            ArgumentCaptor.forClass( EngineOrderAttemptRecordRequest.class );

        verify( client ).recordOrderAttempt( requestCaptor.capture() );
        EngineOrderAttemptRecordRequest recorded = requestCaptor.getValue();

        assertThat( recorded.attemptKey() ).isEqualTo( "entry:5:2:2029-12-31T23:59:00.150Z" );
        assertThat( recorded.armedTradeId() ).isEqualTo( 5L );
        assertThat( recorded.attemptNumber() ).isEqualTo( 2 );
        assertThat( recorded.status() ).isEqualTo( OrderAttemptStatus.FAILED );
        assertThat( recorded.failureReason() ).isEqualTo( "Missing engine credentials for bybit." );
        assertThat( response.results() ).singleElement().satisfies( result -> {
            assertThat( result.attemptKey() ).isEqualTo( recorded.attemptKey() );
            assertThat( result.status() ).isEqualTo( OrderAttemptStatus.FAILED );
            assertThat( result.failureReason() ).isEqualTo( "Missing engine credentials for bybit." );
        } );
        verify( telemetryService ).recordOrderSubmission( "bybit", OrderAttemptStatus.FAILED, 25L );
        verify( telemetryService ).recordExecutionRun( eq( true ), eq( NOW ), eq( NOW ), eq( 1 ), eq( 1 ), eq( 0 ), eq( 0L ) );
    }

    @Test
    void filledEntryRecordsOpenPositionAndMovesTradeOpen()
    {
        when( client.listPlans( false ) ).thenReturn( List.of( plan( 5L, EnginePlanStatus.ENTRY_WINDOW, List.of( pastAttempt( 1, 0 ) ) ) ) );
        when( executionPort.submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( false ) ) )
            .thenReturn( filledAttempt( 5L, "bybit", "REQ/USDT", NOW ) );

        var response = service.runOnce( false );
        ArgumentCaptor<EnginePositionRecordRequest> positionCaptor = ArgumentCaptor.forClass( EnginePositionRecordRequest.class );
        ArgumentCaptor<EngineTradeStateUpdateRequest> stateCaptor = ArgumentCaptor.forClass( EngineTradeStateUpdateRequest.class );

        assertThat( response.attemptsSubmitted() ).isEqualTo( 1 );
        verify( client ).recordPosition( positionCaptor.capture() );
        verify( client ).updateTradeState( eq( 5L ), stateCaptor.capture() );
        assertThat( positionCaptor.getValue().state() ).isEqualTo( PositionState.OPEN );
        assertThat( positionCaptor.getValue().quantity() ).isEqualByComparingTo( "10" );
        assertThat( positionCaptor.getValue().entryPrice() ).isEqualByComparingTo( "2.5" );
        assertThat( stateCaptor.getValue().state() ).isEqualTo( ArmedTradeState.OPEN );
    }

    @Test
    void acknowledgedEntryDoesNotChangePositionOrTradeState()
    {
        when( client.listPlans( false ) ).thenReturn( List.of( plan( 5L, EnginePlanStatus.ENTRY_WINDOW, List.of( pastAttempt( 1, 0 ) ) ) ) );
        when( executionPort.submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( false ) ) )
            .thenReturn( acknowledgedAttempt( 5L, "bybit", "REQ/USDT", NOW ) );

        var response = service.runOnce( false );

        assertThat( response.results() ).singleElement().satisfies( result -> assertThat( result.status() ).isEqualTo( OrderAttemptStatus.ACKNOWLEDGED ) );
        verify( client, never() ).recordPosition( any() );
        verify( client, never() ).updateTradeState( any(), any() );
    }

    @Test
    void filledEntryFallsBackToSubmittedAtAndOrderQuantityWhenFillDetailsArePartial()
    {
        when( client.listPlans( false ) ).thenReturn( List.of( plan( 5L, EnginePlanStatus.ENTRY_WINDOW, List.of( pastAttempt( 1, 0 ) ) ) ) );
        when( executionPort.submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( false ) ) )
            .thenReturn( filledAttemptWithoutExchangeTimestamp( 5L, "bybit", "REQ/USDT", NOW ) );

        service.runOnce( false );
        ArgumentCaptor<EnginePositionRecordRequest> positionCaptor = ArgumentCaptor.forClass( EnginePositionRecordRequest.class );

        verify( client ).recordPosition( positionCaptor.capture() );
        assertThat( positionCaptor.getValue().openedAt() ).isEqualTo( NOW );
        assertThat( positionCaptor.getValue().quantity() ).isEqualByComparingTo( "10" );
    }

    @Test
    void exitWindowSubmitsReduceOnlyExitAndRecordsOutcome()
    {
        when( client.listPlans( false ) ).thenReturn( List.of( openPlan( 5L ) ) );

        var response = service.runOnce( false );
        ArgumentCaptor<OrderIntent> intentCaptor = ArgumentCaptor.forClass( OrderIntent.class );
        ArgumentCaptor<EngineOrderAttemptRecordRequest> attemptCaptor = ArgumentCaptor.forClass( EngineOrderAttemptRecordRequest.class );
        ArgumentCaptor<EngineTradeStateUpdateRequest> stateCaptor = ArgumentCaptor.forClass( EngineTradeStateUpdateRequest.class );
        ArgumentCaptor<EngineTradeOutcomeRecordRequest> outcomeCaptor = ArgumentCaptor.forClass( EngineTradeOutcomeRecordRequest.class );

        assertThat( response.attemptsSubmitted() ).isEqualTo( 1 );
        assertThat( response.results() ).singleElement().satisfies( result -> assertThat( result.status() ).isEqualTo( OrderAttemptStatus.FILLED ) );
        verify( executionPort ).submitOrder( any( EngineExecutionPlan.class ), intentCaptor.capture(), eq( true ) );
        verify( client ).recordOrderAttempt( attemptCaptor.capture() );
        verify( client ).updateTradeState( eq( 5L ), stateCaptor.capture() );
        verify( client ).recordTradeOutcome( outcomeCaptor.capture() );
        verify( telemetryService ).recordOrderSubmission( "bybit", OrderAttemptStatus.FILLED, 25L );
        assertThat( intentCaptor.getValue().side() ).isEqualTo( TradeSide.LONG );
        assertThat( intentCaptor.getValue().quantity() ).isEqualByComparingTo( "10" );
        assertThat( attemptCaptor.getValue().attemptKey() ).isEqualTo( "exit:5:2030-01-01T00:01:00Z" );
        assertThat( stateCaptor.getValue().state() ).isEqualTo( ArmedTradeState.CLOSED );
        assertThat( outcomeCaptor.getValue().outcomeCode() ).isEqualTo( "CLOSED" );
    }

    @Test
    void duplicateScheduledEntryTicksDoNotSubmitTheSameAttemptTwice()
    {
        EngineExecutionPlan plan = plan( 5L, EnginePlanStatus.ENTRY_WINDOW, List.of( pastAttempt( 1, 0 ) ) );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ), List.of( plan ) );

        var first = service.runOnce( false );
        var second = service.runOnce( false );

        assertThat( first.attemptsSubmitted() ).isEqualTo( 1 );
        assertThat( second.attemptsSubmitted() ).isZero();
        assertThat( second.attemptsSkipped() ).isEqualTo( 1 );
        verify( executionPort, times( 1 ) ).submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( false ) );
        verify( client, times( 1 ) ).recordOrderAttempt( any() );
    }

    @Test
    void duplicateScheduledExitTicksDoNotSubmitTheSameAttemptTwice()
    {
        EngineExecutionPlan plan = openPlan( 5L );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ), List.of( plan ) );

        var first = service.runOnce( false );
        var second = service.runOnce( false );

        assertThat( first.attemptsSubmitted() ).isEqualTo( 1 );
        assertThat( second.attemptsSubmitted() ).isZero();
        assertThat( second.attemptsSkipped() ).isEqualTo( 1 );
        verify( executionPort, times( 1 ) ).submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( true ) );
        verify( client, times( 1 ) ).recordOrderAttempt( any() );
    }

    @Test
    void isolatesEntryFailuresAndContinuesProcessingOtherTrades()
    {
        when( client.listPlans( false ) ).thenReturn( List.of(
            plan( 5L, EnginePlanStatus.ENTRY_WINDOW, List.of( pastAttempt( 1, 0 ) ) ),
            plan( 6L, EnginePlanStatus.ENTRY_WINDOW, List.of( pastAttempt( 1, 0 ) ) )
        ) );
        doAnswer( invocation -> {
            EngineOrderAttemptRecordRequest request = invocation.getArgument( 0 );
            if( request.armedTradeId().equals( 5L ) )
            {
                throw new IllegalStateException( "monitor write failed" );
            }
            return recordedAttempt( request );
        } ).when( client ).recordOrderAttempt( any() );

        var response = service.runOnce( false );

        assertThat( response.plansScanned() ).isEqualTo( 2 );
        assertThat( response.attemptsSubmitted() ).isEqualTo( 2 );
        assertThat( response.results() ).extracting( EngineExecutionAttemptResult::armedTradeId )
                                      .containsExactly( 5L, 6L );
        assertThat( response.results().getFirst().status() ).isEqualTo( OrderAttemptStatus.FAILED );
        assertThat( response.results().getFirst().failureReason() ).contains( "monitor write failed" );
        verify( executionPort, times( 2 ) ).submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( false ) );
        verify( client, times( 2 ) ).recordOrderAttempt( any() );
    }

    @Test
    void exitWindowUsesSubmittedAtWhenExchangeTimestampIsMissing()
    {
        when( client.listPlans( false ) ).thenReturn( List.of( openPlan( 5L ) ) );
        when( executionPort.submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( true ) ) )
            .thenReturn( filledAttemptWithoutExchangeTimestamp( 5L, "bybit", "REQ/USDT", NOW ) );

        service.runOnce( false );
        ArgumentCaptor<EnginePositionRecordRequest> positionCaptor = ArgumentCaptor.forClass( EnginePositionRecordRequest.class );
        ArgumentCaptor<EngineTradeOutcomeRecordRequest> outcomeCaptor = ArgumentCaptor.forClass( EngineTradeOutcomeRecordRequest.class );

        verify( client ).recordPosition( positionCaptor.capture() );
        verify( client ).recordTradeOutcome( outcomeCaptor.capture() );
        assertThat( positionCaptor.getValue().closedAt() ).isEqualTo( NOW );
        assertThat( outcomeCaptor.getValue().evaluatedAt() ).isEqualTo( NOW );
    }

    @Test
    void rejectedExitMarksOnlyThatTradeFailedWithoutOutcome()
    {
        when( client.listPlans( false ) ).thenReturn( List.of( openPlan( 5L ) ) );
        when( executionPort.submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( true ) ) )
            .thenReturn( rejectedAttempt( 5L, "bybit", "REQ/USDT", NOW ) );
        ArgumentCaptor<EngineTradeStateUpdateRequest> stateCaptor = ArgumentCaptor.forClass( EngineTradeStateUpdateRequest.class );

        var response = service.runOnce( false );

        assertThat( response.results() ).singleElement().satisfies( result -> assertThat( result.status() ).isEqualTo( OrderAttemptStatus.REJECTED ) );
        verify( client ).updateTradeState( eq( 5L ), stateCaptor.capture() );
        verify( client, never() ).recordTradeOutcome( any() );
        assertThat( stateCaptor.getValue().state() ).isEqualTo( ArmedTradeState.FAILED );
    }

    @Test
    void longExitRecordsPositiveGrossPnlAndZeroFeesWhenFeeIsMissing()
    {
        when( client.listPlans( false ) ).thenReturn( List.of( openPlan( 5L, TradeSide.LONG, BigDecimal.TEN, BigDecimal.valueOf( 2.5 ) ) ) );
        when( executionPort.submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( true ) ) )
            .thenReturn( filledAttempt( 5L, "bybit", "REQ/USDT", TradeSide.SHORT, NOW, BigDecimal.valueOf( 3 ), BigDecimal.TEN, null, NOW ) );
        ArgumentCaptor<OrderIntent> intentCaptor = ArgumentCaptor.forClass( OrderIntent.class );
        ArgumentCaptor<EngineTradeOutcomeRecordRequest> outcomeCaptor = ArgumentCaptor.forClass( EngineTradeOutcomeRecordRequest.class );

        service.runOnce( false );

        verify( executionPort ).submitOrder( any( EngineExecutionPlan.class ), intentCaptor.capture(), eq( true ) );
        verify( client ).recordTradeOutcome( outcomeCaptor.capture() );
        assertThat( intentCaptor.getValue().side() ).isEqualTo( TradeSide.SHORT );
        assertThat( outcomeCaptor.getValue().grossPnlUsd() ).isEqualByComparingTo( "5" );
        assertThat( outcomeCaptor.getValue().netPnlUsd() ).isEqualByComparingTo( "5" );
        assertThat( outcomeCaptor.getValue().feesUsd() ).isEqualByComparingTo( "0" );
    }

    @Test
    void skipsNullPlansAndExitPlansWithoutPositivePositionQuantity()
    {
        when( client.listPlans( false ) ).thenReturn( Arrays.asList(
            null,
            openPlan( 5L, TradeSide.SHORT, null, BigDecimal.valueOf( 2.5 ) ),
            openPlan( 6L, TradeSide.SHORT, BigDecimal.ZERO, BigDecimal.valueOf( 2.5 ) )
        ) );

        var response = service.runOnce( false );

        assertThat( response.attemptsSubmitted() ).isZero();
        assertThat( response.attemptsSkipped() ).isEqualTo( 3 );
        verify( executionPort, never() ).submitOrder( any( EngineExecutionPlan.class ), any( OrderIntent.class ), eq( true ) );
    }

    private static LongSupplier advancingNanos( long startingNanos, long stepNanos )
    {
        AtomicLong current = new AtomicLong( startingNanos );
        return () -> current.getAndAdd( stepNanos );
    }

    private static EngineExecutionPlan plan( Long armedTradeId, EnginePlanStatus status, List<EngineEntryAttemptPlan> attempts )
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
            attempts == null ? null : attempts.size(),
            150L,
            0L,
            0L,
            0L,
            attempts,
            status,
            NOW,
            0L,
            60_000L,
            "summary",
            "REQUSDT",
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.valueOf( 5 ),
            NOW.minusSeconds( 60 ),
            NOW.minusSeconds( 60 ),
            null,
            null
        );
    }

    private static EngineExecutionPlan openPlan( Long armedTradeId )
    {
        return openPlan( armedTradeId, EnginePlanStatus.EXIT_WINDOW );
    }

    private static EngineExecutionPlan openPlan( Long armedTradeId, EnginePlanStatus status )
    {
        return openPlan( armedTradeId, TradeSide.SHORT, BigDecimal.TEN, BigDecimal.valueOf( 2.5 ), status );
    }

    private static EngineExecutionPlan openPlan( Long armedTradeId, TradeSide side, BigDecimal quantity, BigDecimal entryPrice )
    {
        return openPlan( armedTradeId, side, quantity, entryPrice, EnginePlanStatus.EXIT_WINDOW );
    }

    private static EngineExecutionPlan openPlan(
        Long armedTradeId,
        TradeSide side,
        BigDecimal quantity,
        BigDecimal entryPrice,
        EnginePlanStatus status
    )
    {
        return new EngineExecutionPlan(
            armedTradeId,
            13L,
            "bybit",
            "REQ/USDT",
            side,
            BigDecimal.valueOf( 25 ),
            ArmedTradeState.OPEN,
            NOW.minusSeconds( 60 ),
            NOW.minusSeconds( 120 ),
            NOW.plusSeconds( 60 ),
            1,
            0L,
            0L,
            0L,
            0L,
            List.of( pastAttempt( 1, 0 ) ),
            status,
            NOW,
            0L,
            -60_000L,
            "exit",
            "REQUSDT",
            BigDecimal.ONE,
            BigDecimal.ONE,
            BigDecimal.valueOf( 5 ),
            NOW.minusSeconds( 3600 ),
            NOW.minusSeconds( 300 ),
            quantity,
            entryPrice
        );
    }

    private static EngineEntryAttemptPlan pastAttempt( int attemptNumber, long offsetMs )
    {
        Instant target = NOW.minusMillis( 500 - offsetMs );
        return new EngineEntryAttemptPlan( attemptNumber, target, target, 0L, offsetMs, 0L );
    }

    private static EngineEntryAttemptPlan futureAttempt( int attemptNumber, long offsetMs )
    {
        Instant target = NOW.plusMillis( offsetMs );
        return new EngineEntryAttemptPlan( attemptNumber, target, target, offsetMs, offsetMs, 0L );
    }

    private static OrderAttempt failedAttempt( Long armedTradeId, String venue, String symbol, Instant submittedAt )
    {
        return new OrderAttempt(
            null,
            null,
            armedTradeId,
            null,
            venue,
            symbol,
            TradeSide.SHORT,
            ExecutionType.MARKET,
            BigDecimal.valueOf( 25 ),
            null,
            OrderAttemptStatus.FAILED,
            null,
            null,
            null,
            submittedAt,
            null,
            "Missing engine credentials for bybit.",
            null,
            null,
            null,
            null,
            null
        );
    }

    private static OrderAttempt filledAttempt( Long armedTradeId, String venue, String symbol, Instant submittedAt )
    {
        return filledAttempt(
            armedTradeId,
            venue,
            symbol,
            TradeSide.SHORT,
            submittedAt,
            BigDecimal.valueOf( 2.5 ),
            BigDecimal.TEN,
            BigDecimal.valueOf( 0.01 ),
            submittedAt
        );
    }

    private static OrderAttempt filledAttemptWithoutExchangeTimestamp( Long armedTradeId, String venue, String symbol, Instant submittedAt )
    {
        return filledAttempt(
            armedTradeId,
            venue,
            symbol,
            TradeSide.SHORT,
            submittedAt,
            BigDecimal.valueOf( 2.5 ),
            null,
            null,
            null
        );
    }

    private static OrderAttempt acknowledgedAttempt( Long armedTradeId, String venue, String symbol, Instant submittedAt )
    {
        return new OrderAttempt(
            null,
            null,
            armedTradeId,
            null,
            venue,
            symbol,
            TradeSide.SHORT,
            ExecutionType.MARKET,
            BigDecimal.TEN,
            null,
            OrderAttemptStatus.ACKNOWLEDGED,
            "external-ack",
            null,
            null,
            submittedAt,
            submittedAt,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    private static OrderAttempt rejectedAttempt( Long armedTradeId, String venue, String symbol, Instant submittedAt )
    {
        return new OrderAttempt(
            null,
            null,
            armedTradeId,
            null,
            venue,
            symbol,
            TradeSide.LONG,
            ExecutionType.MARKET,
            BigDecimal.TEN,
            null,
            OrderAttemptStatus.REJECTED,
            "external-rejected",
            null,
            null,
            submittedAt,
            null,
            "exchange rejected",
            null,
            null,
            null,
            null,
            null
        );
    }

    private static OrderAttempt filledAttempt(
        Long armedTradeId,
        String venue,
        String symbol,
        TradeSide side,
        Instant submittedAt,
        BigDecimal averageFillPrice,
        BigDecimal filledQuantity,
        BigDecimal feeUsd,
        Instant exchangeTimestamp
    )
    {
        return new OrderAttempt(
            null,
            null,
            armedTradeId,
            null,
            venue,
            symbol,
            side,
            ExecutionType.MARKET,
            BigDecimal.TEN,
            null,
            OrderAttemptStatus.FILLED,
            "external-1",
            null,
            null,
            submittedAt,
            exchangeTimestamp,
            null,
            averageFillPrice,
            filledQuantity,
            feeUsd,
            null,
            null
        );
    }

    private static EngineOrderAttemptResponse recordedAttempt( EngineOrderAttemptRecordRequest request )
    {
        return new EngineOrderAttemptResponse(
            101L,
            request.attemptKey(),
            request.armedTradeId(),
            request.attemptNumber(),
            request.venue(),
            request.symbol(),
            request.side(),
            request.executionType(),
            request.quantity(),
            request.limitPrice(),
            request.status(),
            request.externalOrderId(),
            request.targetEntryAt(),
            request.triggerAt(),
            request.submittedAt(),
            request.exchangeTimestamp(),
            request.failureReason(),
            request.averageFillPrice(),
            request.filledQuantity(),
            request.feeUsd(),
            NOW,
            NOW
        );
    }

    private static EnginePositionResponse recordedPosition( EnginePositionRecordRequest request )
    {
        return new EnginePositionResponse(
            201L,
            request.armedTradeId(),
            request.venue(),
            request.symbol(),
            request.side(),
            request.quantity(),
            request.entryPrice(),
            request.exitPrice(),
            request.state(),
            request.openedAt(),
            request.closedAt(),
            NOW,
            NOW
        );
    }

    private static EngineTradeOutcomeResponse recordedOutcome( EngineTradeOutcomeRecordRequest request )
    {
        return new EngineTradeOutcomeResponse(
            301L,
            request.armedTradeId(),
            request.grossPnlUsd(),
            request.netPnlUsd(),
            request.feesUsd(),
            request.outcomeCode(),
            request.notes(),
            request.evaluatedAt(),
            NOW,
            NOW
        );
    }
}
