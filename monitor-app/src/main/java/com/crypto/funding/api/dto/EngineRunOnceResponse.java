package com.crypto.funding.api.dto;

import com.crypto.funding.contract.engine.EngineExecutionAttemptResult;

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
}
