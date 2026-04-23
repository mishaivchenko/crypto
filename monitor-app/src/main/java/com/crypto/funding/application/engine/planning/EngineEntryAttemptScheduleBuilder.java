package com.crypto.funding.application.engine.planning;

import com.crypto.funding.contract.engine.EngineEntryAttemptPlan;
import com.crypto.funding.domain.trade.ArmedTrade;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class EngineEntryAttemptScheduleBuilder
{
    public List<EngineEntryAttemptPlan> build( ArmedTrade trade, Instant now )
    {
        if( trade.plannedEntryAt() == null )
        {
            return List.of();
        }

        int attempts = trade.entryAttemptCount() == null ? 1 : trade.entryAttemptCount();
        long spacingMs = trade.entrySpacingMs() == null ? 0L : trade.entrySpacingMs();
        long effectiveLatencyMs = trade.effectiveEntryLatencyMs() == null ? 0L : trade.effectiveEntryLatencyMs();

        return java.util.stream.IntStream.range( 0, attempts )
                                         .mapToObj( index -> {
                                             long offsetMs = spacingMs * index;
                                             Instant targetEntryAt = trade.plannedEntryAt().plusMillis( offsetMs );
                                             Instant triggerAt = targetEntryAt.minusMillis( effectiveLatencyMs );
                                             return new EngineEntryAttemptPlan(
                                                 index + 1,
                                                 targetEntryAt,
                                                 triggerAt,
                                                 Duration.between( now, triggerAt ).toMillis(),
                                                 offsetMs,
                                                 effectiveLatencyMs
                                             );
                                         } )
                                         .toList();
    }

    public Instant firstTriggerAt( ArmedTrade trade )
    {
        long effectiveLatencyMs = trade.effectiveEntryLatencyMs() == null ? 0L : trade.effectiveEntryLatencyMs();
        return trade.plannedEntryAt().minusMillis( effectiveLatencyMs );
    }

    public Instant lastTargetAt( ArmedTrade trade )
    {
        int attempts = trade.entryAttemptCount() == null ? 1 : trade.entryAttemptCount();
        long spacingMs = trade.entrySpacingMs() == null ? 0L : trade.entrySpacingMs();
        return trade.plannedEntryAt().plusMillis( spacingMs * Math.max( 0, attempts - 1 ) );
    }
}
