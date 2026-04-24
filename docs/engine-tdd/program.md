# Engine TDD Living Program

## Purpose

`engine-app` and the engine-relevant behavior in `platform-core` should become executable specifications. The long-term target is that engine semantics can be rebuilt from fast tests plus requirement docs, while `monitor-app` remains a read-only acceptance boundary for engine contracts.

## Scope

- In scope: `engine-app`, engine-owned contracts, and engine-relevant value/invariant behavior in `platform-core`.
- Out of scope: monitor-side business logic, candidate/event workflows, live exchange enablement, and non-engine frontend work.
- Contract freeze: `/internal/engine/*` and monitor-side internal engine endpoints keep their wire shape while this program runs.

## Engine Inventory

- Every production class under `engine-app/src/main/java/com/crypto/funding/engine` must appear in `docs/engine-tdd`, `gap-matrix.md`, and at least one requirement-mapped test.
- Current engine inventory: `CredentialAwareExecutionPort`, `EngineApplication`, `EngineController`, `EngineExecutionScheduler`, `EngineExecutionService`, `EngineMetricsPublishProperties`, `EngineMetricsPublisher`, `EngineModuleConfiguration`, `EnginePlanClient`, `EnginePlanService`, `EngineProperties`, `EngineRuntimeControlService`, and `EngineTelemetryService`.

## Source Of Truth

- Tests are the executable source of truth for behavior.
- `docs/engine-tdd/requirements/*.md` are the navigation and completeness layer.
- `docs/engine-tdd/gap-matrix.md` is the tracking layer that maps every requirement to current coverage and the next target.

## Coverage Baskets

- `rule/spec`: fast tests for execution, credentials, planning, scheduler, runtime, telemetry, and engine-core invariants.
- `contract`: client/wire-shape tests for monitor communication and metrics snapshot payloads.
- `config`: properties, defaults, lower bounds, and safe profile semantics.
- `acceptance`: thin end-to-end confirmation through `engine-app` and monitor-side engine boundaries.

## Program Waves

### Wave 0: Baseline Freeze And Mapping

- Preserve existing acceptance tests.
- Introduce stable requirement IDs.
- Keep the gap matrix current.
- Add a docs-to-tests consistency gate.

### Wave 1: Engine-Core Specs

- Lock constructor rules, invariant checks, and snapshot normalization in `platform-core`.

### Wave 2: Credential And Execution Rules

- Express guarded execution, credential requirements, and attempt scheduling behavior as fast tests.

### Wave 3: Plan, Runtime, Scheduler, Telemetry

- Move engine read-side semantics and runtime control semantics under fast tests.

### Wave 4: Metrics Publishing And Monitor Client

- Keep monitor wire contracts explicit and testable without expanding monitor business scope.

### Wave 5: Acceptance Thinning And Survivor Elimination

- Remove duplicated assertions from wide tests only after equivalent fast coverage exists.
- Drive mutation survivors to zero or record an explicit allowlist in the gap matrix.
- Keep the hourly thread heartbeat active until every `engine-app` class is traceable and mutation-clean.

## Entry Points

- `./gradlew engineTddDocsCheck --no-daemon`
- `./gradlew engineTddGate --no-daemon`

## Stop Condition

This program stays open until the gap matrix has no uncovered engine-owned semantics, `engineTddGate` is green, and mutation/coverage targets remain green after refactors. When new engine behavior appears, the program reopens with the same rules.
