# Scheduler Requirements

Primary class: `EngineExecutionScheduler`.

- `ENG-SCH-001`: the scheduler is a no-op when the runtime gate is closed.
- `ENG-SCH-002`: the scheduler delegates to `runOnce(false)` when the runtime gate is open.
