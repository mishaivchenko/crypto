# Acceptance Boundary Requirements

Primary classes: `EngineController`, `EngineApplication`.

- `ENG-ACC-001`: `engine-app` exposes summary and plan endpoints without changing monitor contract shape.
- `ENG-ACC-002`: `engine-app` exposes runtime controls and accepts runtime updates through `/internal/engine/runtime`.
- `ENG-ACC-003`: forced run-once execution records failed attempts through the existing monitor contract when credentials are missing.
- `ENG-ACC-004`: metrics publishing stays feature-flagged; when enabled it posts a low-cardinality snapshot and when disabled the publisher bean stays absent.
- `ENG-ACC-005`: `local-safe`, `staging`, and `prod-like` profiles preserve safe loop defaults and profile-specific metrics publishing behavior.
- `ENG-ACC-006`: monitor-side internal engine plan and order-attempt endpoints remain the read-only acceptance boundary for internal token enforcement, idempotent attempt recording, and overdue inclusion.
- `ENG-ACC-007`: monitor-side metrics ingest and dev-tools endpoints remain the read-only acceptance boundary for engine metrics export, metrics-disable behavior, operator auth, and engine proxy wiring.
- `ENG-ACC-008`: `EngineController` delegates summary, list-plans, and get-plan reads without reshaping service responses.
- `ENG-ACC-009`: `EngineController` passes the `force` flag directly to `EngineExecutionService.runOnce`.
- `ENG-ACC-010`: `EngineController` passes runtime snapshots and runtime update payloads through unchanged.
- `ENG-ACC-011`: `EngineApplication.main` delegates bootstrap to `SpringApplication.run(EngineApplication.class, args)`.
