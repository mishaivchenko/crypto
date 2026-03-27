package com.crypto.funding.legacy.execution;

import com.crypto.funding.config.ExecutionMode;

import java.util.Set;

public record LegacyExecutionDecision(
    ExecutionMode mode,
    boolean allowed,
    boolean passive,
    String reason,
    Set<String> requestedVenues,
    Set<String> executableVenues
)
{
    public static LegacyExecutionDecision blocked(
        ExecutionMode mode,
        String reason,
        Set<String> requestedVenues,
        Set<String> executableVenues
    )
    {
        return new LegacyExecutionDecision( mode, false, true, reason, requestedVenues, executableVenues );
    }

    public static LegacyExecutionDecision allowed(
        ExecutionMode mode,
        Set<String> requestedVenues,
        Set<String> executableVenues
    )
    {
        return new LegacyExecutionDecision( mode, true, false, null, requestedVenues, executableVenues );
    }
}
