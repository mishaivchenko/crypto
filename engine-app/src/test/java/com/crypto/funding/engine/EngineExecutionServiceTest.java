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
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.TradeSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        when( executionPort.submitOrder( anyLong(), anyString(), anyString(), any( OrderIntent.class ) ) )
            .thenReturn( failedAttempt( 5L, "bybit", "REQ/USDT", NOW.minusSeconds( 1 ) ) );
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
        verify( executionPort, times( 1 ) ).submitOrder( anyLong(), anyString(), anyString(), any( OrderIntent.class ) );
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
        verify( executionPort, times( 3 ) ).submitOrder( anyLong(), anyString(), anyString(), any( OrderIntent.class ) );
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
        verify( executionPort, times( 3 ) ).submitOrder( anyLong(), anyString(), anyString(), any( OrderIntent.class ) );
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
        verify( executionPort, never() ).submitOrder( anyLong(), anyString(), anyString(), any( OrderIntent.class ) );
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
        verify( executionPort, never() ).submitOrder( anyLong(), anyString(), anyString(), any( OrderIntent.class ) );
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
        when( executionPort.submitOrder( anyLong(), anyString(), anyString(), any( OrderIntent.class ) ) )
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
            "summary"
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
            NOW,
            NOW
        );
    }
}
