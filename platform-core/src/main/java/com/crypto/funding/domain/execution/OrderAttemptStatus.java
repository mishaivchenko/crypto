package com.crypto.funding.domain.execution;

public enum OrderAttemptStatus
{
    CREATED,
    SUBMITTED,
    ACKNOWLEDGED,
    FILLED,
    CANCELLED,
    REJECTED,
    FAILED,
    EXPIRED
}
