package com.crypto.funding.engine;

import com.crypto.funding.application.port.ExecutionPort;
import com.crypto.funding.contract.engine.EngineEntryAttemptPlan;
import com.crypto.funding.contract.engine.EngineExecutionAttemptResult;
import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.contract.engine.EngineExecutionRunResponse;
import com.crypto.funding.contract.engine.MarkPriceResponse;
import com.crypto.funding.contract.engine.EngineLatencySampleRequest;
import com.crypto.funding.contract.engine.EngineOrderAttemptRecordRequest;
import com.crypto.funding.contract.engine.EngineOrderAttemptResponse;
import com.crypto.funding.contract.engine.EngineExecutionTargetPhase;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import com.crypto.funding.contract.engine.EnginePositionRecordRequest;
import com.crypto.funding.contract.engine.EngineTradeOutcomeRecordRequest;
import com.crypto.funding.contract.engine.EngineTradeStateUpdateRequest;
import com.crypto.funding.domain.execution.ExecutionType;
import com.crypto.funding.domain.execution.OrderAttempt;
import com.crypto.funding.domain.execution.OrderAttemptStatus;
import com.crypto.funding.domain.execution.OrderIntent;
import com.crypto.funding.domain.trade.ArmedTradeState;
import com.crypto.funding.domain.trade.PositionState;
import com.crypto.funding.domain.trade.TradeSide;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

@Service
public class EngineExecutionService
{
    private static final Logger log = LoggerFactory.getLogger( EngineExecutionService.class );

    record WarmupCalibration(
        long p50Ms,
        long p95Ms,
        long p99Ms,
        int sampleCount,
        boolean fallbackUsed,
        long calibratedEffectiveLatencyMs,
        Instant doneAt
    )
    {
    }

    private final EnginePlanClient client;
    private final ExecutionPort executionPort;
    private final EngineTelemetryService telemetryService;
    private final Clock clock;
    private final LongSupplier nanoTimeSupplier;
    private final HttpClient probeHttpClient;
    private final Set<String> submittedAttemptKeys = ConcurrentHashMap.newKeySet();
    private final Map<Long, WarmupCalibration> warmupByTrade = new ConcurrentHashMap<>();

    @Autowired
    public EngineExecutionService( EnginePlanClient client, ExecutionPort executionPort, EngineTelemetryService telemetryService )
    {
        this( client, executionPort, telemetryService, Clock.systemUTC(), System::nanoTime,
              HttpClient.newBuilder().build() );
    }

    EngineExecutionService( EnginePlanClient client, ExecutionPort executionPort, EngineTelemetryService telemetryService, Clock clock )
    {
        this( client, executionPort, telemetryService, clock, System::nanoTime,
              HttpClient.newBuilder().build() );
    }

    EngineExecutionService(
        EnginePlanClient client,
        ExecutionPort executionPort,
        EngineTelemetryService telemetryService,
        Clock clock,
        LongSupplier nanoTimeSupplier
    )
    {
        this( client, executionPort, telemetryService, clock, nanoTimeSupplier,
              HttpClient.newBuilder().build() );
    }

    EngineExecutionService(
        EnginePlanClient client,
        ExecutionPort executionPort,
        EngineTelemetryService telemetryService,
        Clock clock,
        LongSupplier nanoTimeSupplier,
        HttpClient probeHttpClient
    )
    {
        this.client = client;
        this.executionPort = executionPort;
        this.telemetryService = telemetryService;
        this.clock = clock;
        this.nanoTimeSupplier = nanoTimeSupplier;
        this.probeHttpClient = probeHttpClient;
    }

    public EngineExecutionRunResponse runOnce( boolean force )
    {
        Instant startedAt = Instant.now( clock );
        int plansScanned = 0;
        int attemptsSubmitted = 0;
        int skipped = 0;
        try
        {
            List<EngineExecutionPlan> plans = client.listPlans( force );
            plansScanned = plans.size();
            List<EngineExecutionAttemptResult> results = new ArrayList<>();

            for( EngineExecutionPlan plan : plans )
            {
                if( plan != null && plan.status() == EnginePlanStatus.WAITING_EXIT && !force && shouldEarlyExit( plan ) )
                {
                    String exitAttemptKey = exitAttemptKey( plan );
                    if( reserveAttemptKey( exitAttemptKey ) )
                    {
                        try
                        {
                            results.add( executeExit( plan ) );
                        }
                        catch( Exception e )
                        {
                            results.add( isolatedFailure( plan.armedTradeId(), null, exitAttemptKey, e ) );
                        }
                    }
                    else
                    {
                        skipped++;
                    }
                    continue;
                }
                if( !shouldProcessPlan( plan, force ) )
                {
                    skipped++;
                    continue;
                }
                if( plan.status() == EnginePlanStatus.EXIT_WINDOW && !force )
                {
                    String exitAttemptKey = exitAttemptKey( plan );
                    if( !reserveAttemptKey( exitAttemptKey ) )
                    {
                        skipped++;
                        continue;
                    }
                    try
                    {
                        results.add( executeExit( plan ) );
                    }
                    catch( Exception e )
                    {
                        results.add( isolatedFailure( plan.armedTradeId(), null, exitAttemptKey, e ) );
                    }
                    continue;
                }
                // Warm-up probe phase: run before the first entry trigger window
                if( plan.probeUrl() != null && !force )
                {
                    Instant firstTrigger = plan.entryAttempts().get( 0 ).targetEntryAt()
                                               .minusMillis( plan.effectiveEntryLatencyMs() != null ? plan.effectiveEntryLatencyMs() : 0L );
                    long lead = plan.warmupProbeLeadMs() != null ? plan.warmupProbeLeadMs() : 500L;
                    Instant now = Instant.now( clock );
                    boolean inWarmupWindow = now.isAfter( firstTrigger.minusMillis( lead ) ) && now.isBefore( firstTrigger );
                    if( inWarmupWindow && !warmupByTrade.containsKey( plan.armedTradeId() ) )
                    {
                        WarmupCalibration cal = executeWarmupProbes( plan, firstTrigger, now );
                        warmupByTrade.put( plan.armedTradeId(), cal );
                        reportWarmupSamples( plan, cal );
                        log.info( "warm-up done tradeId={} venue={} sampleCount={} p50={}ms p95={}ms p99={}ms budgetMs={} fallback={} calibratedTrigger={}",
                            plan.armedTradeId(), plan.venue(), cal.sampleCount(),
                            cal.p50Ms(), cal.p95Ms(), cal.p99Ms(),
                            warmupBudgetMs( lead ), cal.fallbackUsed(),
                            plan.entryAttempts().get( 0 ).targetEntryAt().minusMillis( cal.calibratedEffectiveLatencyMs() ) );
                    }
                }

                // Clean up warm-up state for terminal plans
                if( plan.tradeState() == ArmedTradeState.CLOSED
                    || plan.tradeState() == ArmedTradeState.CANCELLED
                    || plan.tradeState() == ArmedTradeState.FAILED )
                {
                    warmupByTrade.remove( plan.armedTradeId() );
                }

                for( EngineEntryAttemptPlan attemptPlan : plan.entryAttempts() )
                {
                    if( !force && calibratedTrigger( plan, attemptPlan ).isAfter( Instant.now( clock ) ) )
                    {
                        skipped++;
                        continue;
                    }
                    String attemptKey = attemptKey( plan, attemptPlan );
                    if( !reserveAttemptKey( attemptKey ) )
                    {
                        skipped++;
                        continue;
                    }
                    try
                    {
                        EngineExecutionAttemptResult result = executeEntryAttempt( plan, attemptPlan );
                        results.add( result );
                    }
                    catch( Exception e )
                    {
                        results.add( isolatedFailure( plan.armedTradeId(), attemptPlan.attemptNumber(), attemptKey, e ) );
                    }
                }
            }

            attemptsSubmitted = results.size();
            Instant finishedAt = Instant.now( clock );
            telemetryService.recordExecutionRun(
                force,
                startedAt,
                finishedAt,
                plansScanned,
                attemptsSubmitted,
                skipped,
                java.time.Duration.between( startedAt, finishedAt ).toMillis()
            );

            return new EngineExecutionRunResponse(
                startedAt,
                finishedAt,
                force,
                plansScanned,
                attemptsSubmitted,
                skipped,
                results
            );
        }
        finally
        {
            // telemetry is recorded on the happy path with run details; failures still bubble up to the caller.
        }
    }

    public EngineExecutionRunResponse runTarget( Long armedTradeId, EngineExecutionTargetPhase phase, boolean force )
    {
        Instant startedAt = Instant.now( clock );
        List<EngineExecutionAttemptResult> results = new ArrayList<>();
        int skipped = 0;
        if( armedTradeId == null || phase == null )
        {
            skipped++;
        }
        else
        {
            EngineExecutionPlan plan = client.getPlan( armedTradeId );
            if( phase == EngineExecutionTargetPhase.EXIT )
            {
                if( shouldProcessTargetExit( plan, force ) )
                {
                    String exitAttemptKey = exitAttemptKey( plan );
                    if( reserveAttemptKey( exitAttemptKey ) )
                    {
                        try
                        {
                            results.add( executeExit( plan ) );
                        }
                        catch( Exception e )
                        {
                            results.add( isolatedFailure( plan.armedTradeId(), null, exitAttemptKey, e ) );
                        }
                    }
                    else
                    {
                        skipped++;
                    }
                }
                else
                {
                    skipped++;
                }
            }
            else
            {
                EngineEntryAttemptPlan attemptPlan = firstTargetEntryAttempt( plan );
                if( shouldProcessTargetEntry( plan, attemptPlan, force ) )
                {
                    String attemptKey = attemptKey( plan, attemptPlan );
                    if( reserveAttemptKey( attemptKey ) )
                    {
                        try
                        {
                            results.add( executeEntryAttempt( plan, attemptPlan ) );
                        }
                        catch( Exception e )
                        {
                            results.add( isolatedFailure( plan.armedTradeId(), attemptPlan.attemptNumber(), attemptKey, e ) );
                        }
                    }
                    else
                    {
                        skipped++;
                    }
                }
                else
                {
                    skipped++;
                }
            }
        }

        Instant finishedAt = Instant.now( clock );
        telemetryService.recordExecutionRun(
            force,
            startedAt,
            finishedAt,
            1,
            results.size(),
            skipped,
            java.time.Duration.between( startedAt, finishedAt ).toMillis()
        );
        return new EngineExecutionRunResponse(
            startedAt,
            finishedAt,
            force,
            1,
            results.size(),
            skipped,
            results
        );
    }

    private EngineExecutionAttemptResult executeEntryAttempt( EngineExecutionPlan plan, EngineEntryAttemptPlan attemptPlan )
    {
        OrderIntent intent = new OrderIntent(
            plan.intendedSide(),
            ExecutionType.MARKET,
            plan.notionalUsd(),
            null,
            attemptPlan.triggerAt()
        );
        long submitStartedAt = nanoTimeSupplier.getAsLong();
        Instant submittedAt = Instant.now( clock );
        OrderAttempt attempt = executionPort.submitOrder( plan, intent, false );
        long submitDurationMs = ( nanoTimeSupplier.getAsLong() - submitStartedAt ) / 1_000_000L;
        telemetryService.recordOrderSubmission( plan.venue(), attempt.status(), submitDurationMs );
        reportLatencySample( plan.venue(), plan.symbol(), submitDurationMs, submittedAt );
        String attemptKey = attemptKey( plan, attemptPlan );
        EngineOrderAttemptResponse recorded = recordAttempt(
            attemptKey,
            attemptPlan.attemptNumber(),
            attemptPlan.targetEntryAt(),
            attemptPlan.triggerAt(),
            plan,
            attempt,
            submitDurationMs
        );
        applyEntryLifecycle( plan, recorded, attempt );

        return toResult( recorded );
    }

    private EngineExecutionAttemptResult executeExit( EngineExecutionPlan plan )
    {
        Instant triggerAt = Instant.now( clock );
        String attemptKey = exitAttemptKey( plan );
        OrderIntent intent = new OrderIntent(
            opposite( plan.intendedSide() ),
            ExecutionType.MARKET,
            plan.positionQuantity(),
            null,
            triggerAt
        );
        long submitStartedAt = nanoTimeSupplier.getAsLong();
        Instant submittedAt = Instant.now( clock );
        OrderAttempt attempt = executionPort.submitOrder( plan, intent, true );
        long submitDurationMs = ( nanoTimeSupplier.getAsLong() - submitStartedAt ) / 1_000_000L;
        telemetryService.recordOrderSubmission( plan.venue(), attempt.status(), submitDurationMs );
        reportLatencySample( plan.venue(), plan.symbol(), submitDurationMs, submittedAt );
        EngineOrderAttemptResponse recorded = recordAttempt(
            attemptKey,
            null,
            plan.plannedExitAt(),
            triggerAt,
            plan,
            attempt,
            submitDurationMs
        );
        applyExitLifecycle( plan, recorded, attempt, attemptKey );

        return toResult( recorded );
    }

    private EngineOrderAttemptResponse recordAttempt(
        String attemptKey,
        Integer attemptNumber,
        Instant targetAt,
        Instant triggerAt,
        EngineExecutionPlan plan,
        OrderAttempt attempt,
        long requestDurationMs
    )
    {
        return client.recordOrderAttempt( new EngineOrderAttemptRecordRequest(
            attemptKey,
            plan.armedTradeId(),
            attemptNumber,
            plan.venue(),
            plan.symbol(),
            attempt.side(),
            attempt.executionType(),
            attempt.quantity(),
            attempt.limitPrice(),
            attempt.status(),
            attempt.externalOrderId(),
            targetAt,
            triggerAt,
            attempt.submittedAt(),
            attempt.exchangeTimestamp(),
            attempt.failureReason(),
            attempt.averageFillPrice(),
            attempt.filledQuantity(),
            attempt.feeUsd(),
            requestDurationMs
        ) );
    }

    private void applyEntryLifecycle( EngineExecutionPlan plan, EngineOrderAttemptResponse recorded, OrderAttempt attempt )
    {
        if( recorded.status() == OrderAttemptStatus.FILLED )
        {
            client.recordPosition( new EnginePositionRecordRequest(
                plan.armedTradeId(),
                plan.venue(),
                plan.symbol(),
                plan.intendedSide(),
                filledQuantity( attempt ),
                attempt.averageFillPrice(),
                null,
                PositionState.OPEN,
                attempt.exchangeTimestamp() == null ? attempt.submittedAt() : attempt.exchangeTimestamp(),
                null
            ) );
            client.updateTradeState(
                plan.armedTradeId(),
                new EngineTradeStateUpdateRequest( ArmedTradeState.OPEN, "entry filled" )
            );
        }
        else if( isTerminalFailure( recorded.status() ) )
        {
            client.updateTradeState(
                plan.armedTradeId(),
                new EngineTradeStateUpdateRequest( ArmedTradeState.FAILED, recorded.failureReason() )
            );
        }
    }

    private void applyExitLifecycle( EngineExecutionPlan plan, EngineOrderAttemptResponse recorded, OrderAttempt attempt, String exitAttemptKey )
    {
        if( isCompletedExit( recorded, attempt ) )
        {
            BigDecimal fees = zeroIfNull( attempt.feeUsd() );
            BigDecimal grossPnl = grossPnl( plan, attempt );
            client.recordPosition( new EnginePositionRecordRequest(
                plan.armedTradeId(),
                plan.venue(),
                plan.symbol(),
                plan.intendedSide(),
                plan.positionQuantity(),
                plan.positionEntryPrice(),
                attempt.averageFillPrice(),
                PositionState.CLOSED,
                null,
                attempt.exchangeTimestamp() == null ? attempt.submittedAt() : attempt.exchangeTimestamp()
            ) );
            client.updateTradeState(
                plan.armedTradeId(),
                new EngineTradeStateUpdateRequest( ArmedTradeState.CLOSED, "exit filled" )
            );
            client.recordTradeOutcome( new EngineTradeOutcomeRecordRequest(
                plan.armedTradeId(),
                grossPnl,
                grossPnl.subtract( fees ),
                fees,
                "CLOSED",
                "entry/exit filled",
                attempt.exchangeTimestamp() == null ? attempt.submittedAt() : attempt.exchangeTimestamp()
            ) );
        }
        else if( isTerminalFailure( recorded.status() ) )
        {
            releaseAttemptKey( exitAttemptKey );
            client.updateTradeState(
                plan.armedTradeId(),
                new EngineTradeStateUpdateRequest( ArmedTradeState.FAILED, recorded.failureReason() )
            );
        }
    }

    private static boolean isCompletedExit( EngineOrderAttemptResponse recorded, OrderAttempt attempt )
    {
        if( recorded.status() == OrderAttemptStatus.FILLED )
        {
            return true;
        }
        return recorded.status() == OrderAttemptStatus.ACKNOWLEDGED
               && attempt.averageFillPrice() != null
               && attempt.filledQuantity() != null
               && attempt.filledQuantity().signum() > 0;
    }

    private EngineExecutionAttemptResult toResult( EngineOrderAttemptResponse recorded )
    {
        return new EngineExecutionAttemptResult(
            recorded.armedTradeId(),
            recorded.attemptNumber(),
            recorded.attemptKey(),
            recorded.status(),
            recorded.failureReason(),
            recorded.createdAt()
        );
    }

    private EngineExecutionAttemptResult isolatedFailure( Long armedTradeId, Integer attemptNumber, String attemptKey, Exception exception )
    {
        return new EngineExecutionAttemptResult(
            armedTradeId,
            attemptNumber,
            attemptKey,
            OrderAttemptStatus.FAILED,
            "Engine execution failed for " + attemptKey + ": " + exception.getMessage(),
            Instant.now( clock )
        );
    }

    private boolean shouldProcessPlan( EngineExecutionPlan plan, boolean force )
    {
        if( plan == null )
        {
            return false;
        }
        if( plan.status() == EnginePlanStatus.EXIT_WINDOW && !force )
        {
            return plan.positionQuantity() != null && plan.positionQuantity().signum() > 0;
        }
        if( plan.entryAttempts() == null || plan.entryAttempts().isEmpty() )
        {
            return false;
        }
        if( force )
        {
            return plan.status() == EnginePlanStatus.WAITING_ENTRY
                   || plan.status() == EnginePlanStatus.ENTRY_WINDOW
                   || plan.status() == EnginePlanStatus.OVERDUE;
        }
        return plan.status() == EnginePlanStatus.ENTRY_WINDOW;
    }

    private boolean shouldProcessTargetEntry( EngineExecutionPlan plan, EngineEntryAttemptPlan attemptPlan, boolean force )
    {
        if( plan == null || attemptPlan == null )
        {
            return false;
        }
        if( force )
        {
            return plan.status() == EnginePlanStatus.WAITING_ENTRY
                   || plan.status() == EnginePlanStatus.ENTRY_WINDOW
                   || plan.status() == EnginePlanStatus.OVERDUE;
        }
        return plan.status() == EnginePlanStatus.ENTRY_WINDOW
               && !calibratedTrigger( plan, attemptPlan ).isAfter( Instant.now( clock ) );
    }

    private boolean shouldProcessTargetExit( EngineExecutionPlan plan, boolean force )
    {
        if( plan == null || plan.positionQuantity() == null || plan.positionQuantity().signum() <= 0 )
        {
            return false;
        }
        if( force )
        {
            return plan.status() == EnginePlanStatus.WAITING_EXIT || plan.status() == EnginePlanStatus.EXIT_WINDOW;
        }
        return plan.status() == EnginePlanStatus.EXIT_WINDOW;
    }

    private EngineEntryAttemptPlan firstTargetEntryAttempt( EngineExecutionPlan plan )
    {
        if( plan == null || plan.entryAttempts() == null || plan.entryAttempts().isEmpty() )
        {
            return null;
        }
        return plan.entryAttempts().getFirst();
    }

    private String attemptKey( EngineExecutionPlan plan, EngineEntryAttemptPlan attemptPlan )
    {
        return "entry:" + plan.armedTradeId() + ":" + attemptPlan.attemptNumber() + ":" + attemptPlan.targetEntryAt();
    }

    private String exitAttemptKey( EngineExecutionPlan plan )
    {
        return "exit:" + plan.armedTradeId() + ":" + plan.plannedExitAt();
    }

    private boolean reserveAttemptKey( String attemptKey )
    {
        return submittedAttemptKeys.add( attemptKey );
    }

    private void releaseAttemptKey( String attemptKey )
    {
        submittedAttemptKeys.remove( attemptKey );
    }

    private static TradeSide opposite( TradeSide side )
    {
        return side == TradeSide.SHORT ? TradeSide.LONG : TradeSide.SHORT;
    }

    private static boolean isTerminalFailure( OrderAttemptStatus status )
    {
        return status == OrderAttemptStatus.FAILED || status == OrderAttemptStatus.REJECTED;
    }

    private static BigDecimal filledQuantity( OrderAttempt attempt )
    {
        return attempt.filledQuantity() == null ? attempt.quantity() : attempt.filledQuantity();
    }

    private static BigDecimal grossPnl( EngineExecutionPlan plan, OrderAttempt attempt )
    {
        BigDecimal quantity = zeroIfNull( filledQuantity( attempt ) );
        BigDecimal entryPrice = zeroIfNull( plan.positionEntryPrice() );
        BigDecimal exitPrice = zeroIfNull( attempt.averageFillPrice() );
        if( plan.intendedSide() == TradeSide.SHORT )
        {
            return entryPrice.subtract( exitPrice ).multiply( quantity );
        }
        return exitPrice.subtract( entryPrice ).multiply( quantity );
    }

    private static BigDecimal zeroIfNull( BigDecimal value )
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean shouldEarlyExit( EngineExecutionPlan plan )
    {
        if( ( plan.stopLossUsd() == null && plan.takeProfitUsd() == null )
            || plan.positionQuantity() == null || plan.positionQuantity().signum() <= 0
            || plan.positionEntryPrice() == null || plan.venueSymbol() == null )
        {
            return false;
        }
        Optional<MarkPriceResponse> markPriceResponse = client.fetchMarkPrice( plan.venue(), plan.venueSymbol() );
        if( markPriceResponse.isEmpty() )
        {
            return false;
        }
        BigDecimal markPrice = markPriceResponse.get().markPrice();
        if( markPrice == null )
        {
            return false;
        }
        BigDecimal grossPnl = grossPnlForPrice( plan.intendedSide(), plan.positionEntryPrice(), markPrice, plan.positionQuantity() );
        if( plan.stopLossUsd() != null && grossPnl.compareTo( plan.stopLossUsd().negate() ) < 0 )
        {
            return true;
        }
        return plan.takeProfitUsd() != null && grossPnl.compareTo( plan.takeProfitUsd() ) > 0;
    }

    private static BigDecimal grossPnlForPrice( TradeSide side, BigDecimal entryPrice, BigDecimal currentPrice, BigDecimal quantity )
    {
        BigDecimal safeEntry = zeroIfNull( entryPrice );
        BigDecimal safeCurrent = zeroIfNull( currentPrice );
        BigDecimal safeQty = zeroIfNull( quantity );
        if( side == TradeSide.SHORT )
        {
            return safeEntry.subtract( safeCurrent ).multiply( safeQty );
        }
        return safeCurrent.subtract( safeEntry ).multiply( safeQty );
    }

    // Single source of truth for calibrated trigger time
    Instant calibratedTrigger( EngineExecutionPlan plan, EngineEntryAttemptPlan attempt )
    {
        WarmupCalibration cal = warmupByTrade.get( plan.armedTradeId() );
        long effectiveLatencyMs = cal != null
                                  ? cal.calibratedEffectiveLatencyMs()
                                  : ( plan.effectiveEntryLatencyMs() != null ? plan.effectiveEntryLatencyMs() : 0L );
        effectiveLatencyMs = Math.max( 0L, effectiveLatencyMs );
        return attempt.targetEntryAt().minusMillis( effectiveLatencyMs );
    }

    static long warmupBudgetMs( long leadMs )
    {
        return Math.min( leadMs / 2, 250L );
    }

    static long perProbeTimeoutMs( long budgetMs, int probeCount )
    {
        return Math.max( budgetMs / probeCount, 10L );
    }

    private WarmupCalibration executeWarmupProbes( EngineExecutionPlan plan, Instant firstTrigger, Instant startNow )
    {
        int count = plan.warmupProbeCount() != null ? plan.warmupProbeCount() : 3;
        long lead = plan.warmupProbeLeadMs() != null ? plan.warmupProbeLeadMs() : 500L;
        long budgetMs = warmupBudgetMs( lead );
        long perProbeMs = perProbeTimeoutMs( budgetMs, count );
        Instant budgetDeadline = startNow.plusMillis( budgetMs ).isBefore( firstTrigger )
                                 ? startNow.plusMillis( budgetMs ) : firstTrigger;

        List<Long> samples = new ArrayList<>( count );
        for( int i = 0; i < count; i++ )
        {
            if( !Instant.now( clock ).isBefore( budgetDeadline ) )
            {
                break;
            }
            HttpRequest req = HttpRequest.newBuilder()
                                         .uri( URI.create( plan.probeUrl() ) )
                                         .GET()
                                         .timeout( Duration.ofMillis( perProbeMs ) )
                                         .build();
            long start = nanoTimeSupplier.getAsLong();
            try
            {
                probeHttpClient.send( req, HttpResponse.BodyHandlers.discarding() );
                samples.add( ( nanoTimeSupplier.getAsLong() - start ) / 1_000_000L );
            }
            catch( Exception ignored )
            {
                // timeout or network error — skip sample, continue
            }
        }
        return buildCalibration( samples, plan );
    }

    private WarmupCalibration buildCalibration( List<Long> samples, EngineExecutionPlan plan )
    {
        long fallbackLatency = plan.effectiveEntryLatencyMs() != null ? plan.effectiveEntryLatencyMs() : 0L;
        long manual = plan.manualLatencyAdjustmentMs() != null ? plan.manualLatencyAdjustmentMs() : 0L;

        if( samples.isEmpty() )
        {
            return new WarmupCalibration( 0L, 0L, 0L, 0, true, fallbackLatency, Instant.now( clock ) );
        }

        List<Long> sorted = samples.stream().sorted().toList();
        long p50 = percentile( sorted, 50 );
        long p95 = percentile( sorted, 95 );
        long p99 = percentile( sorted, 99 );
        long calibrated = Math.max( 0L, p50 + manual );
        return new WarmupCalibration( p50, p95, p99, samples.size(), false, calibrated, Instant.now( clock ) );
    }

    static long percentile( List<Long> sorted, int pct )
    {
        int idx = (int) Math.max( 0, Math.ceil( pct / 100.0 * sorted.size() ) - 1 );
        return sorted.get( idx );
    }

    private void reportWarmupSamples( EngineExecutionPlan plan, WarmupCalibration cal )
    {
        try
        {
            client.recordLatencySample( new EngineLatencySampleRequest(
                plan.venue(),
                "_all_",
                "warmup-probe",
                cal.p50Ms(),
                cal.doneAt()
            ) );
        }
        catch( Exception ignored )
        {
            // best-effort; never block execution
        }
    }

    private void reportLatencySample( String venue, String symbol, long durationMs, Instant sampledAt )
    {
        try
        {
            client.recordLatencySample( new EngineLatencySampleRequest(
                venue,
                symbol,
                "order-submit",
                durationMs,
                sampledAt
            ) );
        }
        catch( Exception ignored )
        {
            // Latency feedback is best-effort; never block execution on it.
        }
    }
}
