# Engine-Core Requirements

Primary classes: engine-owned invariants consumed by `EngineExecutionService` and `EngineMetricsPublisher`.

- `ENG-CORE-001`: `OrderIntent` enforces non-null side, non-null execution type, positive quantity, and positive limit price for limit intents.
- `ENG-CORE-002`: `OrderAttempt` enforces required identifiers, venue/symbol presence, positive quantity, positive attempt number when present, and non-null status.
- `ENG-CORE-003`: `EngineMetricsSnapshot` zero-fills status breakdown values for all known `EnginePlanStatus` entries.
- `ENG-CORE-004`: `EngineMetricsSnapshot` normalizes string-keyed metric maps to lowercase keys, drops blank keys, and converts `null` counts to zero.
