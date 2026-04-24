# Execution Requirements

Primary class: `EngineExecutionService`.

- `ENG-EXE-001`: `force=false` processes only `ENTRY_WINDOW` plans.
- `ENG-EXE-002`: `force=true` also processes `WAITING_ENTRY` and `OVERDUE` plans.
- `ENG-EXE-003`: plans with `null` or empty `entryAttempts` are skipped.
- `ENG-EXE-004`: future triggers are skipped when the run is not forced.
- `ENG-EXE-005`: attempt keys stay deterministic as `entry:<armedTradeId>:<attemptNumber>:<targetEntryAt>`.
- `ENG-EXE-006`: recorded order-attempt payloads mirror the execution result returned by the execution port.
- `ENG-EXE-007`: execution runs update telemetry for run counts, run timing, and submit status accounting.
