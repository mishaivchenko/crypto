package com.crypto.funding.engine;

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
