# Monitor Client Requirements

Primary classes: `EnginePlanClient`, `EngineProperties`.

- `ENG-CLI-001`: `listPlans(includeAll)` sends the current query flag and internal token header.
- `ENG-CLI-002`: `recordOrderAttempt` posts the request body shape expected by monitor and records timing telemetry.
- `ENG-CLI-003`: `publishMetricsSnapshot` posts the current snapshot wire shape with the internal token header.
- `ENG-CLI-004`: `getPlan` fetches a single plan by path without changing endpoint shape.
- `ENG-CLI-005`: engine client properties keep the default monitor base URL, blank internal token, and safe loop defaults.
- `ENG-CLI-006`: `fetchMarkPrice` fetches mark price from the monitor price endpoint; errors return `Optional.empty()` without propagating.
- `ENG-CLI-007`: `recordWarmupCalibration` posts warmup calibration data to `/internal/v1/engine/trades/{id}/warmup-calibration` with the internal token header and correct body shape.
- `ENG-CLI-008`: `fetchCredentials` fetches venue credentials from `/internal/v1/engine/credentials/{venue}?mode={mode}` with the internal token header; errors return `Optional.empty()` without propagating.
