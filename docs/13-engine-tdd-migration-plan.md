# Engine TDD Migration Plan

## Goal

Перевести `engine-app` от преобладающего integration-style покрытия к TDD-oriented структуре, где быстрые tests выражают правила движка как исполняемые требования, а широкие acceptance tests остаются тонким внешним слоем. Эта миграция идёт на актуальном `main` и предполагает Java 25 как обязательный baseline для всего репозитория.

Отдельный merge ветки `codex/engine-tdd-java25` не требуется: она уже является ancestor текущего `main`, поэтому merge-step считается фактически завершённым и в этой линии фиксируется как `no-op`.

## Current Baseline

### Актуальный scope

- `platform-core` содержит shared domain, engine contracts, ports и utilities.
- `monitor-app` владеет operator API, internal engine contracts и acceptance boundary со стороны control plane.
- `engine-app` владеет execution planning, runtime loop, telemetry и metrics publishing.

### Текущие acceptance tests

- `engine-app/src/test/java/com/crypto/funding/engine/EngineApplicationIntegrationTest.java`
- `engine-app/src/test/java/com/crypto/funding/engine/EngineMetricsPublisherIntegrationTest.java`
- `engine-app/src/test/java/com/crypto/funding/engine/EngineMetricsPublisherDisabledIntegrationTest.java`
- `monitor-app/src/test/java/com/crypto/funding/api/InternalEnginePlanApiIntegrationTest.java`
- `monitor-app/src/test/java/com/crypto/funding/api/InternalEngineMetricsApiIntegrationTest.java`
- `monitor-app/src/test/java/com/crypto/funding/api/InternalEngineMetricsDisabledIntegrationTest.java`
- `monitor-app/src/test/java/com/crypto/funding/api/MonitorDevToolsApiIntegrationTest.java`

### Текущий перекос

- acceptance coverage сильнее, чем rule/spec coverage;
- часть engine semantics спрятана внутри больших integration scenarios;
- быстрые tests почти не фиксируют rules around execution filtering, credential guarding и telemetry aggregation;
- scheduler/config semantics покрыты частично и не выглядят как executable requirements.

## Gap Matrix Deliverable

До начала production refactor обязателен отдельный gap matrix со столбцами:

| Requirement | Current coverage | Missing coverage | Target level | Target test file |
| --- | --- | --- | --- | --- |
| Missing API key or secret -> failed attempt | Existing flow asserts indirectly, if any | Explicit fast rule missing | Unit/spec | `CredentialAwareExecutionPortTest` |
| Passphrase required only for bitget/okx/kucoin | Not explicit | Explicit rule missing | Unit/spec | `CredentialAwareExecutionPortTest` |
| `force=false` executes only `ENTRY_WINDOW` | Broadly implied by integration flow | Explicit filtering spec missing | Unit/spec | `EngineExecutionServiceTest` |
| `force=true` includes `WAITING_ENTRY` and `OVERDUE` | Partially implied | Explicit coverage missing | Unit/spec | `EngineExecutionServiceTest` |
| Future attempts skipped when not forced | Not explicit | Missing | Unit/spec | `EngineExecutionServiceTest` |
| Attempt key generation deterministic | Not explicit | Missing | Unit/spec | `EngineExecutionServiceTest` |
| Summary counts total vs actionable plans | Not explicit | Missing | Unit/spec | `EnginePlanServiceTest` |
| Blank venue normalized to `unknown` | Not explicit | Missing | Unit/spec | `EnginePlanServiceTest` |
| Telemetry averages zero-safe | Not explicit | Missing | Unit/spec | `EngineTelemetryServiceTest` |
| Telemetry failure classification stable | Not explicit | Missing | Unit/spec | `EngineTelemetryServiceTest` |
| Scheduler no-op when disabled | Partially covered via app context | Direct rule missing | Unit/spec | `EngineExecutionSchedulerTest` |
| Scheduler delegates when enabled | Partially covered via app context | Direct rule missing | Unit/spec | `EngineExecutionSchedulerTest` |
| Engine config defaults and lower bounds | Not explicit | Missing | Config/unit | `EnginePropertiesTest`, `EngineMetricsPublishPropertiesTest` |

Gap matrix должен жить либо как отдельный раздел обновлённого migration artifact, либо как companion note в первой TDD implementation branch. Он обязателен до удаления каких-либо integration assertions.

## Migration Principles

- Сохранять monitor-side engine contract tests как black-box acceptance layer.
- Не удалять широкие integration tests до появления читаемой эквивалентной fast coverage.
- Сначала делать rules explicit через tests, потом рефакторить production code.
- Не включать live execution.
- Не менять REST API, operator flow, candidate flow, UI и venue workflows.
- Не расширять `monitor-app`, кроме поддержки существующих engine contracts как acceptance boundary.
- Любая работа выполняется уже на Java 25 baseline.

## Phase Plan

### Phase 0. Freeze Current Acceptance Baseline

Цель: зафиксировать поведение текущего `main` перед TDD migration.

Write scope:
- `engine-app/src/test/java/...`
- `monitor-app/src/test/java/...`
- при необходимости notes/gap matrix document

Expected tests:
- существующие acceptance tests проходят без ослабления assertions

Must stay unchanged:
- REST contracts
- safe behavior
- monitor/engine interaction shape

Exit criteria:
- зелёный baseline:
  - `./gradlew clean :platform-core:test :engine-app:test :monitor-app:test`

### Phase 0.5. Java 25 Readiness Gate

Цель: зафиксировать единую Java 25 baseline до engine TDD refactor.

Write scope:
- root build and runtime config
- CI and Docker runtime references
- repo-facing docs

Expected tests/checks:
- `./gradlew clean :platform-core:test :engine-app:test :monitor-app:test`
- `./gradlew build`
- `./gradlew bootRunMonitor`
- `./gradlew bootRunEngine`

Must stay unchanged:
- public API
- runtime ports
- business semantics

Exit criteria:
- toolchain, CI and Docker all point to Java 25
- repo-facing docs no longer instruct Java 21
- monitor and engine start on Java 25

### Phase 1. Fast Rule/Spec Tests For Credential And Execution Rules

Цель: сделать core execution rules быстрыми и читаемыми.

Write scope:
- `engine-app/src/test/java/com/crypto/funding/engine/CredentialAwareExecutionPortTest.java`
- `engine-app/src/test/java/com/crypto/funding/engine/EngineExecutionServiceTest.java`
- production code only if required for testability without behavior change

Expected tests:
- missing credentials -> failed attempt
- venue-specific passphrase rules
- guard remains active even with credentials
- `force=false` processes only `ENTRY_WINDOW`
- `force=true` expands to `WAITING_ENTRY` and `OVERDUE`
- null/empty attempts skipped
- future triggers skipped when not forced
- deterministic attempt key behavior
- telemetry hooks invoked consistently

Must stay unchanged:
- external engine API
- live execution remains off/guarded

Exit criteria:
- new fast tests exist and read as requirements
- existing acceptance tests remain green

### Phase 2. Fast Rule/Spec Tests For Plan And Telemetry Semantics

Цель: зафиксировать read-side and aggregation semantics отдельно от HTTP flows.

Write scope:
- `engine-app/src/test/java/com/crypto/funding/engine/EnginePlanServiceTest.java`
- `engine-app/src/test/java/com/crypto/funding/engine/EngineTelemetryServiceTest.java`

Expected tests:
- total vs actionable counts
- zero-filled status breakdown
- blank venue normalization
- zero-safe averages
- stable failure classification
- per-venue submit aggregation correctness

Must stay unchanged:
- summary and plans endpoint wire shape
- metrics publishing contract

Exit criteria:
- plan and telemetry semantics защищены быстрыми tests

### Phase 3. Scheduler And Config Semantics

Цель: сделать runtime controls и config defaults explicit.

Write scope:
- `engine-app/src/test/java/com/crypto/funding/engine/EngineExecutionSchedulerTest.java`
- `engine-app/src/test/java/com/crypto/funding/engine/EnginePropertiesTest.java`
- `engine-app/src/test/java/com/crypto/funding/engine/EngineMetricsPublishPropertiesTest.java`

Expected tests:
- scheduler no-op when loop disabled
- scheduler delegates when loop enabled
- interval lower bounds / clamping
- default monitor base URL and token semantics

Must stay unchanged:
- existing runtime control endpoints
- existing operator dev-tools wiring

Exit criteria:
- scheduler and properties semantics no longer rely on broad integration behavior for safety

### Phase 4. Thin Acceptance Cleanup

Цель: убрать только дублирующуюся integration detail, сохранив contract confidence.

Write scope:
- existing engine and monitor acceptance tests

Expected changes:
- remove redundant low-level assertions already covered by fast tests
- keep endpoint wiring, serialization, and inter-module contract coverage

Must stay unchanged:
- monitor-side engine contract tests remain as black-box acceptance boundary

Exit criteria:
- acceptance layer becomes thinner and easier to read
- no meaningful loss in contract confidence

### Phase 5. Optional Production Refactor For Testability

Цель: улучшить внутреннюю структуру движка только там, где новые tests показали реальный design debt.

Write scope:
- `CredentialAwareExecutionPort`
- `EngineExecutionService`
- `EnginePlanService`
- `EngineTelemetryService`
- small helpers/collaborators if truly needed

Allowed changes:
- extract helpers
- package-private collaborators
- smaller methods

Not allowed:
- business scope expansion
- live order enablement
- monitor feature work

Exit criteria:
- production code becomes easier to reason about under the new test suite
- external behavior stays stable

## Verification Commands

Минимальный gate для migration slices:

```bash
./gradlew clean :platform-core:test :engine-app:test :monitor-app:test
./gradlew build
```

Java 25 readiness gate:

```bash
./gradlew bootRunMonitor
./gradlew bootRunEngine
```

Если локальная Java path требует явного указания на macOS:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@25 \
./gradlew --no-daemon -Dorg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25 clean :platform-core:test :engine-app:test :monitor-app:test
```

## Exit Criteria For The Full Migration

- Java 25 является единственным зафиксированным baseline для build, local run, CI и Docker runtime.
- У `engine-app` есть отдельный fast test layer, читаемый как набор требований.
- Acceptance tests сохранены, но стали тоньше и сфокусированы на wiring/contracts.
- Любая поломка engine rules сначала ловится быстрыми spec tests, а не только integration scenarios.
- REST API, operator flow, UI и live execution semantics не изменены.
