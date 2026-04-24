package com.crypto.funding.engine;

import com.crypto.funding.application.port.ExecutionPort;
import com.crypto.funding.contract.engine.EngineEntryAttemptPlan;
import com.crypto.funding.contract.engine.EngineExecutionAttemptResult;
import com.crypto.funding.contract.engine.EngineExecutionPlan;
import com.crypto.funding.contract.engine.EngineExecutionRunResponse;
import com.crypto.funding.contract.engine.EngineOrderAttemptRecordRequest;
import com.crypto.funding.contract.engine.EngineOrderAttemptResponse;
import com.crypto.funding.contract.engine.EnginePlanStatus;
import com.crypto.funding.domain.execution.ExecutionType;
import com.crypto.funding.domain.execution.OrderAttempt;
import com.crypto.funding.domain.execution.OrderIntent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

@Service
public class EngineExecutionService
{
    private final EnginePlanClient client;
    private final ExecutionPort executionPort;
    private final EngineTelemetryService telemetryService;
    private final Clock clock;
    private final LongSupplier nanoTimeSupplier;

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
                for( EngineEntryAttemptPlan attemptPlan : plan.entryAttempts() )
                {
                    if( !force && attemptPlan.triggerAt().isAfter( Instant.now( clock ) ) )
                    {
                        skipped++;
                        continue;
                    }
                    EngineExecutionAttemptResult result = executeAttempt( plan, attemptPlan );
                    results.add( result );
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

    private EngineExecutionAttemptResult executeAttempt( EngineExecutionPlan plan, EngineEntryAttemptPlan attemptPlan )
    {
        OrderIntent intent = new OrderIntent(
            plan.intendedSide(),
            ExecutionType.MARKET,
            plan.notionalUsd(),
            null,
            attemptPlan.triggerAt()
        );
        long submitStartedAt = nanoTimeSupplier.getAsLong();
        OrderAttempt attempt = executionPort.submitOrder( plan.armedTradeId(), plan.venue(), plan.symbol(), intent );
        telemetryService.recordOrderSubmission(
            plan.venue(),
            attempt.status(),
            ( nanoTimeSupplier.getAsLong() - submitStartedAt ) / 1_000_000L
        );
        String attemptKey = attemptKey( plan, attemptPlan );
        EngineOrderAttemptResponse recorded = client.recordOrderAttempt( new EngineOrderAttemptRecordRequest(
            attemptKey,
            plan.armedTradeId(),
            attemptPlan.attemptNumber(),
            plan.venue(),
            plan.symbol(),
            attempt.side(),
            attempt.executionType(),
            attempt.quantity(),
            attempt.limitPrice(),
            attempt.status(),
            attempt.externalOrderId(),
            attemptPlan.targetEntryAt(),
            attemptPlan.triggerAt(),
            attempt.submittedAt(),
            attempt.exchangeTimestamp(),
            attempt.failureReason()
        ) );

        return new EngineExecutionAttemptResult(
            recorded.armedTradeId(),
            recorded.attemptNumber(),
            recorded.attemptKey(),
            recorded.status(),
            recorded.failureReason(),
            recorded.createdAt()
        );
    }

    private boolean shouldProcessPlan( EngineExecutionPlan plan, boolean force )
    {
        if( plan == null || plan.entryAttempts() == null || plan.entryAttempts().isEmpty() )
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
}
