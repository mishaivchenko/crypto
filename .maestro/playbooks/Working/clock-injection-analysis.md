# Clock Injection & Time Determinism Analysis

**Audit:** Audit Round 3, Section 6 — Application Structure  
**Date:** 2026-07-13  
**Scope:** All modules (monitor-app, engine-app, telegram-bot-app)

---

## Overview

`java.time.Clock` is the standard Java mechanism for providing an injectable time source. When a service uses `Instant.now(clock)` instead of `Instant.now()`, tests can supply `Clock.fixed(...)` for deterministic assertions. This analysis covers every service across all three modules.

---

## Services WITH Clock Injection (Deterministic)

All follow the same dual-constructor pattern:

```java
@Autowired
public SomeService(DepA depA, DepB depB) {
    this(depA, depB, Clock.systemUTC());  // production: real clock
}

// package-private for tests
SomeService(DepA depA, DepB depB, Clock clock) {
    this.depA = depA;
    this.clock = clock;
}
```

| # | Service | Module | Uses `Instant.now(clock)` | `LongSupplier` also injected? |
|---|---------|--------|--------------------------|-------------------------------|
| 1 | `FundingEventLifecycleService` | monitor-app | Yes | No |
| 2 | `MonitorEnginePlanService` | monitor-app | Yes | No |
| 3 | `EngineMetricsSnapshotView` | monitor-app | Yes (3 sites) | No |
| 4 | `LiquidityAssessmentService` | monitor-app | Yes | No |
| 5 | `FundingObservationMapper` | monitor-app | Yes | No |
| 6 | `FundingApiCandidateSourceService` | monitor-app | In test constructor only (passes to `FundingObservationMapper`) | No |
| 7 | `EngineExecutionService` | engine-app | Yes | Yes (`System::nanoTime` wrapped) |
| 8 | `EngineMetricsPublisher` | engine-app | Yes | No |
| 9 | `EngineRuntimeControlService` | engine-app | Yes | No |

**Verdict:** Well-designed. Tests use `Clock.fixed(instant, ZoneOffset.UTC)` to freeze time. Example from `EngineRuntimeControlServiceTest`:

```java
Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC)
```

---

## Services WITHOUT Clock Injection (Bare `Instant.now()`)

These services call `Instant.now()` directly — time is NOT controllable in tests.

| # | Service | Module | `Instant.now()` call sites |
|---|---------|--------|---------------------------|
| 1 | `SignalCandidateLifecycleService` | monitor-app | 1 (`setReviewedAt`) |
| 2 | `SignalCandidateReviewService` | monitor-app | 2 |
| 3 | `AiSignalAdvisorService` | monitor-app | 2 |
| 4 | `ArmedTradeCommandService` | monitor-app | 1 |
| 5 | `FundingEventCommandService` | monitor-app | 2 |
| 6 | `MonitorOverviewService` | monitor-app | 1 |
| 7 | `OperatorCredentialService` | monitor-app | 3 |
| 8 | `EngineLifecycleRecordService` | monitor-app | 2 |
| 9 | `VenueLatencyProbeService` | monitor-app | 1 |
| 10 | `InstrumentRegistryService` | monitor-app | 1 |
| 11 | `VenueProfileService` | monitor-app | 1 |
| 12 | `VenueDiagnosticsService` | monitor-app | 0 (uses `System.nanoTime()` for timing) |
| 13 | `ApiExceptionHandler` | monitor-app | 1 (in API response) |
| 14 | `InternalEnginePriceController` | monitor-app | 1 (in API response) |
| 15 | `MonitorOverviewController` | monitor-app | 1 (in API response) |

**Impact:** Low-to-medium. Most of these are CRUD operations where the exact timestamp matters less than ordering. The `AiSignalAdvisorService` and `ArmedTradeCommandService` could benefit from Clock injection if trade timing logic is added.

---

## Non-Injectable `System.currentTimeMillis()` Usage

`System.currentTimeMillis()` is used directly (not wrapped in a `LongSupplier`):

| # | Class | Module | Usage |
|---|-------|--------|-------|
| 1 | `BybitCredentialChecker` | monitor-app | HTTP auth timestamp |
| 2 | `KucoinCredentialChecker` | monitor-app | HTTP auth timestamp |
| 3 | `GateCredentialChecker` | monitor-app | HTTP auth timestamp (epoch seconds) |
| 4 | `BitgetCredentialChecker` | monitor-app | HTTP auth timestamp |
| 5 | `LiveExchangeExecutionPort` | engine-app | HTTP auth timestamp (2 sites, 1 epoch seconds) |

**Impact:** Low. These are HTTP request signing timestamps, not trade-decision time sources. Exchange APIs expect the real system clock to match. Testing these requires integration/end-to-end tests, not unit tests.

---

## Non-Injectable `System.nanoTime()` Usage

| # | Class | Module | Usage |
|---|-------|--------|-------|
| 1 | `InstrumentRegistryService` | monitor-app | Elapsed time measurement |
| 2 | `VenueLatencyProbeService` | monitor-app | Latency measurement |
| 3 | `VenueDiagnosticsService` | monitor-app | Credential check latency |
| 4 | `FundingApiCandidateSourceService` | monitor-app | HTTP call duration |

**Impact:** Low. `nanoTime()` is used for performance measurement, not for business logic timing. Only `EngineExecutionService` wraps it in `LongSupplier` — this is the only business-critical timing that needs deterministic test support.

---

## Pattern Summary

```
Production constructors: @Autowired → this(..., Clock.systemUTC())
Test constructors:       package-private → accepts Clock clock
Clock bean:             ❌ NOT DEFINED (no @Bean Clock in any config)
Telegram-bot-app:       ❌ Does NOT use Clock anywhere (non-web, simple HTTP client, no time-dependent logic)
```

## Recommendation

For the current level of test coverage, the existing dual-constructor pattern in 9 services is sufficient. The 12+ services using bare `Instant.now()` represent a testing gap **only if** deterministic time-based unit tests are needed for them. No urgent action required.
