package com.crypto.funding.engine;

import com.crypto.funding.application.port.ExecutionPort;
import com.crypto.funding.contract.engine.EngineEntryAttemptPlan;
import com.crypto.funding.contract.engine.EngineExecutionAttemptResult;
import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.contract.engine.EngineExecutionRunResponse;
import com.crypto.funding.contract.engine.EngineOrderAttemptRecordRequest;
import com.crypto.funding.contract.engine.EngineOrderAttemptResponse;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

@Service
public class EngineExecutionService
{
    private final EnginePlanClient client;
    private final ExecutionPort executionPort;
    private final EngineTelemetryService telemetryService;
    private final Clock clock;
    private final LongSupplier nanoTimeSupplier;
    private final Set<String> submittedAttemptKeys = ConcurrentHashMap.newKeySet();

    @Autowired
    public EngineExecutionService( EnginePlanClient client, ExecutionPort executionPort, EngineTelemetryService telemetryService )
    {
        this( client, executionPort, telemetryService, Clock.systemUTC(), System::nanoTime );
    }

    EngineExecutionService( EnginePlanClient client, ExecutionPort executionPort, EngineTelemetryService telemetryService, Clock clock )
    {
        this( client, executionPort, telemetryService, clock, System::nanoTime );
    }

    EngineExecutionService(
        EnginePlanClient client,
        ExecutionPort executionPort,
        EngineTelemetryService telemetryService,
        Clock clock,
        LongSupplier nanoTimeSupplier
    )
    {
        this.client = client;
        this.executionPort = executionPort;
        this.telemetryService = telemetryService;
        this.clock = clock;
        this.nanoTimeSupplier = nanoTimeSupplier;
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
                for( EngineEntryAttemptPlan attemptPlan : plan.entryAttempts() )
                {
                    if( !force && attemptPlan.triggerAt().isAfter( Instant.now( clock ) ) )
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
        OrderAttempt attempt = executionPort.submitOrder( plan, intent, false );
        telemetryService.recordOrderSubmission(
            plan.venue(),
            attempt.status(),
            ( nanoTimeSupplier.getAsLong() - submitStartedAt ) / 1_000_000L
        );
        String attemptKey = attemptKey( plan, attemptPlan );
        EngineOrderAttemptResponse recorded = recordAttempt(
            attemptKey,
            attemptPlan.attemptNumber(),
            attemptPlan.targetEntryAt(),
            attemptPlan.triggerAt(),
            plan,
            attempt
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
        OrderAttempt attempt = executionPort.submitOrder( plan, intent, true );
        telemetryService.recordOrderSubmission(
            plan.venue(),
            attempt.status(),
            ( nanoTimeSupplier.getAsLong() - submitStartedAt ) / 1_000_000L
        );
        EngineOrderAttemptResponse recorded = recordAttempt(
            attemptKey,
            null,
            plan.plannedExitAt(),
            triggerAt,
            plan,
            attempt
        );
        applyExitLifecycle( plan, recorded, attempt );

        return toResult( recorded );
    }

    private EngineOrderAttemptResponse recordAttempt(
        String attemptKey,
        Integer attemptNumber,
        Instant targetAt,
        Instant triggerAt,
        EngineExecutionPlan plan,
        OrderAttempt attempt
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
            attempt.feeUsd()
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

    private void applyExitLifecycle( EngineExecutionPlan plan, EngineOrderAttemptResponse recorded, OrderAttempt attempt )
    {
        if( recorded.status() == OrderAttemptStatus.FILLED )
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
            client.updateTradeState(
                plan.armedTradeId(),
                new EngineTradeStateUpdateRequest( ArmedTradeState.FAILED, recorded.failureReason() )
            );
        }
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
}
