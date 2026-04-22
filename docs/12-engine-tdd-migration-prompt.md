# Engine TDD Migration Prompt

Используй этот документ как стартовый prompt для отдельной Codex/LLM-сессии, когда нужно безопасно перевести `engine-app` на TDD-oriented структуру тестов поверх актуального `main`.

```text
You are working in the repository /Users/mishaivchenko/.codex/worktrees/da09/crypto.

Task: migrate the engine test strategy toward TDD so that engine tests become executable requirements. Work only on the current modular repository shape and assume Java 25 is already the required baseline.

Scope:
- In scope: engine-app production code, engine-owned test structure, and platform-core classes only where engine behavior depends on them directly.
- In scope: preserving monitor-side engine contract tests as black-box acceptance coverage.
- Out of scope: monitor-app feature expansion, UI work, candidate workflow changes, venue diagnostics redesign, auth changes, live order enablement, and new business flow design.

Safety rules:
- Work on a separate branch only.
- Preserve safe-by-default behavior: no live exchange execution should become enabled.
- Make additive, reversible changes in small slices.
- Do not delete broad acceptance coverage before equivalent rule/spec coverage exists.
- Do not change public REST API unless required by a purely technical refactor for testability.

Current repository shape:
- platform-core
- monitor-app
- engine-app
- frontend

Current Java/runtime baseline:
- Java 25 only
- Gradle toolchain uses Java 25
- Docker runtime uses Java 25
- CI uses Java 25

Current engine production classes to analyze first:
- engine-app/src/main/java/com/crypto/funding/engine/CredentialAwareExecutionPort.java
- engine-app/src/main/java/com/crypto/funding/engine/EngineController.java
- engine-app/src/main/java/com/crypto/funding/engine/EngineExecutionScheduler.java
- engine-app/src/main/java/com/crypto/funding/engine/EngineExecutionService.java
- engine-app/src/main/java/com/crypto/funding/engine/EngineMetricsPublisher.java
- engine-app/src/main/java/com/crypto/funding/engine/EnginePlanClient.java
- engine-app/src/main/java/com/crypto/funding/engine/EnginePlanService.java
- engine-app/src/main/java/com/crypto/funding/engine/EngineProperties.java
- engine-app/src/main/java/com/crypto/funding/engine/EngineMetricsPublishProperties.java
- engine-app/src/main/java/com/crypto/funding/engine/EngineRuntimeControlService.java
- engine-app/src/main/java/com/crypto/funding/engine/EngineTelemetryService.java

Current engine-related tests to classify first:
- engine-app/src/test/java/com/crypto/funding/engine/EngineApplicationIntegrationTest.java
- engine-app/src/test/java/com/crypto/funding/engine/EngineMetricsPublisherIntegrationTest.java
- engine-app/src/test/java/com/crypto/funding/engine/EngineMetricsPublisherDisabledIntegrationTest.java
- engine-app/src/test/java/com/crypto/funding/engine/EngineRuntimeControlServiceTest.java
- monitor-app/src/test/java/com/crypto/funding/api/InternalEnginePlanApiIntegrationTest.java
- monitor-app/src/test/java/com/crypto/funding/api/InternalEngineMetricsApiIntegrationTest.java
- monitor-app/src/test/java/com/crypto/funding/api/InternalEngineMetricsDisabledIntegrationTest.java
- monitor-app/src/test/java/com/crypto/funding/api/MonitorDevToolsApiIntegrationTest.java

Migration goals:
1. Reclassify current coverage into acceptance, rule/spec, and config buckets.
2. Keep a thin acceptance layer for end-to-end engine behavior across engine-app and monitor-app contracts.
3. Move core engine decisions into fast unit/spec tests that read like requirements.
4. Make engine rules explicit before production refactors.
5. Reduce overlap between wide integration scenarios and new fast tests.

Target TDD shape:
- Acceptance tests:
  - engine summary and plans endpoint wiring
  - run-once flow against monitor contracts
  - metrics publish enabled/disabled contract
  - monitor-side internal engine contracts preserved as black-box acceptance coverage
- Rule/spec tests:
  - CredentialAwareExecutionPort
    - fails when API key or secret is missing
    - requires passphrase only for bitget, okx, and kucoin
    - remains guarded even when credentials exist
    - normalizes venue names consistently
  - EngineExecutionService
    - processes ENTRY_WINDOW by default
    - processes WAITING_ENTRY and OVERDUE only in force mode
    - skips null or empty entry attempts
    - skips future triggers when not forced
    - records deterministic attempt keys
    - records telemetry for run duration and submit attempts
  - EnginePlanService
    - counts total vs actionable plans correctly
    - normalizes blank venue to unknown
    - exposes status breakdown with zero-filled statuses
  - EngineTelemetryService
    - averages are zero-safe
    - failure classification is stable
    - per-venue submit stats aggregate correctly
  - EngineExecutionScheduler
    - does nothing when loop disabled
    - delegates when loop enabled
  - Properties/config tests
    - interval lower bounds
    - default monitor base URL and token behavior

Required deliverables:
1. A gap matrix with:
   - requirement
   - current test coverage, if any
   - missing coverage
   - target test level
   - target test file
2. A phased migration plan with explicit write scope.
3. Phase 1 implementation only:
   - highest-value fast rule/spec tests first
   - existing acceptance tests remain green
4. Test evidence:
   - exact Gradle commands run
   - tests added or updated
   - remaining gaps

Suggested execution order:
1. Freeze current acceptance behavior on Java 25.
2. Add unit/spec tests for CredentialAwareExecutionPort and EngineExecutionService.
3. Add unit/spec tests for EnginePlanService and EngineTelemetryService.
4. Add scheduler/config tests.
5. Slim redundant assertions from integration tests only after equivalent fast coverage exists.

Definition of done for the first migration slice:
- engine-app has new fast tests that describe rules, not only HTTP flows
- no production behavior changes unless needed to make rules explicit
- acceptance tests still pass
- relevant verification is green:
  - ./gradlew clean :platform-core:test :engine-app:test :monitor-app:test

When in doubt, preserve behavior, clarify requirements through tests first, and keep monitor-side contracts as acceptance boundaries rather than refactoring them into engine internals.
```
