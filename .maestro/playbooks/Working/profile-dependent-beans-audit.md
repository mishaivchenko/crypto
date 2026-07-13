---
type: analysis
title: Profile-Dependent Beans Audit
created: 2026-07-13
tags:
  - audit
  - spring-boot
  - profiles
  - beans
  - conditional
related:
  - '[[AUDIT-ROUND-3-03]]'
---

# Profile-Dependent Beans Audit

## Summary

**Zero `@Profile` annotations** exist anywhere in the codebase across all 3 modules. The project manages profile-specific behavior entirely through:

1. **Property values** in per-profile `application-{profile}.yml` files — all beans exist unconditionally, but their behavior is controlled by property values
2. **`@ConditionalOnProperty` annotations** — 11 beans gated on features (not profiles), enabling/disabling entire subsystems

## Bean Inventory by Profile

### engine-app (13 production classes)

| Bean | Conditional? | Creates at profile? | Behavior when |
|------|-------------|-------------------|--------------|
| `EngineController` | No — always created | all | REST endpoints always available |
| `EngineCredentialCache` | No — always created | all | Loads credentials on startup (retries async) |
| `EngineCredentialStatusController` | No — always created | all | Credential status REST endpoint always available |
| `EngineExecutionScheduler` | No — always created | all | Scheduler always active (250ms tick); guard checks `execution-loop-enabled` |
| `EngineExecutionService` | No — always created | all | Order execution service always present; `liveOrderEnabled` guard prevents live orders |
| `EngineMetricsPublisher` | `engine.metrics-publish.enabled=true` | prod-like ✓, staging ✓, local-safe ✗, testnet ✗ | Published only when enabled |
| `EngineModuleConfiguration` | No — always created | all | Imports PlanClient, PlanService, Controller |
| `EnginePlanClient` | No — always created | all | HTTP client to monitor always created |
| `EnginePlanService` | No — always created | all | Plan processing always available |
| `EngineTelemetryService` | No — always created | all | Telemetry always available |
| `EngineRuntimeControlService` | No — always created | all | Runtime kill switch / loop control always available |
| `CredentialAwareExecutionPort` | No — always created | all | Guards orders; without credentials always returns FAILED |
| `EngineProperties` | No — always created | all | Configuration properties always bound |
| `EngineMetricsPublishProperties` | No — always created | all | Metrics properties always bound |

**Key insight:** 12/13 engine-app beans exist unconditionally. Only `EngineMetricsPublisher` is conditional (disabled in local-safe and testnet). Even with live orders OFF, the entire engine execution stack exists — it just guards at runtime.

### monitor-app

#### Beans gated on `monitor.engine-metrics.enabled=true` (5 beans)

These beans form a complete sub-graph for engine observability:

| Bean | Enabled by default? | Profile behavior |
|------|--------------------|-----------------|
| `InternalEngineMetricsController` | No (matchIfMissing=false) | Only prod-like, staging |
| `EngineMetricsIngestService` | No | Only prod-like, staging |
| `EngineMetricsSnapshotStore` | No | Only prod-like, staging |
| `EngineMetricsSnapshotView` | No | Only prod-like, staging |
| `EngineMetricsMeterBinder` | No | Only prod-like, staging |

#### Bean gated on `trading.candidate-source.enabled` (1 bean)

| Bean | Enabled by default? | Profile behavior |
|------|--------------------|-----------------|
| `FundingApiCandidateSourceService` | Yes (matchIfMissing=true) | Always enabled unless explicitly disabled |

#### Unconditional beans (always created across ALL profiles)

The majority of monitor-app beans are unconditional — they exist regardless of profile:

- **REST controllers** (17+ controllers): `FundingEventController`, `ArmedTradeController`, `SignalCandidateController`, `EngineControlController`, etc.
- **Services**: `FundingEventLifecycleService`, `FundingEventCommandService`, `ArmedTradeCommandService`, `SignalCandidateLifecycleService`, `MonitorOverviewService`, `MonitorEnginePlanService`, `AutoApprovalPipelineService`, etc.
- **Adapters**: 20 venue adapter classes (5 venues × 4 ports), `VenueHttpClientConfig.venueHttpClient()` bean
- **Security**: `OperatorCredentialService`, `OperatorAuthenticationFilter`, `OperatorAccountService`
- **Persistence**: 18 JPA entities, 15 repositories, Flyway migrations
- **Other**: `VenueProfileService`, `VenueLatencyProbeService`, `InstrumentRegistryService`, `VenueDiagnosticsService`

### telegram-bot-app

#### Beans gated on `telegram.bot.token` being non-empty (4 beans)

| Bean | Conditional? | Profile behavior |
|------|-------------|-----------------|
| `TelegramBotConfig.telegramBot()` | `@ConditionalOnProperty(name = "telegram.bot.token", matchIfMissing = false)` | Created only if token is provided |
| `FundingBot` | Same condition | Created only if token is provided |
| `SignalNotificationScheduler` | Same condition | Created only if token is provided |
| `TradeNotificationScheduler` | Same condition | Created only if token is provided |

#### Unconditional beans

| Bean | Notes |
|------|-------|
| `MonitorFeignConfig.monitorOperatorTokenInterceptor()` | Always created; short-circuits if token is blank |
| `MonitorApiClient` (Feign) | Always created |
| Command handlers, message handlers | Always created (but inactive without the bot) |

## Profile-Specific Behavior Summary

### local-safe
- **Engine loop**: OFF (bean exists, property=false)
- **Live orders**: OFF (bean exists, property=false)
- **Metrics**: OFF (EngineMetricsPublisher not created, monitor metrics beans not created)
- **Auth**: OFF
- **Credential storage**: OFF (no master key required)
- **Metadata sync**: OFF (no startup sync)
- **Telegram**: Token-dependent
- **DeepSeek AI**: OFF

### testnet (engine-app only)
- **Engine loop**: ON (`execution-loop-enabled: true`)
- **Live orders**: ON (`live-order-enabled: true`)
- **Kill switch**: OFF (`kill-switch-enabled: false`)
- **Access mode**: `testnet`
- **Limited venues**: `gate` only (live-enabled-venues)
- **Metrics**: OFF (EngineMetricsPublisher not created)
- **Loop interval**: 2000ms (slower for safety)

### staging
- **Engine loop**: OFF (bean exists, property=false)
- **Live orders**: OFF (property=false)
- **Metrics**: ON (both engine publisher and monitor ingestion)
- **Auth**: ON
- **Credential storage**: ON (master key required)
- **Metadata sync**: ON (requires explicit CREDENTIALS=true)

### prod-like
- **Engine loop**: OFF (property=false — must explicitly enable)
- **Live orders**: OFF (property=false — must explicitly enable)
- **Metrics**: ON
- **Auth**: ON
- **Credential storage**: ON (master key required)
- **Metadata sync**: ON (requires explicit CREDENTIALS=true)

## Beans Created Even When Live Trading Is Disabled

Nearly the entire application context exists regardless of whether live trading is enabled:

1. **All engine-app beans** (12/13): EngineExecutionScheduler, EngineExecutionService, CredentialAwareExecutionPort, EnginePlanClient, all controllers, all services — all present. The execution loop and live order guards (`EngineRuntimeControlService`) gate at runtime via property checks, not bean presence.

2. **All monitor-app venue adapters**: 20 classes ready to call exchange APIs. Without credentials they fail gracefully, but the HTTP client, Feign configs, and adapter instances are all in the context.

3. **All monitor-app controllers**: All REST endpoints are available including `InternalEnginePlanController`, `EngineControlController` — even with loop disabled.

4. **Auto-approval pipeline**: All `AutoApprovalPipelineService` beans are unconditional — auto-approval can trigger trades at any time via API.

5. **Unconditional Telegram beans**: `MonitorFeignConfig` creates the Feign `RequestInterceptor` even when no bot token is set.

## Design Pattern: Property-Driven, Not Bean-Driven

The project deliberately avoids `@Profile` annotations. Instead:

- **All beans are always created** (with 10 exceptions gated by `@ConditionalOnProperty`)
- **Behavior is controlled by property values** per profile: `execution-loop-enabled`, `live-order-enabled`, `auth-enabled`
- **Benefits**: Profiles can be combined; properties can be overridden at deployment via environment variables; testing is simpler (no profile switching needed)
- **Trade-off**: Marginal startup cost from creating beans that may never execute (the engine loop tick runs but immediately exits)
