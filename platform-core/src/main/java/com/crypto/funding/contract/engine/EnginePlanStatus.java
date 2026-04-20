package com.crypto.funding.contract.engine;

public enum EnginePlanStatus
{
    WAITING_ENTRY,
    ENTRY_WINDOW,
    WAITING_EXIT,
    EXIT_WINDOW,
    OVERDUE,
    CLOSED,
    INVALID
}
