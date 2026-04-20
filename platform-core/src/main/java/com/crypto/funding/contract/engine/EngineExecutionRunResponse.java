package com.crypto.funding.contract.engine;

import java.time.Instant;
import java.util.List;

public record EngineExecutionRunResponse(
    Instant startedAt,
    Instant finishedAt,
    boolean force,
    int plansScanned,
    int attemptsSubmitted,
    int attemptsSkipped,
    List<EngineExecutionAttemptResult> results
)
{
}
