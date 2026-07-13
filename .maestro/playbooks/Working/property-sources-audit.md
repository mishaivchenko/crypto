---
type: audit
title: Property Sources Audit — Complete Inventory and Cross-Reference
created: 2026-07-13
tags:
  - audit
  - configuration
  - properties
  - secrets
related:
  - '[[application-properties-audit]]'
  - '[[profile-inventory]]'
---

# Property Sources Audit

## Overview

This report covers the full property lifecycle across all 3 modules (monitor-app, engine-app, telegram-bot-app), cross-referencing declared/provided sources with consumed properties. It completes Section 8 — Configuration Inventory tasks for Property Sources, Environment Variables, and Runtime Configuration.

---

## Part 1: `@ConfigurationProperties` Classes

### Summary

| Module | Property Classes | Total Leaf Properties | With `@Validated` |
|---|---|---|---|
| monitor-app | 15 | 47 | **0** |
| engine-app | 2 | 13 | **0** |
| telegram-bot-app | 3 | 13 | **0** |
| **Total** | **20** | **73** | **0** |

### All Classes (alphabetical by prefix)

| # | Class | Prefix | Leaf Props | @Validated? |
|---|---|---|---|---|
| 1 | `AutoApprovalProperties` | `trading.auto-approval` | 2 | No |
| 2 | `CandidateProperties` | `trading.candidates` | 1 | No |
| 3 | `CredentialStorageProperties` | `credentials.storage` | 3 | No |
| 4 | `DeepSeekProperties` | `ai.deepseek` | 5 | No |
| 5 | `EngineMetricsPublishProperties` | `engine.metrics-publish` | 2 | No |
| 6 | `EngineProperties` | `engine` | 11 | No |
| 7 | `EnvironmentLinksProperties` | `env.links` | 6 | No |
| 8 | `FundingCandidateSourceProperties` | `trading.candidate-source` | 4 | No |
| 9 | `LiquidityProperties` | `monitor.liquidity` | 9 | No |
| 10 | `MetadataSyncProperties` | `trading.metadata` | 6 | No |
| 11 | `MonitorDevTestToolProperties` | `monitor.dev-test-tool` | 3 | No |
| 12 | `MonitorEngineControlProperties` | `monitor.engine-control` | 2 | No |
| 13 | `MonitorEngineMetricsProperties` | `monitor.engine-metrics` | 1 | No |
| 14 | `MonitorEnginePlanProperties` | `monitor.engine-plan` | 3 | No |
| 15 | `MonitorProperties` | `monitor` | 3 | No |
| 16 | `MonitorRiskProperties` | `monitor.risk` | 1 | No |
| 17 | `OperatorSecurityProperties` | `security.operators` | 3 | No |
| 18 | `TelegramBotProperties` | `telegram.bot` | 4 | No |
| 19 | `TradePreparationProperties` | `trading.preparation` | 3 | No |
| 20 | `VenueHttpProperties` | `trading.http` | 3 | No |

**Key finding:** NONE of the 20 `@ConfigurationProperties` classes use `@Validated`. This means no startup-time validation of property values (e.g., a negative timeout, empty required URL) — invalid property values are silently accepted and may cause runtime errors.

---

## Part 2: `@Value` Property Injections

### Summary

| Module | Count | Properties Consumed |
|---|---|---|
| monitor-app | 3 (duplicated) | `trading.gate.contracts-base-url` |
| telegram-bot-app | 1 | `monitor.operator-token` |
| engine-app | 0 | — |
| **Total** | **4** | **2 unique properties** |

### Details

| File | Expression | Property | Default Chain |
|---|---|---|---|
| `GateOrderBookAdapter.java:34` | `${trading.gate.contracts-base-url:${GATE_CONTRACTS_BASE_URL:https://...}}` | `trading.gate.contracts-base-url` | `GATE_CONTRACTS_BASE_URL` → hardcoded URL |
| `GateMarkPriceAdapter.java:29` | same | same | same |
| `GateMetadataAdapter.java:37` | same | same | same |
| `MonitorFeignConfig.java:13` | `${monitor.operator-token:}` | `monitor.operator-token` | `""` (empty string) |

**Key finding:** The Gate contracts-base-url expression is **duplicated verbatim in 3 files** — a DRY violation that should be centralized into a `@ConfigurationProperties` class.

---

## Part 3: `Environment.getProperty()` Calls

### Summary

| Module | Direct Calls | Classes Involved |
|---|---|---|
| monitor-app | 23 | 11 classes |
| engine-app | 10 | 1 class (`LiveExchangeExecutionPort`) |
| **Total** | **33** | **12 classes** |

### monitor-app (23 calls)

**`SqliteDataDirectoryEnvironmentPostProcessor`** (1 call):
- `spring.datasource.url` (no default)

**`OperatorCredentialService`** (4 calls, dynamic keys):
- `trading.{venue}.{mode}.api-key` — no default
- `trading.{venue}.{mode}.secret-key` — no default
- `trading.{venue}.{mode}.passphrase` — no default
- `trading.{venue}.{mode}.base-url` — no default

**`VenueProfileService`** (8 calls):
- `trading.{venue}.production.base-url` — no default (dynamic venue)
- `trading.{venue}.{mode}.base-url` — no default
- `trading.{venue}.{mode}.api-key` — no default
- `trading.{venue}.{mode}.secret-key` — no default
- `trading.{venue}.{mode}.passphrase` — no default
- `trading.{venue}.mode` — falls back to first available mode
- `trading.venue-access.mode` — no default
- `trading.bybit.mode` — falls back to `"production"`

**`VenueDiagnosticsService`** (2 calls):
- `trading.{venue}.metadata-base-url` — nested fallback
- `trading.{venue}.contracts-base-url` — no default

**Exchange adapters (8 classes, 1 call each):**
- `trading.{venue}.metadata-base-url` — fallback: resolved credentials URL

### engine-app (10 calls via helper `property()`)

All in `LiveExchangeExecutionPort`:
- `engine.credentials.{venue}.{name}` — no default (direct call)
- `engine.trading-venue-access-mode` — default `"testnet"`
- `engine.live-order-enabled` — default `false`
- `engine.kill-switch-enabled` — default `true`
- `engine.max-notional-usd` — default `25`
- `engine.metadata-max-age-minutes` — default `240`
- `engine.latency-max-age-minutes` — default `1440`
- `engine.{venue}.{mode}-base-url` — dynamic, per-venue fallback via switch
- `engine.live-enabled-venues` — default `"bybit,gate"`

---

## Part 4: `System.getenv()` Calls

### Java Source — Zero direct calls

**No Java source file** calls `System.getenv()` or `System.getProperty()` directly. All environment variable access is routed through Spring's `Environment` abstraction (with a custom `DotenvEnvironmentPostProcessor` that loads `.env`/`.env.local` files into the Spring property source chain).

### build.gradle — 10 calls (all optional with fallbacks)

| Line | Call | Fallback |
|---|---|---|
| 71 | `System.getenv('NVD_API_KEY')` | (optional — lower rate limiting) |
| 90 | `System.getenv('SPRING_PROFILES_ACTIVE')` | `localBootProfile` (`local-safe`) |
| 91 | `System.getenv('INTERNAL_ENGINE_TOKEN')` | `localInternalEngineToken` |
| 92-93 | `System.getenv('MONITOR_ENGINE_CONTROL_INTERNAL_TOKEN')` | cascading fallback |
| 101 | `System.getenv('SPRING_PROFILES_ACTIVE')` | `localBootProfile` |
| 102 | `System.getenv('INTERNAL_ENGINE_TOKEN')` | `localInternalEngineToken` |
| 110 | `System.getenv('SPRING_PROFILES_ACTIVE')` | `localBootProfile` |
| 111 | `System.getenv('TELEGRAM_BOT_TOKEN')` | `''` (empty) |
| 112 | `System.getenv('MONITOR_BASE_URL')` | `'http://localhost:8090'` |

---

## Part 5: Cross-Reference — Duplicates, Unused, Undocumented, Typos, Mismatches

### 5A. Duplicate Property Names Across Files

The following properties appear in multiple YAML files **as explicit overrides** (expected Spring Boot profile pattern):

| Property | Files Where Set |
|---|---|
| `engine.execution-loop-enabled` | application.yml, local-safe, testnet, staging, prod-like (5 files) |
| `engine.live-order-enabled` | application.yml, testnet |
| `engine.kill-switch-enabled` | application.yml, testnet |
| `engine.metrics-publish.enabled` | application.yml, local-safe, testnet, staging, prod-like (5 files) |
| `engine.monitor-base-url` | application.yml, testnet |
| `security.operators.auth-enabled` | platform-core.yml, local-safe, staging, prod-like |
| `credentials.storage.enabled` | platform-core.yml, local-safe, staging, prod-like |
| `credentials.storage.require-master-key-on-startup` | platform-core.yml, local-safe, staging, prod-like |
| `monitor.engine-metrics.enabled` | platform-core.yml, local-safe, staging, prod-like |
| `trading.metadata.require-credentials-on-startup` | platform-core.yml, local-safe, staging, prod-like |
| `trading.metadata.sync-on-startup` | platform-core.yml, local-safe |
| `monitor.base-url` | telegram-bot-app/application.yml, telegram-bot-app/local-safe, staging |
| `telegram.bot.token` | telegram-bot-app/application.yml, local-safe, staging |

All of these are **intentional** profile overrides — not bugs.

### 5B. Properties Declared but Never Read by Any Consumer

After cross-referencing all 20 `@ConfigurationProperties` classes, all `@Value` annotations, all `Environment.getProperty()` calls, and all `@ConditionalOnProperty`/`@Scheduled` SpEL expressions:

| Property | Declared In | Why Not Read |
|---|---|---|
| `monitor.ui.version: 2.0.0` | `monitor-app/application.yml` | **NOT consumed by any @ConfigurationProperties, @Value, or Environment.getProperty()** — the version is only hardcoded as a Java string literal in `MonitorOverviewService.java`. This property exists in YAML as documentation but is never read from the Spring environment. |

This is the **only** custom property declared in YAML that is never programmatically consumed. It exists solely as documentation.

### 5C. Properties Read by Code but Undocumented in Any YAML

These properties are consumed by code but **do not appear in any YAML file** as explicit `key: value` declarations:

| Property | Consumer | Default |
|---|---|---|
| `trading.{venue}.{mode}.api-key` | `OperatorCredentialService`, `VenueProfileService` | None (null) |
| `trading.{venue}.{mode}.secret-key` | same | None |
| `trading.{venue}.{mode}.passphrase` | same | None |
| `trading.{venue}.{mode}.base-url` | same | None |
| `trading.{venue}.production.base-url` | `VenueProfileService` | None |
| `trading.bybit.mode` | `VenueProfileService` | `"production"` |
| `trading.bybit.metadata-base-url` | `BybitMetadataAdapter`, `BybitMarkPriceAdapter` | dynamic |
| `trading.gate.contracts-base-url` | 3x Gate `@Value` | `GATE_CONTRACTS_BASE_URL` → hardcoded |
| `trading.gate.metadata-base-url` | Gate adapters | dynamic |
| `trading.okx.metadata-base-url` | OKX adapters | dynamic |
| `trading.kucoin.metadata-base-url` | Kucoin adapters | dynamic |
| `trading.bitget.metadata-base-url` | Bitget adapters | dynamic |
| `trading.{venue}.metadata-base-url` | `VenueDiagnosticsService` | nested fallback |
| `trading.{venue}.contracts-base-url` (except gate) | `VenueDiagnosticsService` | None |
| `engine.credentials.{venue}.{name}` | `LiveExchangeExecutionPort` | None |

**Notes:**
- All `trading.{venue}.{mode}.` properties are **dynamic** — their keys depend on the venue name and mode. They are not individually enumerated in YAML files but are covered by the `platform-core.yml` env-var bindings under each venue section.
- `trading.bybit.metadata-base-url` appears in `platform-core.yml` as `BYBIT_METADATA_BASE_URL` env-var binding
- The dynamic `trading.{venue}.contracts-base-url` for venues other than Gate has NO default anywhere — only Gate has `GATE_CONTRACTS_BASE_URL`

### 5D. Property Name Typos / Inconsistencies

After comparing all property key names across declarations and consumers:

| Issue | Property | Details |
|---|---|---|
| **Inconsistent venue naming** | `engine.{venue}.{mode}-base-url` vs `trading.{venue}.{mode}.base-url` | Engine uses kebab-case mode with hyphen between mode and base-url (`testnet-base-url`). Monitor uses dot-separated mode group with `.base-url` child. Same concept, different property patterns. |
| **`engine.gate.production-base-url`** differs from `platform-core.yml` `trading.gate.production.base-url` | Engine: `https://fx-api.gateio.ws/api/v4` (perpetual futures). Monitor: `https://api.gateio.ws/api/v4` (spot). **This is a real behavioral difference** — Engine uses perpetual futures API, monitor uses spot API. |
| **`MONITOR_ENGINE_CONTROL_INTERNAL_TOKEN`** env-var name | Controls internal token for monitor→engine API calls but named `MONITOR_ENGINE_CONTROL_*` rather than `MONITOR_ENGINE_*` — correct but unusual |
| **`engine.execution-scheduler-tick-ms`** vs **`engine.execution-loop-interval-ms`** | Two timer-related properties with similar purposes but different names. `scheduler-tick-ms` drives the `@Scheduled` method, `loop-interval-ms` is the `runtime.sleep()` between loop iterations — they're different concerns but the naming implies they might be the same. |

### 5E. Differently-Named Properties Between Monitor and Engine

| Concept | Monitor Property | Engine Property | Consistent? |
|---|---|---|---|
| Venue API key | `trading.{venue}.{mode}.api-key` | `engine.credentials.{venue}.api-key` | **No** — different prefix and structure |
| Venue secret key | `trading.{venue}.{mode}.secret-key` | `engine.credentials.{venue}.secret-key` | **No** |
| Venue passphrase | `trading.{venue}.{mode}.passphrase` | `engine.credentials.{venue}.passphrase` | **No** |
| Venue base URL | `trading.{venue}.{mode}.base-url` | `engine.{venue}.{mode}-base-url` | **No** — different separator |
| Venue access mode | `trading.venue-access.mode` | `engine.trading-venue-access-mode` | **No** — different prefix |
| Venue live list | — | `engine.live-enabled-venues` | Engine-only concept |
| Metrics enabled | `monitor.engine-metrics.enabled` | `engine.metrics-publish.enabled` | **No** — different prefixes |
| Max notional | `trading.auto-approval.max-notional-usd` | `engine.max-notional-usd` | **No** — different prefixes |
| Metadata max age | — | `engine.metadata-max-age-minutes` | Engine-only (cache TTL) |
| Metadata sync interval | `trading.metadata.sync-interval-minutes` | — | Monitor-only (runs sync) |
| Engine plan lookahead | `monitor.engine-plan.lookahead-minutes` | — | Monitor-only (plan generation) |

**Key finding:** Engine and monitor share **zero** property prefix namespaces for common concepts. All shared concepts (credentials, URLs, modes) use different naming conventions. This is partially justified (separate Spring contexts, separate property sources) but makes system-wide configuration verification impossible without external tooling.

---

## Part 6: Additional Properties Consumed Outside @ConfigurationProperties

Properties consumed via **SpEL in annotations** (not through any property class):

| Property | Annotation | Default | File |
|---|---|---|---|
| `engine.execution-scheduler-tick-ms` | `@Scheduled(fixedDelayString)` | 250 | `EngineExecutionScheduler.java` |
| `engine.metrics-publish.interval-ms` | `@Scheduled(fixedDelayString)` | 15000 | `EngineMetricsPublisher.java` |
| `trading.metadata.sync-interval-minutes` | `@Scheduled(fixedDelayString)` | 240 | `InstrumentMetadataSyncRunner.java` |
| `trading.candidate-source.refresh-interval-seconds` | `@Scheduled(fixedDelayString)` | 60 | `FundingApiCandidateSourceService.java` |
| `telegram.bot.signal-poll-interval-ms` | `@Scheduled(fixedDelayString)` | 30000 | `SignalNotificationScheduler.java` |
| `telegram.bot.signal-poll-interval-ms` | `@Scheduled(fixedDelayString)` | 30000 | `TradeNotificationScheduler.java` |

And via `@ConditionalOnProperty`:

| Property | Name | matchIfMissing | Files (6) |
|---|---|---|---|
| `monitor.engine-metrics.enabled` | `enabled` | false | 5 engine-metrics beans |
| `trading.candidate-source.enabled` | `enabled` | **true** | `FundingApiCandidateSourceService` |
| `engine.metrics-publish.enabled` | `enabled` | false (default) | `EngineMetricsPublisher` |
| `telegram.bot.token` | (root) | false | 4 beans (Bot, schedulers, config) |

---

## Part 7: Property Source Hierarchy

```
1. Env vars (OS / Docker / Compose)
2. .env / .env.local  (via DotenvEnvironmentPostProcessor)
3. application-{profile}.yml  (profile-specific, highest priority among files)
4. application.yml  (base per-module)
5. platform-core.yml  (shared defaults, monitor-app only, loaded via spring.config.import)
6. Spring Boot defaults
```

All 3 modules follow the standard Spring Boot precedence. `platform-core.yml` is loaded via `spring.config.import: optional:classpath:/platform-core.yml` in `monitor-app/application.yml` only. Engine-app and telegram-bot-app do not import `platform-core.yml`.

---

## Part 8: Recommendations

1. **Add `@Validated` to all `@ConfigurationProperties` classes** — enables startup-time validation of constraints (e.g., `@Min`, `@NotEmpty`)
2. **Consolidate Gate `@Value` duplication** — move `trading.gate.contracts-base-url` into a `@ConfigurationProperties` class shared across Gate adapters
3. **Monitor-engine property unification** — version 3.0 could adopt consistent property naming between modules (`trading.*` conventions for shared concepts)
4. **Consider removing `monitor.ui.version`** YAML declaration — it's never consumed from the environment. Either wire `MonitorOverviewService` to read it from properties or remove it.
5. **Add `trading.{venue}.contracts-base-url` defaults** for non-Gate venues (OKX, KuCoin, Bitget, Bybit) — currently only Gate has this property defined
6. **Document all consumed properties** — create or update `docs/property-reference.md` with every property key, its consumer, default, and expected value range
