# REST API, Validation & Actuator Audit Report

**Date:** 2026-07-13  
**Audit Scope:** Section 6 — Spring Boot and Application Framework (REST API and validation)  
**Modules:** monitor-app, engine-app, telegram-bot-app, platform-core

---

## 1. Global Exception Handler (`@ControllerAdvice`)

### monitor-app ✅
- **File:** `monitor-app/.../api/ApiExceptionHandler.java`
- Uses `@RestControllerAdvice` (no basePackages — applies to all controllers)
- Handles 6 exception types:
  - `ResourceNotFoundException` → 404 NOT_FOUND
  - `DomainValidationException` → 409 CONFLICT
  - `IllegalArgumentException` / `IllegalStateException` → 400 BAD_REQUEST
  - `MethodArgumentNotValidException` → 400 BAD_REQUEST (field-level error aggregation)
  - `NoResourceFoundException` → 404 NOT_FOUND
  - `Exception` (catch-all) → 500 INTERNAL_SERVER_ERROR
- Response format: `ApiErrorResponse` record (timestamp, status, error phrase, message, path)
- **Verdict:** Well-structured. Has catch-all for unhandled exceptions.

### engine-app ❌
- **No `@ControllerAdvice` or `@ExceptionHandler` exists**
- Relies on Spring Boot's default `BasicErrorController` for error responses
- Spring default error format is inconsistent with monitor-app's `ApiErrorResponse`
- **Risk:** Engine controllers return Spring default error JSON instead of a consistent format

### telegram-bot-app ⚠️
- No web server (sets `spring.main.web-application-type: none`)
- No `@RestController` endpoints — all traffic is outbound HTTP via Feign
- Not applicable — no request-serving surface

---

## 2. Request Validation (`@Valid` Usage)

### Public API endpoints WITH `@Valid` 👍
| Endpoint | Controller | Method |
|----------|-----------|--------|
| PUT `/api/v1/operators/me/credentials/{venue}/{mode}` | OperatorCredentialController | upsert() |
| POST `/api/v1/funding-events/{id}/arm` | FundingEventController | arm() |
| POST `/api/v2/monitor/dev/engine/runtime` | MonitorDevToolsController | updateRuntime() |
| POST `/api/v2/monitor/dev/test-runs` | MonitorDevToolsController | createDevTestRun() |
| POST `/api/v1/auto-approval/rules` | AutoApprovalController | create() |
| PUT `/api/v1/auto-approval/rules/{id}` | AutoApprovalController | update() |
| PUT `/api/v1/armed-trades/{id}` | ArmedTradeController | update() |
| POST `/api/v1/candidates/{id}/approve` | SignalCandidateController | approve() |
| POST `/api/v1/candidates/{id}/reject` | SignalCandidateController | reject() |
| POST `/api/v1/venues/access-mode` | VenueDiagnosticsController | setGlobalMode() |

### Public DTOs WITH Bean Validation annotations 👍
| DTO | Annotations |
|-----|------------|
| OperatorCredentialRequest | `@NotBlank` on apiKey, secretKey |
| ArmFundingEventRequest | `@NotNull @Positive`, `@Min @Max`, `@PositiveOrZero`, `@Min(-60000) @Max(60000)`, `@Size(max=1000)` |
| UpdateArmedTradeRequest | `@NotNull @Positive`, `@Min @Max`, `@PositiveOrZero`, `@Min(-60000) @Max(60000)` |
| DevTestRunCreateRequest | `@NotBlank`, `@NotNull @Positive @DecimalMax("25")` |
| AutoApprovalRuleRequest | `@NotBlank`, `@DecimalMin("0.0")`, `@NotNull @Positive`, `@NotNull` |
| EngineRuntimeSettingsRequest | `@Min(100)` |
| SetGlobalVenueModeRequest | `@NotNull` |
| ApproveCandidateRequest | `@Size(max=32)`, `@Size(max=500)` |
| RejectCandidateRequest | `@Size(max=500)` |

### Public endpoints LACKING `@Valid` ⚠️
| Endpoint | DTO | Notes |
|----------|-----|-------|
| POST `/api/v1/venues/{venue}/default-latency` | `SetVenueDefaultLatencyRequest` | DTO has ZERO validation annotations |
| POST `/api/v2/monitor/dev/test-runs/{id}/entry` | `DevTestRunPhaseRequest` | DTO has ZERO validation annotations |
| POST `/api/v2/monitor/dev/test-runs/{id}/exit` | `DevTestRunPhaseRequest` | DTO has ZERO validation annotations |

### ALL internal endpoints LACK `@Valid` ❌

**monitor-app internal endpoints (engine → monitor direction):**
| Endpoint | DTO |
|----------|-----|
| POST `/internal/v1/engine/metrics-snapshot` | EngineMetricsSnapshot |
| POST `/internal/v1/engine/order-attempts` | EngineOrderAttemptRecordRequest |
| POST `/internal/v1/engine/trades/{id}/state` | EngineTradeStateUpdateRequest |
| POST `/internal/v1/engine/positions` | EnginePositionRecordRequest |
| POST `/internal/v1/engine/outcomes` | EngineTradeOutcomeRecordRequest |
| POST `/internal/v1/engine/latency-samples` | EngineLatencySampleRequest |
| POST `/internal/v1/engine/trades/{id}/warmup-calibration` | WarmupCalibrationRequest |

**engine-app endpoints (monitor → engine direction):**
| Endpoint | DTO |
|----------|-----|
| POST `/internal/engine/execution/target` | EngineExecutionTargetRequest |
| POST `/internal/engine/runtime` | EngineRuntimeControlRequest |

Shared contract classes (in platform-core) have **zero Bean Validation annotations**. Internal endpoints rely on implicit trust via the `X-Internal-Token` shared secret, with no input validation at the HTTP layer.

---

## 3. Bean Validation Provider Availability

| Module | `spring-boot-starter-validation` | Hibernate Validator available? |
|--------|----------------------------------|-------------------------------|
| **monitor-app** | ✅ Yes (explicit dependency) | ✅ Yes |
| **engine-app** | ❌ **Missing** — `jakarta.validation` API indirectly from `spring-boot-starter-web`, but Hibernate Validator implementation is absent | ❌ **No** — `@Valid` on `@RequestBody` would be silently ignored |
| **telegram-bot-app** | ❌ Missing | ❌ No (no HTTP endpoints anyway) |
| **platform-core** | ❌ Missing (java-library, no Spring) | ❌ No (intentionally — pure domain library) |

**Impact:** Even if `@Valid` annotations were added to engine-app controller methods, they would not enforce validation because no Bean Validation provider is on the runtime classpath. The engine-app needs `spring-boot-starter-validation` added to its `build.gradle`.

---

## 4. OpenAPI / Swagger / Springdoc

- **No OpenAPI, Swagger, or Springdoc dependencies** in any `build.gradle`
- **No `@Operation`, `@ApiResponse`, `@Schema`, or `@Tag` annotations** in any Java source
- **No OpenAPI specification files** (`.yaml`, `.yml`, `.json`) exist in the repository
- **No API documentation tooling** is configured
- **Verdict:** The project has no generated or hand-written API documentation. API consumers (monitor UI, telegram bot) must inspect code or use runtime debugging to understand endpoints.

---

## 5. API Specification Generation

- **No API specification generation** is set up
- No springdoc-openapi, no Swagger Codegen, no OpenAPI Generator
- No protocol buffer definitions, no GraphQL schema, no RAML/Blueprint docs
- **Verdict:** Not applicable — no specification generation exists

---

## 6. Contract Between Monitor and Engine

Shared contract is defined in `platform-core`:

```
platform-core/src/main/java/com/crypto/funding/contract/engine/
├── EngineExecutionPlan.java
├── EngineExecutionRunResponse.java
├── EngineExecutionTargetRequest.java
├── EngineLatencySampleRequest.java
├── EngineMetricsSnapshot.java
├── EngineMetricsSnapshotRequest.java
├── EngineOrderAttemptRecordRequest.java
├── EnginePositionRecordRequest.java
├── EngineRuntimeControlRequest.java
├── EngineRuntimeControlResponse.java
├── EngineSummaryResponse.java
├── EngineTradeOutcomeRecordRequest.java
├── EngineTradeStateUpdateRequest.java
├── EngineVenueCredentials.java
├── EngineVenueCredentialsResponse.java
├── MarkPriceResponse.java
├── WarmupCalibrationRequest.java
├── WarmupCalibrationResponse.java
└── ... (enums and shared types)
```

**Dependency topology:**
```
monitor-app ──depends on──▶ platform-core ◀──depends on── engine-app
                                    ▲
                                    │
                          telegram-bot-app ──depends on──▶ platform-core
                              (uses only domain types, not contracts)
```

- Both modules compile against the same platform-core jar
- Contracts are plain Java records with no validation, versioning, or serialization schema
- No contract testing framework (Pact, Spring Cloud Contract) validates compatibility
- **Risk:** An incompatible change to a shared record compiles fine but breaks at runtime

---

## 7. Internal API Versioning

### Current state:

| Module | API Prefix | Versioned? |
|--------|-----------|------------|
| monitor-app (public operator UI) | `/api/v1/...` | ✅ Yes — v1 |
| monitor-app (public operator dev tools) | `/api/v2/monitor/...` | ✅ Yes — v2 |
| monitor-app (internal engine API) | `/internal/v1/engine/...` | ✅ Yes — v1 |
| **engine-app (internal)** | **`/internal/engine/...`** | **❌ No version prefix** |

### Analysis:
- monitor-app's public API is versioned with `/api/v1/` and `/api/v2/`
- monitor-app's internal API for engine communication uses `/internal/v1/`
- **engine-app has NO version prefix** — its paths are `/internal/engine/...` without `/v1/`
- monitor-app's `EngineControlService` calls engine-app using unversioned paths like `/internal/engine/execution/run-once`
- **Risk:** Future incompatible changes to engine-app's API cannot be detected at the URL level. The monitor-app must be deployed in lockstep with the engine-app.

---

## 8. Monitor-Engine Version Incompatibility Detection

**None exists.** This is a significant finding.

| Detection mechanism | Status |
|--------------------|--------|
| Version header in HTTP requests (`Accept-Version`, `X-API-Version`) | ❌ Not implemented |
| Runtime version comparison on startup | ❌ Not implemented |
| Shared version constant from build artifact | ❌ Not implemented |
| Version strings exist in code | ⚠️ Hardcoded as `"2.0.0"` **string literals** in Java source |

### Details:
- `MonitorOverviewService.java` line 196: `return "2.0.0";` — hardcoded
- `EngineRuntimeControlService.java` line 106: `return "2.0.0";` — hardcoded
- Neither is sourced from `gradle.properties`, `build.gradle`, or any properties file
- The version field is exposed in API responses but never compared across modules
- **No startup validation** checks that monitor-app and engine-app versions are compatible
- **No runtime validation** checks version compatibility on API calls

---

## 9. Build Version Exposure via Actuator (`/actuator/info`)

### Current state:
- **`/actuator/info` returns empty `{}`** in all modules
- No `build-info.properties` is generated — the `spring-boot` Gradle plugin's `buildInfo()` is not invoked
- No `git.properties` is generated — `git-commit-id-plugin` is not configured
- No version, build time, or git commit is exposed at runtime

### Configuration:
- monitor-app: Exposes `health,info,prometheus` via management endpoints (from `platform-core.yml`)
- engine-app: Has `spring-boot-starter-actuator` but **no `management.endpoints.*` config** — only default `/actuator/health` exposed
- telegram-bot-app: No actuator dependency, no web server

### Artifact version:
- Root `build.gradle` line 19: `version = '2.0.0'`
- This sets the JAR artifact version but is never consumed at runtime

---

## 10. Health Indicators

### No custom `HealthIndicator` implementations exist anywhere:

| Module | Auto-configured indicators | Custom indicators |
|--------|---------------------------|-------------------|
| monitor-app | `DataSourceHealthIndicator`, `DiskSpaceHealthIndicator`, `PingHealthIndicator` | ❌ None |
| engine-app | `DiskSpaceHealthIndicator`, `PingHealthIndicator` | ❌ None |
| telegram-bot-app | N/A (no web server) | ❌ None |

### Health assessments that should be considered:
1. **Credential availability:** Is the credential master key present? Are exchange API keys populated?
2. **Exchange connectivity:** Can the app reach the configured exchanges?
3. **Engine loop status:** Is the execution loop running? (engine-app)
4. **Clock synchronization:** Is the system clock within acceptable drift?
5. **Database connectivity:** Can the app reach its SQLite database? (monitor-app — auto-configured via DataSourceHealthIndicator)

---

## Summary of Findings

| # | Finding | Severity | Notes |
|---|---------|----------|-------|
| 1 | engine-app missing `@ControllerAdvice` | Medium | Inconsistent error format, relies on Spring Boot default |
| 2 | engine-app missing `spring-boot-starter-validation` | Medium | `@Valid` silently ignored — no validation possible |
| 3 | All internal endpoints lack validation | Medium | Internal API relies on implicit trust between modules |
| 4 | 2 public DTOs have no validation annotations | Low | SetVenueDefaultLatencyRequest, DevTestRunPhaseRequest |
| 5 | No OpenAPI/Swagger documentation | Low | No API docs — requires code inspection |
| 6 | engine-app has no API version prefix | Medium | `/internal/engine/...` vs `/internal/v1/engine/...` |
| 7 | No monitor-engine version compatibility check | High | Cannot detect version mismatch at startup or runtime |
| 8 | `/actuator/info` returns empty | Low | Build version not exposed — no build-info config |
| 9 | No custom health indicators | Low | Domain-specific health checks not implemented |
| 10 | Version hardcoded as string literal | Low | `"2.0.0"` in Java source, not configurable |
