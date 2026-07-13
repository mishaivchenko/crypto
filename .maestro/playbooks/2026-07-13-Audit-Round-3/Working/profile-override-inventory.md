---
type: audit
title: Profile Parameter Override Inventory & Dangerous Inheritance Analysis
created: 2026-07-13
tags:
  - audit
  - configuration
  - spring-boot
  - profiles
  - security
related:
  - '[[profile-differences-comparison]]'
  - '[[local-safe-profile-verification]]'
  - '[[testnet-profile-verification]]'
  - '[[staging-profile-verification]]'
  - '[[prod-like-profile-verification]]'
  - '[[application-properties-audit]]'
---

# Profile Parameter Override Inventory & Dangerous Inheritance Analysis

## Part 1: Complete Parameter Override Inventory

Every parameter explicitly set in every profile YAML file, across all 3 modules. Parameters not listed here are **inherited unchanged** from their base default (in `application.yml`, `platform-core.yml`, or Spring Boot defaults).

---

### 1. monitor-app

#### Base defaults source: `platform-core.yml` + `application.yml`

**Property source order (lowest → highest priority):**
1. Spring Boot defaults
2. `platform-core.yml` (shared config, imported via `spring.config.import`)
3. `application.yml` (module-specific base)
4. `application-{profile}.yml` (profile-specific overrides)
5. Environment variables / JVM args / CLI args / Docker Compose `environment:` block

#### application-local-safe.yml — 7 explicit overrides

| # | Property | Override Value | Base Default (from platform-core or application.yml) | Env Var Override |
|---|----------|---------------|------------------------------------------------------|------------------|
| 1 | `security.operators.auth-enabled` | `false` | `${SECURITY_OPERATOR_AUTH_ENABLED:false}` (same) | `SECURITY_OPERATOR_AUTH_ENABLED` |
| 2 | `credentials.storage.enabled` | `false` | `${CREDENTIALS_STORAGE_ENABLED:false}` (same) | `CREDENTIALS_STORAGE_ENABLED` |
| 3 | `credentials.storage.require-master-key-on-startup` | `false` | `${CREDENTIALS_REQUIRE_MASTER_KEY_ON_STARTUP:true}` (overrides default `true`) | `CREDENTIALS_REQUIRE_MASTER_KEY_ON_STARTUP` |
| 4 | `monitor.engine-metrics.enabled` | `false` | `${MONITOR_ENGINE_METRICS_ENABLED:false}` (same) | `MONITOR_ENGINE_METRICS_ENABLED` |
| 5 | `trading.metadata.require-credentials-on-startup` | `false` | `${TRADING_METADATA_REQUIRE_CREDENTIALS_ON_STARTUP:false}` (same) | `TRADING_METADATA_REQUIRE_CREDENTIALS_ON_STARTUP` |
| 6 | `trading.metadata.sync-on-startup` | `false` | `${TRADING_METADATA_SYNC_ON_STARTUP:true}` **overrides default `true` → `false`** | `TRADING_METADATA_SYNC_ON_STARTUP` |
| 7 | `ai.deepseek.enabled` | `false` | `${AI_DEEPSEEK_ENABLED:false}` (same) | `AI_DEEPSEEK_ENABLED` |

**Notes on local-safe overrides:**
- Overrides #1, #2, #4, #5, #7 are explicit re-statements of base defaults (documentation-style)
- Override #3 changes the base default from `true` to `false` — meaningful difference
- Override #6 changes the base default from `true` to `false` — meaningful difference
- `trading.candidate-source.enabled` is **NOT overridden** — inherits `true` from base (external API poll active during local dev)

#### application-staging.yml — 5 explicit overrides

| # | Property | Override Value | Base Default | Δ from base default? |
|---|----------|---------------|-------------|---------------------|
| 1 | `security.operators.auth-enabled` | `true` | `false` | **YES** — OFF → ON |
| 2 | `credentials.storage.enabled` | `true` | `false` | **YES** — OFF → ON |
| 3 | `credentials.storage.require-master-key-on-startup` | `true` | `true` | No (same as default) |
| 4 | `monitor.engine-metrics.enabled` | `true` | `false` | **YES** — OFF → ON |
| 5 | `trading.metadata.require-credentials-on-startup` | `false` | `false` | No (same as default) |

**Notes on staging overrides:**
- Does NOT override `trading.metadata.sync-on-startup` — inherits `true` from base
- Does NOT override `ai.deepseek.enabled` — inherits `false` from base
- Does NOT override `trading.candidate-source.enabled` — inherits `true` from base
- 3 out of 5 overrides are meaningful changes from base defaults

#### application-prod-like.yml — 5 explicit overrides

| # | Property | Override Value | Base Default | Δ from base default? |
|---|----------|---------------|-------------|---------------------|
| 1 | `security.operators.auth-enabled` | `true` | `false` | **YES** — OFF → ON |
| 2 | `credentials.storage.enabled` | `true` | `false` | **YES** — OFF → ON |
| 3 | `credentials.storage.require-master-key-on-startup` | `true` | `true` | No (same as default) |
| 4 | `monitor.engine-metrics.enabled` | `true` | `false` | **YES** — OFF → ON |
| 5 | `trading.metadata.require-credentials-on-startup` | `true` | `false` | **YES** — OFF → ON |

**Only difference from staging:** `trading.metadata.require-credentials-on-startup: true` (vs `false` in staging). This is the single distinguishing property between staging and prod-like for monitor-app.

---

### 2. engine-app

#### Base defaults source: `application.yml` only

**Note:** engine-app does NOT import `platform-core.yml`. All defaults are self-contained in `engine-app/src/main/resources/application.yml`.

#### application-local-safe.yml — 2 explicit overrides

| # | Property | Override Value | Base Default | Δ from base default? |
|---|----------|---------------|-------------|---------------------|
| 1 | `engine.execution-loop-enabled` | `false` | `${ENGINE_EXECUTION_LOOP_ENABLED:false}` | No (same as default) |
| 2 | `engine.metrics-publish.enabled` | `false` | `${ENGINE_METRICS_PUBLISH_ENABLED:false}` | No (same as default) |

**Notes:** Both overrides are documentation-style re-statements of base defaults. No meaningful changes — local-safe's safety comes from base defaults, not profile overrides.

#### application-testnet.yml — 12 explicit overrides (the most of any profile)

| # | Property | Override Value | Base Default | Δ from base default? |
|---|----------|---------------|-------------|---------------------|
| 1 | `engine.execution-loop-enabled` | `true` | `false` | **YES** — OFF → **ON** ⚠️ |
| 2 | `engine.execution-loop-interval-ms` | `2000` | `1000` | **YES** — 1000ms → **2000ms** |
| 3 | `engine.live-order-enabled` | `true` | `false` | **YES** — OFF → **ON** ⚠️⚠️ |
| 4 | `engine.kill-switch-enabled` | `false` | `true` | **YES** — ON → **OFF** ⚠️⚠️⚠️ |
| 5 | `engine.trading-venue-access-mode` | `testnet` | `testnet` | No (same as default) |
| 6 | `engine.live-enabled-venues` | `gate` | `bybit,gate` | **YES** — bybit removed, **gate only** |
| 7 | `engine.max-notional-usd` | `25` | `25` | No (same as default, documentation) |
| 8 | `engine.metadata-max-age-minutes` | `240` | `240` | No (same as default, documentation) |
| 9 | `engine.latency-max-age-minutes` | `1440` | `1440` | No (same as default, documentation) |
| 10 | `engine.monitor-base-url` | `${...http://localhost:8090}` | `${...http://localhost:8090}` | No (same expression) |
| 11 | `engine.internal-token` | `${...funding-local-internal-token}` | `${...}` | **YES** — empty → **dev token** |
| 12 | `engine.metrics-publish.enabled` | `false` | `false` | No (same as default) |

**Notes:**
- 4 meaningful changes (execution-loop ON, live-order ON, kill-switch OFF, internal-token with default)
- 6 documentation-style duplicates of base defaults (venues, notional, ages, monitor URL, metrics)
- 1 limit-scoping change (live-enabled-venues: `gate` only instead of `bybit,gate`)
- 1 rate-limiting change (execution-loop-interval: 1000ms → 2000ms)
- **This is the only profile that reverses all 3 critical execution guards**

#### application-staging.yml — 2 explicit overrides

| # | Property | Override Value | Base Default | Δ from base default? |
|---|----------|---------------|-------------|---------------------|
| 1 | `engine.execution-loop-enabled` | `false` | `false` | No (same as default) |
| 2 | `engine.metrics-publish.enabled` | `true` | `false` | **YES** — OFF → ON |

#### application-prod-like.yml — 2 explicit overrides

| # | Property | Override Value | Base Default | Δ from base default? |
|---|----------|---------------|-------------|---------------------|
| 1 | `engine.execution-loop-enabled` | `false` | `false` | No (same as default) |
| 2 | `engine.metrics-publish.enabled` | `true` | `false` | **YES** — OFF → ON |

**Byte-for-byte identical to staging.** The only behavioral difference between engine-app staging and prod-like comes from environment variables, not YAML overrides.

---

### 3. telegram-bot-app

#### Base defaults source: `application.yml` only

**Structural note:** telegram-bot-app profile YAMLs do NOT use `spring.config.activate.on-profile` — they rely on Spring Boot's `application-{profile}.yml` filename convention only.

#### application-local-safe.yml — 4 explicit overrides

| # | Property | Override Value | Base Default | Δ from base default? |
|---|----------|---------------|-------------|---------------------|
| 1 | `telegram.bot.token` | `${TELEGRAM_BOT_TOKEN:}` | `${TELEGRAM_BOT_TOKEN:}` | No (same expression) |
| 2 | `monitor.base-url` | `http://localhost:8090` (hardcoded) | `${MONITOR_BASE_URL:http://localhost:8090}` | No — resolves to same value |
| 3 | `monitor.public-url` | (empty, hardcoded) | `${MONITOR_PUBLIC_URL:}` | No — resolves to same value |
| 4 | `logging.level.com.crypto.funding.telegram` | `DEBUG` | *(not set)* | **YES** — DEBUG logging |

**Notes:**
- Overrides #1-3 are documentation-style only, no functional change vs base defaults
- Only meaningful change: DEBUG logging level

#### application-staging.yml — 4 explicit overrides

| # | Property | Override Value | Base Default | Δ from base default? |
|---|----------|---------------|-------------|---------------------|
| 1 | `telegram.bot.token` | `${TELEGRAM_BOT_TOKEN}` **(no default)** | `${TELEGRAM_BOT_TOKEN:}` (default empty) | **YES** — token now **required** (no fallback) |
| 2 | `monitor.base-url` | `${MONITOR_BASE_URL:http://monitor:8090}` | `${MONITOR_BASE_URL:http://localhost:8090}` | **YES** — Docker hostname default |
| 3 | `monitor.public-url` | `${MONITOR_PUBLIC_URL:https://crypto-monitor.org}` | `${MONITOR_PUBLIC_URL:}` | **YES** — production URL default |
| 4 | `logging.level.com.crypto.funding.telegram` | `INFO` | *(not set)* | **YES** — INFO logging |

**Notes:**
- All 4 overrides are meaningful — removes empty defaults, sets Docker/production-appropriate values
- telegram-bot-app has NO `testnet` or `prod-like` profiles

---

### 4. Cross-Module Override Summary

| Module | Profile | # Overrides | Meaningful Δ from base |
|--------|---------|:----------:|:---------------------:|
| monitor-app | local-safe | 7 | 2 (master-key startup, metadata sync) |
| monitor-app | staging | 5 | 3 (auth ON, credentials ON, metrics ON) |
| monitor-app | prod-like | 5 | 4 (same + metadata creds required) |
| engine-app | local-safe | 2 | 0 (documentation only) |
| engine-app | testnet | 12 | 4 (execution-critical) |
| engine-app | staging | 2 | 1 (metrics ON) |
| engine-app | prod-like | 2 | 1 (metrics ON) |
| telegram-bot-app | local-safe | 4 | 1 (DEBUG logging) |
| telegram-bot-app | staging | 4 | 4 (all meaningful) |

**Total explicit overrides across all profiles:** 43
**Total meaningful changes from base defaults:** ~17

---

## Part 2: Dangerous Parameter Inheritance Analysis

This section identifies parameters that are **inherited unchanged** from base defaults in profiles where they should arguably be overridden for safety.

### CRITICAL severity (potential trading/financial risk)

| # | Parameter | Inherited Default | Profiles That Inherit It | Risk |
|---|-----------|-------------------|-------------------------|------|
| 1 | `trading.candidate-source.enabled` | `true` | **ALL** profiles — local-safe, staging, prod-like, testnet (none override it) | External API (`uainvest.com.ua`) polled every 60s in **every** environment, including local development. No profile explicitly disables this. |
| 2 | `server.shutdown` | `immediate` (Spring Boot default) | **ALL** profiles — **no profile overrides this** in any module | SIGTERM kills in-flight trades and `@Async` tasks mid-execution. No graceful shutdown in any profile. |
| 3 | `spring.task.scheduling.pool.size` | `1` (Spring Boot default) | **ALL** profiles — not configured anywhere | Single scheduler thread shared by 6 `@Scheduled` methods. Any blocking operation in one method stalls the 250ms execution loop tick. |
| 4 | `engine.kill-switch-enabled` | `true` | local-safe, staging, prod-like (testnet overrides to `false`) | Safe in current profiles, but testnet's `kill-switch: false` is inherited if testnet profile is used in combination with any other setting. |

### HIGH severity (operational risk or misconfiguration)

| # | Parameter | Inherited Default | Profiles That Inherit It | Risk |
|---|-----------|-------------------|-------------------------|------|
| 5 | `trading.bybit.mode` | `testnet` | ALL monitor-app profiles | Benign for bybit. But: |
| 6 | `trading.gate.mode` | `testnet` | ALL monitor-app profiles | Benign for gate. But: |
| 7 | `trading.bitget.mode` | `production` | ALL monitor-app profiles | ⚠️ **Production mode by default.** Bitget uses the same URL for testnet and production — no safety net from different hostnames. |
| 8 | `trading.okx.mode` | `production` | ALL monitor-app profiles | ⚠️ **Production mode by default.** OKX uses `x-simulated-trading: 1` header for testnet, but mode defaults to `production`. |
| 9 | `trading.kucoin.mode` | `production` | ALL monitor-app profiles | ⚠️ **Production mode by default.** Different URLs for testnet vs production but mode defaults to production. |
| 10 | `trading.venue-access.mode` (monitor) | `production` | ALL monitor-app profiles | Monitor resolves credentials against production mode by default. Engine uses `testnet` by default. **Inconsistent.** |
| 11 | `engine.internal-token` | empty string | local-safe, staging, prod-like (testnet provides default) | Base default is empty. Only testnet profile provides a default (`funding-local-internal-token`). The other profiles rely on the env var or build.gradle setting. |
| 12 | `security.operators.auth-enabled` | `false` | local-safe (explicit `false` confirms default) | Base default is auth OFF. Staging/prod-like override to ON. If a deployment forgets to set `SPRING_PROFILES_ACTIVE`, the app starts with **no authentication**. |

### MEDIUM severity (visibility or monitoring gaps)

| # | Parameter | Inherited Default | Profiles That Inherit It | Risk |
|---|-----------|-------------------|-------------------------|------|
| 13 | `spring.jpa.hibernate.ddl-auto` | `validate` | ALL monitor-app profiles | Safe default (validate-only). But inherited everywhere — no profile would ever accidentally enable auto-DDL. |
| 14 | `trading.metadata.schedule-enabled` | `false` | ALL monitor-app profiles | Metadata is only synced on startup (or via API). No periodic refresh in any profile. Metadata can become stale. |
| 15 | `trading.metadata.require-credentials-on-startup` | `false` | local-safe, staging inherit `false` (prod-like overrides to `true`) | In staging, metadata sync runs without requiring credentials. If credential checks fail, metadata could be incomplete or stale on startup. |
| 16 | `ai.deepseek.enabled` | `false` | staging, prod-like inherit (local-safe explicitly sets `false`) | Not dangerous — safe default. But if a deployment wants DeepSeek, they must explicitly set the env var. |
| 17 | `monitor.engine-metrics.enabled` | `false` | local-safe inherits (explicit `false` confirms) | Base default is metrics OFF. Only staging/prod-like override to ON. Local instances have no visibility into engine metrics. |
| 18 | `credentials.storage.enabled` | `false` | local-safe inherits; staging/prod-like override to ON | Base default is credential storage OFF. Deployments must remember to enable. If forgotten, credential operations silently fail or use empty credentials. |
| 19 | `spring.main.web-application-type` | `none` | ALL telegram-bot-app profiles | Intentional (non-web app). But if someone adds a REST controller and expects it to serve HTTP, it won't work. |

### LOW severity (documentation or consistency)

| # | Parameter | Inherited Default | Profiles That Inherit It | Risk |
|---|-----------|-------------------|-------------------------|------|
| 20 | `trading.metadata.sync-on-startup` | `true` | staging, prod-like (local-safe overrides to `false`) | In staging and prod-like, exchange metadata is fetched on every startup. Can increase startup time if exchanges are slow. |
| 21 | `logging.level.com.crypto.funding.telegram` | not set | local-safe overrides to DEBUG, staging to INFO | Base default is no telegram-specific logging. Unlikely dangerous. |
| 22 | `trading.http.*` timeouts | connect=1000ms, request=5000ms | ALL monitor-app profiles | Reasonable defaults, inherited everywhere. |
| 23 | `engine.bybit`, `engine.gate`, `engine.okx`, `engine.kucoin`, `engine.bitget` URLs | venue-specific | ALL engine-app profiles | URL definitions in base application.yml, inherited by all profiles. Safe. |
| 24 | `telegram.bot.notification-chat-id` | empty (``) | ALL telegram-bot profiles | Bot starts but won't notify until configured. Safe behavior. |
| 25 | `telegram.bot.allowed-user-ids` | empty (``) | ALL telegram-bot profiles | No user restriction by default. Anyone who knows the bot token can interact. Minor risk for a notification-only bot. |
| 26 | `trading.auto-approval.enabled` | `false` | ALL profiles — none override | Safe default — auto-approval requires explicit opt-in. |
| 27 | `monitor.dev-test-tool.enabled` | `false` | ALL profiles — none override | Safe default — dev test tool requires explicit opt-in. |

---

### 3. Summary: Top 5 Dangerous Inheritances

| Rank | Inherited Value | Profile(s) | Why Dangerous |
|------|----------------|-----------|--------------|
| 1 | `candidate-source.enabled: true` | **All profiles** | External API polled every 60s in EVERY environment, including local dev. No profile disables it. Unexpected outbound traffic and rate-limited API consumption. |
| 2 | `server.shutdown: immediate` | **All profiles** | In-flight trades lost on SIGTERM. No graceful shutdown in any profile. Trading data safety gap. |
| 3 | `scheduling.pool.size: 1` | **All profiles** | Single thread for 6 @Scheduled methods. Engine's 250ms execution tick can be delayed by any other scheduled method (60s candidate poll, 30s telegram poller, 240min metadata sync). |
| 4 | `okx.mode: production`, `bitget.mode: production`, `kucoin.mode: production` | **All monitor-app profiles** | Inconsistent with bybit/gate defaults of `testnet`. Monitor resolves credentials against production endpoints by default for 3/5 venues. OKX and Bitget share URLs for testnet/production — no hostname safety net. |
| 5 | `monitor.venue-access.mode: production` vs `engine.trading-venue-access-mode: testnet` | **All profiles that include both apps** | Cross-module mismatch. Monitor resolves credentials against production mode while engine resolves against testnet mode. Harmless while execution is disabled, but creates confusion and potential misconfiguration risk. |

---

### 4. Properties That Are Safely Inherited (no override needed)

These base defaults are inherited by all profiles and are intentionally safe:

| Property | Default Value | Why Safe |
|----------|--------------|----------|
| `spring.jpa.hibernate.ddl-auto` | `validate` | Prevents accidental schema changes |
| `spring.datasource.hikari.maximum-pool-size` | `2` | Conservative connection pool |
| `trading.auto-approval.enabled` | `false` | No automatic trade approval |
| `monitor.dev-test-tool.enabled` | `false` | No dangerous test tool access |
| `ai.deepseek.enabled` | `false` | AI analysis disabled by default |
| `server.port` (monitor) | `8090` | Consistent, non-privileged port |
| `server.port` (engine) | `8091` | Consistent, non-privileged port |
| `engine.execution-scheduler-tick-ms` | `250` | Reasonable default tick rate |
| `spring.flyway.enabled` | `true` | DB migrations enabled (safe) |
| `spring.jpa.open-in-view` | `false` | Prevents LazyInitializationException in views |
| `trading.preparation.*` | various | Sensible trade preparation defaults |

---

### 5. Recommendations

1. **Add `trading.candidate-source.enabled: false` to `local-safe` profile** in monitor-app — eliminates unexpected external API calls during local development.

2. **Add `server.shutdown: graceful` to base `application.yml`** in all modules — prevents in-flight data loss. Add `spring.lifecycle.timeout-per-shutdown-phase: 30s` to configure timeout.

3. **Add `spring.task.scheduling.pool.size: 4`** to base `application.yml` in monitor-app and engine-app — prevents scheduler thread contention.

4. **Change venue mode defaults for consistency:** Set `bitget.mode: testnet`, `okx.mode: testnet`, `kucoin.mode: testnet` in `platform-core.yml` — makes them consistent with bybit and gate defaults.

5. **Document the monitor↔engine access mode mismatch** in README.md and CLAUDE.md.

6. **Add `trading.candidate-source.enabled: false` to the `.env.example`** comments as a recommended override for local environments.

7. **Add explicit `spring.config.activate.on-profile` declarations** to telegram-bot-app profile YAMLs for consistency with other modules.
