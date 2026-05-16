package com.crypto.funding.api.dto;

import com.crypto.funding.contract.engine.EngineExecutionAttemptResult;
import com.crypto.funding.contract.engine.EngineExecutionRunResponse;

import java.time.Instant;
import java.util.List;

public record EngineRunOnceResponse(
    Instant startedAt,
    Instant finishedAt,
    boolean force,
    int plansScanned,
    int attemptsSubmitted,
    int attemptsSkipped,
    List<EngineExecutionAttemptResult> results
)
{
    public static EngineRunOnceResponse from( EngineExecutionRunResponse r )
    {
        return new EngineRunOnceResponse( r.startedAt(), r.finishedAt(), r.force(),
            r.plansScanned(), r.attemptsSubmitted(), r.attemptsSkipped(), r.results() );
    }
}
