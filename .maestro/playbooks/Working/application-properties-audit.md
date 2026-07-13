---
type: audit
title: Application Properties — Dangerous Defaults & Safe Defaults
created: 2026-07-13
tags:
  - audit
  - configuration
  - spring-boot
  - properties
related:
  - '[[AUDIT-ROUND-3-03]]'
  - '[[startup-shutdown-audit]]'
---

# Application Properties Audit — Dangerous Defaults & Safe Defaults

## All Application YAML Files

| # | File | Module | Purpose |
|---|------|--------|---------|
| 1 | `config/application.yaml` | (shared) | Optional runtime override for monitor-app; mount/copy per deployment |
| 2 | `monitor-app/src/main/resources/platform-core.yml` | monitor-app | Shared core config imported via `spring.config.import` |
| 3 | `monitor-app/src/main/resources/application.yml` | monitor-app | Base config (name, port) |
| 4 | `monitor-app/src/main/resources/application-local-safe.yml` | monitor-app | local-safe profile overrides |
| 5 | `monitor-app/src/main/resources/application-staging.yml` | monitor-app | staging profile overrides |
| 6 | `monitor-app/src/main/resources/application-prod-like.yml` | monitor-app | prod-like profile overrides |
| 7 | `engine-app/src/main/resources/application.yml` | engine-app | Base config (name, port, engine parameters) |
| 8 | `engine-app/src/main/resources/application-local-safe.yml` | engine-app | local-safe profile overrides |
| 9 | `engine-app/src/main/resources/application-testnet.yml` | engine-app | testnet profile overrides |
| 10 | `engine-app/src/main/resources/application-staging.yml` | engine-app | staging profile overrides |
| 11 | `engine-app/src/main/resources/application-prod-like.yml` | engine-app | prod-like profile overrides |
| 12 | `telegram-bot-app/src/main/resources/application.yml` | telegram-bot-app | Base config (token, monitor connection) |
| 13 | `telegram-bot-app/src/main/resources/application-local-safe.yml` | telegram-bot-app | local-safe profile overrides |
| 14 | `telegram-bot-app/src/main/resources/application-staging.yml` | telegram-bot-app | staging profile overrides |

**Total: 14 YAML files across 3 application modules + 1 shared config**

### File Source Location

All files reside under their respective module's `src/main/resources/` directory, except:
- `config/application.yaml` — root-level config directory (loaded at runtime as a sibling to the app JAR)
- `platform-core.yml` — bundled inside `platform-core` JAR, imported via `spring.config.import: optional:classpath:/platform-core.yml`

---

## Dangerous Defaults (Ranked by Severity)

### 🔴 CRITICAL

| # | Property | Default | File | Danger |
|---|----------|---------|------|--------|
| 1 | `security.operators.auth-enabled` | `false` | `platform-core.yml:43` | **No authentication on REST API by default.** Running without the `prod-like` profile leaves all operator API endpoints (arm, approve, reject, credentials) wide open. The default is safe in `local-safe` but a deployment error (forgetting the profile) would expose the full API. |
| 2 | `trading.bitget.mode` | `production` | `platform-core.yml:115` | Bitget venue adapter defaults to **production** URL. Bybit and Gate default to `testnet`. Inconsistent — a developer testing locally without explicitly setting `BITGET_MODE` could hit production Bitget. |
| 3 | `trading.okx.mode` | `production` | `platform-core.yml:124` | Same as Bitget — OKX defaults to production. OKX uses the same base URL for testnet and production (`www.okx.com`), making this particularly dangerous since the `x-simulated-trading: 1` header is the only differentiator. |
| 4 | `trading.kucoin.mode` | `production` | `platform-core.yml:133` | Same as Bitget/OKX — KuCoin defaults to production. |
| 5 | `TRADING_VENUE_ACCESS_MODE` (monitor) | `production` | `platform-core.yml:70` | Monitor venue access defaults to production. While credential storage is disabled by default (making it "safe" for local dev), the UI shows production mode and could mislead operators. |
| 6 | `server.shutdown` (not set) | `immediate` | Spring Boot default | **In-flight trades lost on SIGTERM.** No graceful shutdown configured. `@Scheduled` methods (including 250ms engine loop) and `@Async` methods are killed without draining. An order POST that has been sent but whose response hasn't been received results in an orphaned order. |

### 🟠 HIGH

| # | Property | Default | File | Danger |
|---|----------|---------|------|--------|
| 7 | `TRADING_CANDIDATE_SOURCE_ENABLED` | `true` (matchIfMissing) | `platform-core.yml:77` | External API poller starts automatically in every profile. `local-safe` does NOT override this — `FundingApiCandidateSourceService` will poll `uainvest.com.ua` every 60s in local dev, creating SignalCandidate records. |
| 8 | `spring.task.scheduling.pool.size` (not set) | `1` | Spring Boot default | **Single scheduler thread.** All 6 `@Scheduled` methods share one thread. A blocking operation in any scheduled method (e.g., slow network call in the 60s candidate poll) stalls the 250ms engine execution loop. |
| 9 | `INTERNAL_ENGINE_TOKEN` | empty string `""` | `platform-core.yml:45` | No internal authentication token between monitor and engine by default. The `X-Internal-Token` header is empty. Any service that knows the internal URLs can call engine endpoints without auth. |
| 10 | `MONITOR_OPERATOR_TOKEN` (telegram-bot) | empty string `""` | `telegram-bot-app/application.yml:30` | No operator token for Telegram bot. Bot cannot authenticate to monitor API when operator auth is enabled. |

### 🟡 MEDIUM

| # | Property | Default | File | Danger |
|---|----------|---------|------|--------|
| 11 | Feign connect timeout | `10s` | OpenFeign default | No explicit Feign configuration. Telegram bot uses OpenFeign defaults (10s connect, 60s read) with no retry, no circuit breaker, no custom error decoder — any network issue causes a 60s hang. |
| 12 | `engine.trading-venue-access-mode` | `testnet` | `engine-app/application.yml:23` | This is the engine's own copy and defaults to `testnet` (safe), but it differs from the monitor's default of `production`. Confusing inconsistency between modules. |
| 13 | `TRADING_DEFAULT_ENTRY_SPACING_MS` | `0` | `platform-core.yml:94` | No spacing between entry attempts. Multiple attempts fire simultaneously rather than with deliberate timing. |
| 14 | `management.endpoint.health.probes.enabled` (not set) | `false` | Spring Boot default | K8s readiness/liveness probe endpoints NOT available. But more critically, **no custom HealthIndicator checks any trading-relevant state** — health is UP even when exchange is unreachable, credentials are missing, clock is drifting, or the DB is read-only. |

### 🟢 LOW (Info)

| # | Property | Default | File | Note |
|---|----------|---------|------|------|
| 15 | `spring.threads.virtual.enabled` (not set) | `false` | Spring Boot default | JDK 25 supports virtual threads fully. Enabling could reduce thread overhead for `@Async` methods and venue HTTP calls. Not applicable to scheduler (needs fixed pool). |
| 16 | `spring.jpa.open-in-view` | `false` | `platform-core.yml:33` | ✅ **Good default.** OSIV disabled explicitly. |
| 17 | `spring.jpa.hibernate.ddl-auto` | `validate` | `platform-core.yml:35` | ✅ **Good default.** Hibernate won't modify schema. |
| 18 | `TRADING_METADATA_SCHEDULE_ENABLED` | `false` | `platform-core.yml:84` | ✅ Safe — metadata sync is manual/by-cron by default. |
| 19 | `MONITOR_DEV_TEST_TOOL_ENABLED` | `false` | `platform-core.yml:64` | ✅ Safe — dev test tool disabled by default. |
| 20 | `AI_DEEPSEEK_ENABLED` | `false` | `platform-core.yml:143` | ✅ Safe — AI analysis disabled by default. |

---

## Safe Defaults (Recommended Overrides)

### Must-fix (Critical + High)

| Property | Current Default | Safe Default | Why |
|----------|---------------|-------------|-----|
| `trading.bitget.mode` | `production` | `testnet` | **Inconsistent with bybit/gate.** All venue adapter modes should default to `testnet`. |
| `trading.okx.mode` | `production` | `testnet` | Same as Bitget. |
| `trading.kucoin.mode` | `production` | `testnet` | Same as Bitget. |
| `spring.task.scheduling.pool.size` | `1` (default) | `4` | Prevents scheduler thread starvation. All 6 `@Scheduled` methods get adequate slots. |
| `server.shutdown` | `immediate` | `graceful` | Enables graceful shutdown. In-flight trades get time to complete. Should be combined with `spring.lifecycle.timeout-per-shutdown-phase: 30s`. |
| `TRADING_CANDIDATE_SOURCE_ENABLED` | `true` (matchIfMissing) | `false` (explicit `false` in YAML) | Stop external API polling by default. Deployments explicitly opt in. local-safe profile should override to `false`. |

### Should-fix (Medium)

| Property | Current Default | Safe Default | Why |
|----------|---------------|-------------|-----|
| Feign `connectTimeout` | `10s` (OpenFeign default) | `5s` | Reduce network hang time for telegram-bot API calls. |
| Feign `readTimeout` | `60s` (OpenFeign default) | `10s` | Same reasoning as connect timeout. |
| `TRADING_DEFAULT_ENTRY_SPACING_MS` | `0` | `100` | Add 100ms minimum spacing between entry attempts to avoid race conditions. |

### Info

| Property | Recommended | Why |
|----------|-------------|-----|
| `spring.threads.virtual.enabled` | `true` | JDK 25 support. Reduces thread overhead for `@Async` and RestClient calls. |

---

## Defaults That Differ Between Code and Documentation

| Property | Code Default | `.env.example` Value | Does it differ? | Impact |
|----------|-------------|----------------------|-----------------|--------|
| `SPRING_PROFILES_ACTIVE` | `local-safe` (via `build.gradle`) | `prod-like` | ✅ Intentionally different | `local-safe` for local dev, `prod-like` for deployment — correct |
| `SECURITY_OPERATOR_AUTH_ENABLED` | `false` | `true` | ✅ Intentionally different | `.env.example` is for production deployment docs |
| `CREDENTIALS_STORAGE_ENABLED` | `false` | `true` | ✅ Intentionally different | Same as above |
| `TRADING_VENUE_ACCESS_MODE` | `production` | `production` | ✅ Same | — |
| `TRADING_METADATA_SYNC_ON_STARTUP` | `true` | `true` | ✅ Same | — |
| `ENGINE_EXECUTION_LOOP_ENABLED` | `false` | `false` | ✅ Same | Safe by default in both |
| `ENGINE_LIVE_ORDER_ENABLED` | `false` | `false` | ✅ Same | Safe by default in both |
| `ENGINE_KILL_SWITCH_ENABLED` | `true` | `true` | ✅ Same | Kill switch ON by default in both |
| `INTERNAL_ENGINE_TOKEN` | `""` (empty) | `change-internal-token` (placeholder) | ✅ `.env.example` documents the required placeholder | Correct — actual value must be set per deployment |
| `CREDENTIALS_MASTER_KEY_BASE64` | `""` (empty) | `""` (empty) | ✅ Same | Must be generated per deployment |
| `TELEGRAM_BOT_TOKEN` | `""` (empty) | `""` (empty) | ✅ Same | Must be set per deployment |
| `MONITOR_OPERATOR_TOKEN` | `""` (empty) | `""` (empty) | ✅ Same | Must be set per deployment |

**Conclusive finding:** All documented defaults match the code defaults. `.env.example` values that differ (auth-enabled, credentials-enabled, profiles-active) are intentionally different for production deployment guidance — they are not discrepancies. **No code-vs-documentation drift detected.**

---

## Property With Safe Defaults — Complete Table

### Execution Safety

| Property | Default | Safe? | Override For |
|----------|---------|-------|-------------|
| `engine.execution-loop-enabled` | `false` | ✅ | testnet → `true` |
| `engine.live-order-enabled` | `false` | ✅ | testnet → `true` |
| `engine.kill-switch-enabled` | `true` | ✅ | testnet → `false` |
| `engine.live-enabled-venues` | `bybit,gate` | ✅ | Full deployment → all 5 venues |
| `engine.max-notional-usd` | `25` | ✅ | Production → higher value |
| `engine.trading-venue-access-mode` | `testnet` | ✅ | Production → `production` |

### Security

| Property | Default | Safe? | Override For |
|----------|---------|-------|-------------|
| `security.operators.auth-enabled` | `false` | ⚠️ **Dangerous** | staging/prod-like → `true` |
| `credentials.storage.enabled` | `false` | ✅ | staging/prod-like → `true` |
| `credentials.storage.require-master-key-on-startup` | `true` | ✅ | — |
| `INTERNAL_ENGINE_TOKEN` | `""` | ⚠️ **Dangerous** | Must set per deployment |
| `monitor.engine-metrics.enabled` | `false` | ✅ | staging/prod-like → `true` |
| `MONITOR_DEV_TEST_TOOL_ENABLED` | `false` | ✅ | Set `true` only for testnet debugging |

### Venue Mode Defaults

| Property | Default | Safe? | Override For |
|----------|---------|-------|-------------|
| `trading.bybit.mode` | `testnet` | ✅ | Production → `production` |
| `trading.gate.mode` | `testnet` | ✅ | Production → `production` |
| `trading.bitget.mode` | `production` | ❌ **Dangerous** | — |
| `trading.okx.mode` | `production` | ❌ **Dangerous** | — |
| `trading.kucoin.mode` | `production` | ❌ **Dangerous** | — |
| `trading.venue-access.mode` (monitor) | `production` | ⚠️ | Debatable — safe with credentials disabled; inconsistent naming vs engine's `testnet` |

### Scheduling

| Property | Default | Safe? | Override For |
|----------|---------|-------|-------------|
| `spring.task.scheduling.pool.size` | `1` | ⚠️ **Under-resourced** | Minimum `4` |
| `spring.task.execution.pool.core-size` | `8` (default) | ✅ | Adequate for 7 `@Async` methods |
| `trading.candidate-source.enabled` | `true` (matchIfMissing) | ⚠️ Should default to `false` | Explicitly opt-in per deployment |
| `trading.candidate-source.refresh-interval-seconds` | `60` | ✅ | Reasonable |
| `trading.metadata.schedule-enabled` | `false` | ✅ | — |

### Shutdown

| Property | Default | Safe? | Override For |
|----------|---------|-------|-------------|
| `server.shutdown` | `immediate` | ❌ **Dangerous** | Should be `graceful` |
| `spring.lifecycle.timeout-per-shutdown-phase` | `30s` (default) | ✅ | Adequate |

### HTTP Clients

| Property | Default | Safe? | Override For |
|----------|---------|-------|-------------|
| `trading.http.connect-timeout-ms` | `1000` | ✅ | — |
| `trading.http.request-timeout-ms` | `5000` | ⚠️ **Unused** (declared but never wired to HttpClient) | Wire to the `venueHttpClient` bean |
| Feign connect timeout | `10s` (default) | ⚠️ No explicit config | Should be `5000ms` |
| Feign read timeout | `60s` (default) | ⚠️ No explicit config | Should be `10000ms` |

### Database

| Property | Default | Safe? | Override For |
|----------|---------|-------|-------------|
| `spring.jpa.hibernate.ddl-auto` | `validate` | ✅ | Never change |
| `spring.jpa.open-in-view` | `false` | ✅ | Never change |
| `spring.datasource.hikari.maximum-pool-size` | `2` | ✅ | Adequate for SQLite |
| `spring.flyway.out-of-order` | `true` | ⚠️ | Allows out-of-order migration execution in multi-instance deploys — intentional for MVP |

### Trading Parameters

| Property | Default | Safe? | Override For |
|----------|---------|-------|-------------|
| `trading.candidates.dedupe-window-minutes` | `15` | ✅ | Reasonable |
| `trading.auto-approval.enabled` | `false` | ✅ | Should only enable for authorized testnet |
| `trading.auto-approval.max-notional-usd` | `10` | ✅ | Small default |
| `trading.preparation.default-entry-attempt-count` | `1` | ✅ | — |
| `trading.preparation.default-entry-spacing-ms` | `0` | ⚠️ | Should set to `100` minimum |
| `trading.preparation.default-manual-latency-adjustment-ms` | `0` | ✅ | — |

### AI/DeepSeek

| Property | Default | Safe? | Override For |
|----------|---------|-------|-------------|
| `ai.deepseek.enabled` | `false` | ✅ | Opt-in per deployment |
| `ai.deepseek.timeout-seconds` | `30` | ✅ | Reasonable |

---

## Summary of Recommended Changes

### Critical fixes (prevent potential loss of funds)

1. **Set `trading.bitget.mode: testnet`, `trading.okx.mode: testnet`, `trading.kucoin.mode: testnet`** in `platform-core.yml` — all venue modes should default to `testnet` for consistency.
2. **Set `server.shutdown: graceful`** in all three application base YAMLs and add `spring.lifecycle.timeout-per-shutdown-phase: 30s`.
3. **Set `spring.task.scheduling.pool.size: 4`** in all three application base YAMLs.

### High-priority fixes

4. **Add `TRADING_CANDIDATE_SOURCE_ENABLED: false` to `application-local-safe.yml`** — stop external API polling in local dev.
5. **Set `spring.cloud.openfeign.client.config.default.connect-timeout: 5000` and `read-timeout: 10000`** in telegram-bot-app.

### Monitoring / Observability

6. **Add `management.endpoint.health.probes.enabled: true`** in all modules that run a web server.
7. **Create `TradingReadinessHealthIndicator`** — per prior audit recommendation in `Working/health-indicator-audit.md`.
