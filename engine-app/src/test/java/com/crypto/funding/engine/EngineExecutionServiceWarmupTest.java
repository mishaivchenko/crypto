package com.crypto.funding.engine;

import com.crypto.funding.application.port.ExecutionPort;
import com.crypto.funding.contract.engine.EngineEntryAttemptPlan;
import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import com.crypto.funding.contract.engine.EngineLatencySampleRequest;
import com.crypto.funding.contract.engine.EngineOrderAttemptRecordRequest;
import com.crypto.funding.contract.engine.EngineOrderAttemptResponse;
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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EngineExecutionServiceWarmupTest
{
    private static final Instant NOW = Instant.parse( "2030-01-01T00:00:00Z" );

    private final EnginePlanClient client = mock( EnginePlanClient.class );
    private final ExecutionPort executionPort = mock( ExecutionPort.class );
    private final EngineTelemetryService telemetryService = new EngineTelemetryService();
    private final Clock clock = Clock.fixed( NOW, ZoneOffset.UTC );

    @SuppressWarnings("unchecked")
    private final HttpClient probeHttpClient = mock( HttpClient.class );

    private EngineExecutionService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception
    {
        service = new EngineExecutionService(
            client, executionPort, telemetryService, clock,
            advancingNanos( 0L, 5_000_000L ),
            probeHttpClient
        );
        when( client.recordOrderAttempt( any() ) ).thenAnswer( inv -> recordedAttempt( inv.getArgument( 0 ) ) );
        when( executionPort.submitOrder( any(), any( OrderIntent.class ), eq( false ) ) )
            .thenReturn( filledAttempt() );
        when( probeHttpClient.send( any( HttpRequest.class ), any() ) )
            .thenReturn( mock( HttpResponse.class ) );
    }

    // REQ: ENG-WRM-001 — pure unit: warmupBudgetMs
    @Test
    void warmupBudgetMs_halfOfLeadCappedAt250()
    {
        assertThat( EngineExecutionService.warmupBudgetMs( 500L ) ).isEqualTo( 250L );
        assertThat( EngineExecutionService.warmupBudgetMs( 100L ) ).isEqualTo( 50L );
        assertThat( EngineExecutionService.warmupBudgetMs( 600L ) ).isEqualTo( 250L );
        assertThat( EngineExecutionService.warmupBudgetMs( 0L ) ).isEqualTo( 0L );
    }

    // REQ: ENG-WRM-002 — pure unit: perProbeTimeoutMs
    @Test
    void perProbeTimeoutMs_derivedFromBudgetWithFloor10()
    {
        assertThat( EngineExecutionService.perProbeTimeoutMs( 30L, 3 ) ).isEqualTo( 10L );
        assertThat( EngineExecutionService.perProbeTimeoutMs( 90L, 3 ) ).isEqualTo( 30L );
        assertThat( EngineExecutionService.perProbeTimeoutMs( 5L, 3 ) ).isEqualTo( 10L );
        assertThat( EngineExecutionService.perProbeTimeoutMs( 250L, 5 ) ).isEqualTo( 50L );
    }

    // REQ: ENG-WRM-003 — pure unit: percentile nearest-rank
    @Test
    void percentile_nearestRankCorrect()
    {
        List<Long> sorted = List.of( 10L, 20L, 30L );
        assertThat( EngineExecutionService.percentile( sorted, 50 ) ).isEqualTo( 20L );
        assertThat( EngineExecutionService.percentile( sorted, 95 ) ).isEqualTo( 30L );
        assertThat( EngineExecutionService.percentile( sorted, 99 ) ).isEqualTo( 30L );
        assertThat( EngineExecutionService.percentile( sorted, 1 ) ).isEqualTo( 10L );
    }

    // REQ: ENG-WRM-003 — edge: single sample
    @Test
    void percentile_singleSampleAlwaysReturnsThatSample()
    {
        List<Long> sorted = List.of( 42L );
        assertThat( EngineExecutionService.percentile( sorted, 50 ) ).isEqualTo( 42L );
        assertThat( EngineExecutionService.percentile( sorted, 99 ) ).isEqualTo( 42L );
    }

    // REQ: ENG-WRM-004 — warm-up fires when in window and probeUrl is set
    @Test
    @SuppressWarnings("unchecked")
    void warmupRunsWhenInWindowWithProbeUrl() throws Exception
    {
        // targetEntry 250ms away; effectiveLatency 0 → calibratedTrigger = NOW+250ms (future) → in warmup window
        EngineExecutionPlan plan = planWithProbe( 1L, "https://api.example.com/time",
            3, 500L, 0L, 0L, 0L, NOW.plusMillis( 250 ) );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ) );

        service.runOnce( false );

        verify( probeHttpClient, times( 3 ) ).send( any( HttpRequest.class ), any() );
    }

    // REQ: ENG-WRM-005 — no probe when probeUrl is null → backward compat
    @Test
    void noProbeWhenProbeUrlNull() throws Exception
    {
        EngineExecutionPlan plan = planWithProbe( 1L, null, null, null,
            0L, 0L, 0L, NOW.plusMillis( 250 ) );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ) );

        service.runOnce( false );

        verify( probeHttpClient, never() ).send( any( HttpRequest.class ), any() );
    }

    // REQ: ENG-WRM-005 — no probe outside warmup window (trigger already in the past)
    @Test
    void noProbeOutsideWarmupWindow() throws Exception
    {
        // first calibratedTrigger = NOW - 600ms (already past warmup window)
        EngineExecutionPlan plan = planWithProbe( 1L, "https://api.example.com/time",
            3, 500L, 0L, 0L, 0L, NOW.minusMillis( 100 ) );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ) );

        service.runOnce( false );

        verify( probeHttpClient, never() ).send( any( HttpRequest.class ), any() );
    }

    // REQ: ENG-WRM-006 — warm-up idempotent: not repeated for same tradeId in same service instance
    @Test
    @SuppressWarnings("unchecked")
    void warmupNotRepeatedForSameTradeId() throws Exception
    {
        EngineExecutionPlan plan = planWithProbe( 1L, "https://api.example.com/time",
            3, 500L, 0L, 0L, 0L, NOW.plusMillis( 250 ) );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ) );

        service.runOnce( false );
        service.runOnce( false );

        verify( probeHttpClient, times( 3 ) ).send( any( HttpRequest.class ), any() );
    }

    // REQ: ENG-WRM-007 — all probes timeout → fallbackUsed, execution still proceeds
    @Test
    void allProbesTimeoutFallsBackAndExecutionProceeds() throws Exception
    {
        // effectiveLatency = 200ms, targetEntry = NOW + 100ms → calibratedTrigger = NOW - 100ms (past) → should execute
        EngineExecutionPlan plan = planWithProbe( 1L, "https://api.example.com/time",
            3, 500L, 200L, 0L, 200L, NOW.plusMillis( 100 ) );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ) );
        when( probeHttpClient.send( any( HttpRequest.class ), any() ) )
            .thenThrow( new java.io.IOException( "timeout" ) );

        var response = service.runOnce( false );

        assertThat( response.attemptsSubmitted() ).isEqualTo( 1 );
        verify( executionPort, times( 1 ) ).submitOrder( eq( plan ), any( OrderIntent.class ), eq( false ) );
    }

    // REQ: ENG-WRM-007 — execution proceeds when all probes throw (no hang, no missed trigger)
    @Test
    void executionProceedsWhenAllProbesThrow() throws Exception
    {
        // target past → execution window; all probes throw → fallback, still executes
        EngineExecutionPlan plan = planWithProbe( 1L, "https://api.example.com/time",
            3, 500L, 100L, 0L, 100L, NOW.minusMillis( 50 ) );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ) );
        when( probeHttpClient.send( any( HttpRequest.class ), any() ) )
            .thenThrow( new java.io.IOException( "timeout" ) );

        var response = service.runOnce( false );

        assertThat( response.attemptsSubmitted() ).isEqualTo( 1 );
    }

    // REQ: ENG-WRM-008 — force=true skips warm-up phase
    @Test
    @SuppressWarnings("unchecked")
    void warmupSkippedWhenForced() throws Exception
    {
        EngineExecutionPlan plan = planWithProbe( 1L, "https://api.example.com/time",
            3, 500L, 0L, 0L, 0L, NOW.plusMillis( 250 ) );
        when( client.listPlans( true ) ).thenReturn( List.of( plan ) );

        service.runOnce( true );

        verify( probeHttpClient, never() ).send( any( HttpRequest.class ), any() );
    }

    // REQ: ENG-WRM-009 — terminal trade state (CLOSED) removes warmup entry from map
    @Test
    @SuppressWarnings("unchecked")
    void terminalStateRemovesWarmupEntryAndAllowsRedo() throws Exception
    {
        EngineExecutionPlan openPlan = planWithProbe( 1L, "https://api.example.com/time",
            3, 500L, 0L, 0L, 0L, NOW.plusMillis( 250 ) );
        EngineExecutionPlan closedPlan = planWithProbeAndState( 1L, "https://api.example.com/time",
            3, 500L, 0L, 0L, 0L, NOW.plusMillis( 250 ), ArmedTradeState.CLOSED );
        EngineExecutionPlan openPlan2 = planWithProbe( 1L, "https://api.example.com/time",
            3, 500L, 0L, 0L, 0L, NOW.plusMillis( 250 ) );

        when( client.listPlans( false ) )
            .thenReturn( List.of( openPlan ) )
            .thenReturn( List.of( closedPlan ) )
            .thenReturn( List.of( openPlan2 ) );

        service.runOnce( false );   // warmup fires: 3 probes
        service.runOnce( false );   // closed: clears warmup entry (no probes — not in window for ENTRY_WINDOW)
        service.runOnce( false );   // open again: warmup fires again: 3 more probes

        verify( probeHttpClient, times( 6 ) ).send( any(), any() );
    }

    // REQ: ENG-WRM-010 — calibratedTrigger uses plan effectiveLatencyMs when no warmup
    @Test
    void calibratedTriggerFallsBackToPlanLatencyWhenNoWarmup()
    {
        // No probe URL → no warmup, uses plan.effectiveLatencyMs = 200ms
        // targetEntry = NOW + 100ms, effectiveLatency = 200ms → calibratedTrigger = NOW - 100ms → past → executes
        EngineExecutionPlan plan = planWithProbe( 1L, null, null, null,
            200L, 0L, 200L, NOW.plusMillis( 100 ) );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ) );

        var response = service.runOnce( false );

        assertThat( response.attemptsSubmitted() ).isEqualTo( 1 );
    }

    // REQ: ENG-WRM-010 — calibratedTrigger treats null effectiveLatencyMs as 0
    @Test
    void calibratedTriggerTreatsNullEffectiveLatencyAsZero()
    {
        // No probe URL → no warmup; effectiveLatencyMs = null → treated as 0
        // targetEntry = NOW + 100ms → calibratedTrigger = NOW + 100ms (future) → skip
        EngineExecutionPlan plan = planWithProbe( 1L, null, null, null,
            null, null, null, NOW.plusMillis( 100 ) );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ) );

        var response = service.runOnce( false );

        assertThat( response.attemptsSubmitted() ).isZero();
    }

    // REQ: ENG-WRM-011 — future calibrated trigger is skipped even with no warmup
    @Test
    void futureCalibrateTriggerSkipsExecution()
    {
        // targetEntry = NOW + 2000ms, effectiveLatency = 100ms → calibratedTrigger = NOW + 1900ms → future → skip
        EngineExecutionPlan plan = planWithProbe( 1L, null, null, null,
            100L, 0L, 100L, NOW.plusMillis( 2000 ) );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ) );

        var response = service.runOnce( false );

        assertThat( response.attemptsSubmitted() ).isZero();
        assertThat( response.attemptsSkipped() ).isEqualTo( 1 );
    }

    // REQ: ENG-WRM-012 — warmup probe reports latency sample to monitor
    @Test
    @SuppressWarnings("unchecked")
    void warmupProbeRecordsLatencySampleWithWarmupOperation() throws Exception
    {
        EngineExecutionPlan plan = planWithProbe( 1L, "https://api.example.com/time",
            3, 500L, 0L, 0L, 0L, NOW.plusMillis( 250 ) );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ) );

        service.runOnce( false );

        ArgumentCaptor<EngineLatencySampleRequest> captor =
            ArgumentCaptor.forClass( EngineLatencySampleRequest.class );
        verify( client, times( 1 ) ).recordLatencySample( captor.capture() );
        EngineLatencySampleRequest sample = captor.getValue();
        assertThat( sample.operation() ).isEqualTo( "warmup-probe" );
        assertThat( sample.symbol() ).isEqualTo( "_all_" );
    }

    // REQ: ENG-WRM-013 — warmup window uses effectiveLatencyMs=null as 0 to compute firstTrigger
    @Test
    @SuppressWarnings("unchecked")
    void warmupWindowComputedWithNullEffectiveLatency() throws Exception
    {
        // effectiveLatencyMs=null → treated as 0; firstTrigger = targetEntry - 0 = targetEntry
        // NOW is 250ms before targetEntry → inside warmup window (lead=500) → probes fire
        EngineExecutionPlan plan = planWithProbe( 1L, "https://api.example.com/time",
            3, 500L, null, null, null, NOW.plusMillis( 250 ) );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ) );

        service.runOnce( false );

        verify( probeHttpClient, times( 3 ) ).send( any( HttpRequest.class ), any() );
    }

    // REQ: ENG-WRM-013 — warmup uses default probeLeadMs=500 when null
    @Test
    @SuppressWarnings("unchecked")
    void warmupWindowUsesDefault500LeadWhenNull() throws Exception
    {
        // warmupProbeLeadMs=null → default 500ms; targetEntry 250ms away → in window
        EngineExecutionPlan plan = planWithProbe( 1L, "https://api.example.com/time",
            3, null, 0L, 0L, 0L, NOW.plusMillis( 250 ) );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ) );

        service.runOnce( false );

        verify( probeHttpClient, times( 3 ) ).send( any( HttpRequest.class ), any() );
    }

    // REQ: ENG-WRM-013 — executeWarmupProbes uses default count=3 when warmupProbeCount null
    @Test
    @SuppressWarnings("unchecked")
    void warmupProbeCountDefaultsTo3WhenNull() throws Exception
    {
        // warmupProbeCount=null → default 3 probes
        EngineExecutionPlan plan = planWithProbe( 1L, "https://api.example.com/time",
            null, 500L, 0L, 0L, 0L, NOW.plusMillis( 250 ) );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ) );

        service.runOnce( false );

        verify( probeHttpClient, times( 3 ) ).send( any( HttpRequest.class ), any() );
    }

    // REQ: ENG-WRM-013 — budgetDeadline uses budget when it is before firstTrigger
    @Test
    @SuppressWarnings("unchecked")
    void budgetDeadlineCappedAtFirstTrigger() throws Exception
    {
        // warmupProbeLeadMs=600; targetEntry=NOW+400ms; firstTrigger=NOW+400ms
        // NOW is 400ms before firstTrigger → inside warmup window (lead=600 → window starts at NOW-200ms)
        // budget=min(300,250)=250ms; NOW+250ms < NOW+400ms → deadline=NOW+250ms
        EngineExecutionPlan plan = planWithProbe( 1L, "https://api.example.com/time",
            3, 600L, 0L, 0L, 0L, NOW.plusMillis( 400 ) );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ) );

        service.runOnce( false );

        verify( probeHttpClient, times( 3 ) ).send( any( HttpRequest.class ), any() );
    }

    // REQ: ENG-WRM-013 — timing sample value reflects subtraction (not addition) of timestamps
    @Test
    @SuppressWarnings("unchecked")
    void probeTimingSampleIsPositiveNotNegative() throws Exception
    {
        // nanoTime advances by 5ms per call → sample = 5ms (not -(5ms) from subtraction)
        EngineExecutionPlan plan = planWithProbe( 1L, "https://api.example.com/time",
            1, 500L, 0L, 0L, 0L, NOW.plusMillis( 250 ) );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ) );

        service.runOnce( false );

        ArgumentCaptor<EngineLatencySampleRequest> captor =
            ArgumentCaptor.forClass( EngineLatencySampleRequest.class );
        verify( client ).recordLatencySample( captor.capture() );
        assertThat( captor.getValue().durationMs() ).isGreaterThanOrEqualTo( 0L );
    }

    // REQ: ENG-WRM-014 — buildCalibration with empty samples uses plan effectiveEntryLatencyMs as fallback
    @Test
    void buildCalibrationEmptySamplesUsesFallbackEffectiveLatency() throws Exception
    {
        // all probes throw → empty samples → fallbackUsed=true, calibratedLatency = effectiveLatencyMs
        // effectiveLatency=80ms; targetEntry=NOW+50ms → calibratedTrigger = NOW-30ms (past) → execution fires
        EngineExecutionPlan plan = planWithProbe( 1L, "https://api.example.com/time",
            3, 500L, 80L, 0L, 80L, NOW.plusMillis( 50 ) );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ) );
        when( probeHttpClient.send( any( HttpRequest.class ), any() ) )
            .thenThrow( new java.io.IOException( "timeout" ) );

        var response = service.runOnce( false );

        assertThat( response.attemptsSubmitted() ).isEqualTo( 1 );
    }

    // REQ: ENG-WRM-014 — buildCalibration with empty samples uses 0 when effectiveEntryLatencyMs is null
    @Test
    void buildCalibrationEmptySamplesUsesFallback0WhenEffectiveLatencyNull() throws Exception
    {
        // effectiveLatencyMs=null → fallback=0; targetEntry=NOW+100ms → calibratedTrigger=NOW+100ms (future) → skip
        EngineExecutionPlan plan = planWithProbe( 1L, "https://api.example.com/time",
            3, 500L, null, null, null, NOW.plusMillis( 100 ) );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ) );
        when( probeHttpClient.send( any( HttpRequest.class ), any() ) )
            .thenThrow( new java.io.IOException( "timeout" ) );

        var response = service.runOnce( false );

        assertThat( response.attemptsSubmitted() ).isZero();
    }

    // REQ: ENG-WRM-015 — buildCalibration adds p50+manualLatency for calibratedEffectiveLatencyMs
    @Test
    @SuppressWarnings("unchecked")
    void buildCalibrationAddsManualLatencyToP50() throws Exception
    {
        // nanoTimeSupplier advances 5ms per call → measured ~5ms; manualLatencyMs=10ms
        // calibratedEffectiveLatency = p50 + manual = ~5ms + 10ms = ~15ms
        // targetEntry=NOW+300ms; calibratedTrigger=NOW+300-15=NOW+285ms (future) → skip execution
        EngineExecutionPlan plan = planWithProbe( 1L, "https://api.example.com/time",
            1, 500L, 5L, 10L, 5L, NOW.plusMillis( 300 ) );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ) );

        var response = service.runOnce( false );

        assertThat( response.attemptsSubmitted() ).isZero();
    }

    // REQ: ENG-WRM-015 — buildCalibration uses 0 for null manualLatencyAdjustmentMs
    @Test
    @SuppressWarnings("unchecked")
    void buildCalibrationTreatsNullManualLatencyAsZero() throws Exception
    {
        // manualLatencyMs=null → treated as 0; calibrated = p50 + 0 = p50
        // measuredLatency=5ms (from nanoTime); targetEntry=NOW+250ms; in warmup window
        EngineExecutionPlan plan = planWithProbe( 1L, "https://api.example.com/time",
            1, 500L, 5L, null, 5L, NOW.plusMillis( 250 ) );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ) );

        // Just verify probe fires and no exception from null manual latency
        service.runOnce( false );

        verify( probeHttpClient, times( 1 ) ).send( any( HttpRequest.class ), any() );
    }

    // REQ: ENG-WRM-016 — budgetDeadline caps probes to budget (not trigger) when budget < timeToTrigger
    @Test
    @SuppressWarnings("unchecked")
    void budgetDeadlineStopsLoopWhenExhausted() throws Exception
    {
        // Advancing clock: each call to Instant.now(clock) returns NOW + n*100ms.
        // runOnce calls now() at ~line 116 (startedAt) and ~line 177 (warmup now → startNow).
        // executeWarmupProbes loop checks now() each iteration.
        // With budget=50ms: budgetDeadline = startNow + 50ms.
        // By the first budget check in the loop, clock has advanced well past the deadline → break immediately.
        AtomicLong clockTick = new AtomicLong( NOW.toEpochMilli() );
        Clock advancingClock = new Clock()
        {
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone( java.time.ZoneId zone ) { return this; }
            public Instant instant() { return Instant.ofEpochMilli( clockTick.getAndAdd( 100 ) ); }
        };
        EngineExecutionService svc = new EngineExecutionService(
            client, executionPort, telemetryService, advancingClock,
            advancingNanos( 0L, 5_000_000L ),
            probeHttpClient
        );
        // lead=500, targetEntry=NOW+400ms; budget=250ms; startNow=~NOW+100ms; deadline=~NOW+350ms
        // Clock in loop = NOW+200ms, NOW+300ms, etc.; within deadline → probes may fire, but that's OK
        // Key test: if budget << timeToTrigger and clock advances fast, verify 0 probes due to deadline exhaustion
        // Use very small lead=50 → budget=25ms; targetEntry=NOW+200ms; firstTrigger=NOW+200ms
        // startNow≈NOW+100ms; deadline=NOW+125ms; loop clock≈NOW+200ms → past deadline → break → 0 probes
        EngineExecutionPlan plan = planWithProbeAndState( 99L, "https://api.example.com/time",
            3, 50L, 200L, 0L, 200L, NOW.plusMillis( 200 ), ArmedTradeState.ARMED );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ) );

        svc.runOnce( false );

        // Budget of 25ms is immediately exhausted when clock advances by 100ms per call → 0 probes
        verify( probeHttpClient, never() ).send( any( HttpRequest.class ), any() );
    }

    // REQ: ENG-WRM-016 — deadline uses firstTrigger when budget exceeds time-to-trigger
    @Test
    @SuppressWarnings("unchecked")
    void budgetDeadlineFallsBackToFirstTriggerWhenBudgetExceedsTrigger() throws Exception
    {
        // startNow=NOW+100ms (clock advances 100ms/call), budget=250ms → startNow+budget=NOW+350ms
        // firstTrigger=NOW+150ms (effectiveLatency=0, targetEntry=NOW+150ms)
        // startNow+budget (NOW+350ms) is NOT before firstTrigger (NOW+150ms) → correct: deadline=firstTrigger=NOW+150ms
        // mutation: deadline=startNow+budget=NOW+350ms (extends past trigger)
        // With clock advancing 100ms/step: loop check returns NOW+200ms > NOW+150ms → break (correct)
        // With mutation: deadline=NOW+350ms; loop check returns NOW+200ms < NOW+350ms → probe fires (mutation detected!)
        AtomicLong clockTick = new AtomicLong( NOW.toEpochMilli() );
        Clock advancingClock = new Clock()
        {
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone( java.time.ZoneId zone ) { return this; }
            public Instant instant() { return Instant.ofEpochMilli( clockTick.getAndAdd( 100 ) ); }
        };
        EngineExecutionService svc = new EngineExecutionService(
            client, executionPort, telemetryService, advancingClock,
            advancingNanos( 0L, 5_000_000L ),
            probeHttpClient
        );
        // targetEntry is close: plan status ENTRY_WINDOW, effectiveLatency=0
        // firstTrigger ≈ NOW+150ms; startNow≈NOW+100ms; budget=250ms → startNow+budget > firstTrigger
        EngineExecutionPlan plan = planWithProbeAndState( 77L, "https://api.example.com/time",
            3, 500L, 0L, 0L, 0L, NOW.plusMillis( 150 ), ArmedTradeState.ARMED );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ) );

        svc.runOnce( false );

        // Correct: deadline=firstTrigger=NOW+150ms; loop clock≈NOW+200ms > deadline → 0 probes
        // Mutation: deadline=NOW+350ms; loop clock≈NOW+200ms < deadline → 1+ probes
        verify( probeHttpClient, never() ).send( any( HttpRequest.class ), any() );
    }

    // REQ: ENG-WRM-016 — timing subtraction (not addition) computes probe duration
    @Test
    @SuppressWarnings("unchecked")
    void probeTimingSampleReflectsSubtractionNotAddition() throws Exception
    {
        // nanoTime starts at 10ms, step=5ms: start=10ms, end=15ms
        // correct: (15-10)/1 = 5ms; mutation (addition): (15+10)/1 = 25ms
        EngineExecutionService svc = new EngineExecutionService(
            client, executionPort, telemetryService, clock,
            advancingNanos( 10_000_000L, 5_000_000L ),
            probeHttpClient
        );
        EngineExecutionPlan plan = planWithProbe( 1L, "https://api.example.com/time",
            1, 500L, 0L, 0L, 0L, NOW.plusMillis( 250 ) );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ) );

        svc.runOnce( false );

        ArgumentCaptor<EngineLatencySampleRequest> captor =
            ArgumentCaptor.forClass( EngineLatencySampleRequest.class );
        verify( client ).recordLatencySample( captor.capture() );
        // p50 should be 5ms (from subtraction), not 25ms (from addition) — also not 0 or negative
        assertThat( captor.getValue().durationMs() ).isEqualTo( 5L );
    }

    // REQ: ENG-WRM-017 — calibration adds (not subtracts) manual latency to p50
    @Test
    @SuppressWarnings("unchecked")
    void calibrationAddsManualLatencyToP50ForCalibratedTrigger() throws Exception
    {
        // p50 ≈ 5ms (from advancing nanos), manual = 200ms
        // correct: calibrated = 5 + 200 = 205ms → trigger = targetEntry - 205ms
        // mutation (subtraction): calibrated = max(0, 5 - 200) = 0ms → trigger = targetEntry
        // targetEntry = NOW + 100ms:
        //   correct calibrated=205ms: trigger = NOW+100-205 = NOW-105ms (past) → executes
        //   mutation calibrated=0ms: trigger = NOW+100ms (future) → skips
        EngineExecutionService svc = new EngineExecutionService(
            client, executionPort, telemetryService, clock,
            advancingNanos( 10_000_000L, 5_000_000L ),
            probeHttpClient
        );
        EngineExecutionPlan plan = planWithProbe( 1L, "https://api.example.com/time",
            1, 500L, 0L, 200L, 0L, NOW.plusMillis( 100 ) );
        when( client.listPlans( false ) ).thenReturn( List.of( plan ) );

        var response = svc.runOnce( false );

        assertThat( response.attemptsSubmitted() ).isEqualTo( 1 );
    }

    private static EngineExecutionPlan planWithProbe(
        Long armedTradeId,
        String probeUrl,
        Integer warmupProbeCount,
        Long warmupProbeLeadMs,
        Long measuredLatencyMs,
        Long manualLatencyMs,
        Long effectiveLatencyMs,
        Instant targetEntry
    )
    {
        return planWithProbeAndState( armedTradeId, probeUrl, warmupProbeCount, warmupProbeLeadMs,
            measuredLatencyMs, manualLatencyMs, effectiveLatencyMs, targetEntry, ArmedTradeState.ARMED );
    }

    private static EngineExecutionPlan planWithProbeAndState(
        Long armedTradeId,
        String probeUrl,
        Integer warmupProbeCount,
        Long warmupProbeLeadMs,
        Long measuredLatencyMs,
        Long manualLatencyMs,
        Long effectiveLatencyMs,
        Instant targetEntry,
        ArmedTradeState tradeState
    )
    {
        long eff = effectiveLatencyMs != null ? effectiveLatencyMs : 0L;
        long meas = measuredLatencyMs != null ? measuredLatencyMs : 0L;
        long manual = manualLatencyMs != null ? manualLatencyMs : 0L;
        Instant triggerAt = targetEntry.minusMillis( eff );
        return new EngineExecutionPlan(
            armedTradeId,
            13L,
            "bybit",
            "REQ/USDT",
            TradeSide.SHORT,
            BigDecimal.valueOf( 25 ),
            tradeState,
            NOW.plusSeconds( 60 ),
            targetEntry,
            NOW.plusSeconds( 300 ),
            1,
            150L,
            measuredLatencyMs,
            manualLatencyMs,
            effectiveLatencyMs,
            List.of( new EngineEntryAttemptPlan( 1, targetEntry, triggerAt, eff, 0L, effectiveLatencyMs ) ),
            EnginePlanStatus.ENTRY_WINDOW,
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
            null,
            null,
            null,
            probeUrl,
            warmupProbeCount,
            warmupProbeLeadMs
        );
    }

    private static OrderAttempt filledAttempt()
    {
        return new OrderAttempt(
            null,
            null,
            1L,
            null,
            "bybit",
            "REQ/USDT",
            TradeSide.SHORT,
            ExecutionType.MARKET,
            BigDecimal.TEN,
            null,
            OrderAttemptStatus.FILLED,
            "ext-order-1",
            null,
            null,
            NOW,
            NOW,
            null,
            BigDecimal.valueOf( 2.5 ),
            BigDecimal.TEN,
            BigDecimal.ZERO,
            null,
            null,
            null
        );
    }

    private static EngineOrderAttemptResponse recordedAttempt( EngineOrderAttemptRecordRequest req )
    {
        return new EngineOrderAttemptResponse(
            101L,
            req.attemptKey(),
            req.armedTradeId(),
            req.attemptNumber(),
            req.venue(),
            req.symbol(),
            req.side(),
            req.executionType(),
            req.quantity(),
            req.limitPrice(),
            req.status(),
            req.externalOrderId(),
            req.targetEntryAt(),
            req.triggerAt(),
            req.submittedAt(),
            req.exchangeTimestamp(),
            req.failureReason(),
            req.averageFillPrice(),
            req.filledQuantity(),
            req.feeUsd(),
            req.requestDurationMs(),
            NOW,
            NOW
        );
    }

    private static LongSupplier advancingNanos( long initialNs, long stepNs )
    {
        AtomicLong counter = new AtomicLong( initialNs );
        return () -> counter.getAndAdd( stepNs );
    }
}
