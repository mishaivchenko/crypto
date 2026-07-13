---
type: audit
title: Profile Differences — Exact Cross-Profile Comparison
created: 2026-07-13
tags:
  - audit
  - configuration
  - spring-boot
  - profiles
related:
  - '[[profile-inventory]]'
  - '[[local-safe-profile-verification]]'
  - '[[testnet-profile-verification]]'
  - '[[staging-profile-verification]]'
  - '[[prod-like-profile-verification]]'
  - '[[application-properties-audit]]'
---

# Profile Differences — Exact Cross-Profile Comparison

## Scope

This document provides the unified, across-profile comparison of all 4 named profiles (`local-safe`, `testnet`, `staging`, `prod-like`) across all 3 application modules (monitor-app, engine-app, telegram-bot-app). It complements the individual per-profile verification reports by showing **what changes between profiles** rather than what each profile sets independently.

## Profile Availability Per Module

| Profile | monitor-app | engine-app | telegram-bot-app |
|---------|:-----------:|:----------:|:----------------:|
| `local-safe` | ✅ | ✅ | ✅ |
| `testnet` | ❌ | ✅ | ❌ |
| `staging` | ✅ | ✅ | ✅ |
| `prod-like` | ✅ | ✅ | ❌ |

**Key facts:**
- `testnet` exists **only** in engine-app (enables Gate testnet execution)
- `prod-like` does **not** exist in telegram-bot-app (uses `staging` in production deployment)
- `prod` does not exist anywhere (only `prod-like` is used)

---

## 1. monitor-app — Property Comparison Across Profiles

### 1.1 Explicit Overrides Table

Uniform comparison of every property explicitly set in any monitor-app profile YAML:

| Property | `platform-core.yml` (default) | `local-safe` | `staging` | `prod-like` |
|----------|:-----------------------------:|:------------:|:---------:|:-----------:|
| `security.operators.auth-enabled` | `${SECURITY_OPERATOR_AUTH_ENABLED:false}` | **`false`** | **`true`** | **`true`** |
| `credentials.storage.enabled` | `${CREDENTIALS_STORAGE_ENABLED:false}` | **`false`** | **`true`** | **`true`** |
| `credentials.storage.require-master-key-on-startup` | `${CREDENTIALS_REQUIRE_MASTER_KEY_ON_STARTUP:true}` | **`false`** | **`true`** | **`true`** |
| `monitor.engine-metrics.enabled` | `${MONITOR_ENGINE_METRICS_ENABLED:false}` | **`false`** | **`true`** | **`true`** |
| `trading.metadata.require-credentials-on-startup` | `${TRADING_METADATA_REQUIRE_CREDENTIALS_ON_STARTUP:false}` | **`false`** | **`false`** | **`true`** |
| `trading.metadata.sync-on-startup` | `${TRADING_METADATA_SYNC_ON_STARTUP:true}` | **`false`** | *(inherited: `true`)* | *(inherited: `true`)* |
| `ai.deepseek.enabled` | `${AI_DEEPSEEK_ENABLED:false}` | **`false`** | *(inherited: `false`)* | *(inherited: `false`)* |
| `spring.config.activate.on-profile` | — | `local-safe` | `staging` | `prod-like` |

**Empty cells = inherited default (value from `platform-core.yml` used unchanged).**

### 1.2 monitor-app — Dimension Summary by Profile

| Dimension | `local-safe` | `staging` | `prod-like` |
|-----------|:------------:|:---------:|:-----------:|
| Authentication | 🟢 OFF | 🔵 ON | 🔵 ON |
| Credential storage | 🟢 OFF | 🔵 ON | 🔵 ON |
| Master key required on startup | 🟢 OFF | 🔵 ON | 🔵 ON |
| Engine metrics ingestion | 🟢 OFF | 🟡 ON | 🟡 ON |
| Metadata credentials required | 🟢 OFF | 🟢 OFF | 🔵 ON |
| Metadata startup sync | 🟢 OFF | 🟡 ON (default) | 🟡 ON (default) |
| DeepSeek AI | 🟢 OFF | 🟢 OFF (default) | 🟢 OFF (default) |
| External API poll (candidate source) | 🟡 ON (default) | 🟡 ON (default) | 🟡 ON (default) |
| Auto-approval | 🟢 OFF (default) | 🟢 OFF (default) | 🟢 OFF (default) |
| Dev test tool | 🟢 OFF (default) | 🟢 OFF (default) | 🟢 OFF (default) |

### 1.3 monitor-app — Profile Pairwise Diff

#### local-safe → staging (5 properties change)

| Property | local-safe | staging | Change |
|----------|:----------:|:-------:|--------|
| `auth-enabled` | `false` | `true` | OFF → ON |
| `credentials.storage.enabled` | `false` | `true` | OFF → ON |
| `credentials.storage.require-master-key-on-startup` | `false` | `true` | OFF → ON |
| `monitor.engine-metrics.enabled` | `false` | `true` | OFF → ON |
| `trading.metadata.sync-on-startup` | `false` | `true` (default) | Disabled explicitly → default (enabled) |

#### staging → prod-like (1 property changes)

| Property | staging | prod-like | Change |
|----------|:-------:|:---------:|--------|
| `trading.metadata.require-credentials-on-startup` | `false` | `true` | OFF → ON |

This is the **only difference** between staging and prod-like for monitor-app.

#### local-safe → prod-like (5 properties change)

| Property | local-safe | prod-like | Change |
|----------|:----------:|:---------:|--------|
| `auth-enabled` | `false` | `true` | OFF → ON |
| `credentials.storage.enabled` | `false` | `true` | OFF → ON |
| `credentials.storage.require-master-key-on-startup` | `false` | `true` | OFF → ON |
| `monitor.engine-metrics.enabled` | `false` | `true` | OFF → ON |
| `trading.metadata.require-credentials-on-startup` | `false` | `true` | OFF → ON |
| `trading.metadata.sync-on-startup` | `false` | `true` (default) | Disabled explicitly → default (enabled) |

---

## 2. engine-app — Property Comparison Across Profiles

### 2.1 Explicit Overrides Table

Uniform comparison of every property explicitly set in any engine-app profile YAML:

| Property | `application.yml` (base default) | `local-safe` | `testnet` | `staging` | `prod-like` |
|----------|:-------------------------------:|:------------:|:---------:|:---------:|:-----------:|
| `engine.execution-loop-enabled` | `${ENGINE_EXECUTION_LOOP_ENABLED:false}` | **`false`** | **`true`** | **`false`** | **`false`** |
| `engine.execution-loop-interval-ms` | `${ENGINE_EXECUTION_LOOP_INTERVAL_MS:1000}` | *(inherited: 1000)* | **`2000`** | *(inherited: 1000)* | *(inherited: 1000)* |
| `engine.live-order-enabled` | `${ENGINE_LIVE_ORDER_ENABLED:false}` | *(inherited: `false`)* | **`true`** | *(inherited: `false`)* | *(inherited: `false`)* |
| `engine.kill-switch-enabled` | `${ENGINE_KILL_SWITCH_ENABLED:true}` | *(inherited: `true`)* | **`false`** | *(inherited: `true`)* | *(inherited: `true`)* |
| `engine.trading-venue-access-mode` | `${TRADING_VENUE_ACCESS_MODE:testnet}` | *(inherited: testnet)* | **`testnet`** | *(inherited: testnet)* | *(inherited: testnet)* |
| `engine.live-enabled-venues` | `${ENGINE_LIVE_ENABLED_VENUES:bybit,gate}` | *(inherited: bybit,gate)* | **`gate`** | *(inherited: bybit,gate)* | *(inherited: bybit,gate)* |
| `engine.max-notional-usd` | `${ENGINE_MAX_NOTIONAL_USD:25}` | *(inherited: 25)* | **`25`** | *(inherited: 25)* | *(inherited: 25)* |
| `engine.metadata-max-age-minutes` | `${ENGINE_METADATA_MAX_AGE_MINUTES:240}` | *(inherited: 240)* | **`240`** | *(inherited: 240)* | *(inherited: 240)* |
| `engine.latency-max-age-minutes` | `${ENGINE_LATENCY_MAX_AGE_MINUTES:1440}` | *(inherited: 1440)* | **`1440`** | *(inherited: 1440)* | *(inherited: 1440)* |
| `engine.monitor-base-url` | `${MONITOR_INTERNAL_BASE_URL:http://localhost:8090}` | *(inherited)* | **`${MONITOR_INTERNAL_BASE_URL:http://localhost:8090}`** | *(inherited)* | *(inherited)* |
| `engine.internal-token` | `${INTERNAL_ENGINE_TOKEN:}` | *(inherited: empty)* | **`${INTERNAL_ENGINE_TOKEN:funding-local-internal-token}`** | *(inherited: empty)* | *(inherited: empty)* |
| `engine.metrics-publish.enabled` | `${ENGINE_METRICS_PUBLISH_ENABLED:false}` | **`false`** | **`false`** | **`true`** | **`true`** |

**Bold = explicitly set in that profile's YAML file. Empty cells = inherited from `application.yml` base defaults.**

### 2.2 engine-app — Distinct YAML Override Profiles

| Property | `local-safe` | `testnet` | `staging` | `prod-like` |
|----------|:-----------:|:---------:|:---------:|:-----------:|
| **Properties overridden** | 2 | 11 | 2 | 2 |
| **Unique overrides (not shared with any other profile)** | 0 | 6¹ | 0 | 0 |
| **Shared with staging** | Both have execution-loop=false, but staging has metrics=true | — | Both have execution-loop=false, metrics=true | Both have execution-loop=false, metrics=true |
| **Byte-for-byte identical to** | — | — | **prod-like** | **staging** |

¹ Unique testnet overrides: `execution-loop-interval-ms: 2000`, `live-order-enabled: true`, `kill-switch-enabled: false`, `live-enabled-venues: gate`, `internal-token: funding-local-internal-token`, plus 3 others that repeat base defaults explicitly.

### 2.3 engine-app — Dimension Summary by Profile

| Dimension | `local-safe` | `testnet` | `staging` | `prod-like` |
|-----------|:------------:|:---------:|:---------:|:-----------:|
| Execution loop | 🟢 OFF | 🔴 ON (2s tick) | 🟢 OFF | 🟢 OFF |
| Live orders | 🟢 OFF | 🔴 ON (Gate only) | 🟢 OFF | 🟢 OFF |
| Kill switch | 🟢 ON | 🔴 OFF | 🟢 ON | 🟢 ON |
| Metrics publishing | 🟢 OFF | 🟢 OFF | 🟡 ON | 🟡 ON |
| Internal token default | empty | `funding-local-internal-token` | empty | empty |
| Execution guard level | 3 guards ON | 0 guards ON | 3 guards ON | 3 guards ON |

### 2.4 engine-app — Profile Pairwise Diff

#### local-safe → testnet (10 properties change)

| Property | local-safe | testnet | Change |
|----------|:---------:|:-------:|--------|
| `execution-loop-enabled` | `false` | `true` | OFF → ON |
| `execution-loop-interval-ms` | 1000 (base) | **2000** | 1000 → 2000ms |
| `live-order-enabled` | `false` (base) | **`true`** | OFF → ON |
| `kill-switch-enabled` | `true` (base) | **`false`** | ON → OFF |
| `live-enabled-venues` | bybit,gate (base) | **gate** | bybit+gate → gate only |
| `max-notional-usd` | 25 (base) | **25** | (same, explicit) |
| `metadata-max-age-minutes` | 240 (base) | **240** | (same, explicit) |
| `latency-max-age-minutes` | 1440 (base) | **1440** | (same, explicit) |
| `internal-token` | empty (base) | **funding-local-internal-token** | Empty → dev token |
| `metrics-publish.enabled` | `false` | `false` | (same — both disable) |

#### local-safe → staging (1 property changes)

| Property | local-safe | staging | Change |
|----------|:---------:|:-------:|--------|
| `metrics-publish.enabled` | `false` | `true` | OFF → ON |

Note: Both overrides set `execution-loop-enabled: false` so that property is the same.

#### staging → prod-like (0 properties change — IDENTICAL)

engine-app `application-staging.yml` and `application-prod-like.yml` are **byte-for-byte identical**:
- Both set `execution-loop-enabled: false`
- Both set `metrics-publish.enabled: true`

The behavioral distinction between these two profiles relies entirely on **environment variables** (e.g., `ENGINE_EXECUTION_LOOP_ENABLED`, `ENGINE_KILL_SWITCH_ENABLED`), not on YAML overrides.

---

## 3. telegram-bot-app — Property Comparison Across Profiles

### 3.1 Explicit Overrides Table

| Property | `application.yml` (base default) | `local-safe` | `staging` |
|----------|:-------------------------------:|:------------:|:---------:|
| `telegram.bot.token` | `${TELEGRAM_BOT_TOKEN:}` | `${TELEGRAM_BOT_TOKEN:}` (same expression) | **`${TELEGRAM_BOT_TOKEN}`** (no default — required) |
| `telegram.bot.notification-chat-id` | `${TELEGRAM_NOTIFICATION_CHAT_ID:}` | *(same default)* | *(same default)* |
| `telegram.bot.allowed-user-ids` | `${TELEGRAM_ALLOWED_USER_IDS:}` | *(same default)* | *(same default)* |
| `monitor.base-url` | `${MONITOR_BASE_URL:http://localhost:8090}` | **`http://localhost:8090`** (hardcoded) | **`${MONITOR_BASE_URL:http://monitor:8090}`** (Docker hostname) |
| `monitor.public-url` | `${MONITOR_PUBLIC_URL:}` | **empty** (hardcoded) | **`${MONITOR_PUBLIC_URL:https://crypto-monitor.org}`** |
| `logging.level.com.crypto.funding.telegram` | *(not set)* | **DEBUG** | **INFO** |
| `spring.config.activate.on-profile` | — | ❌ **Missing** | ❌ **Missing** |

**Note:** telegram-bot-app profile YAML files do **not** declare `spring.config.activate.on-profile` — they rely on Spring Boot's `application-{profile}.yml` filename convention.

### 3.2 telegram-bot-app — Dimension Summary by Profile

| Dimension | `local-safe` | `staging` |
|-----------|:------------:|:---------:|
| Bot activation | 🟢 Disabled (empty token default) | 🔵 Requires `TELEGRAM_BOT_TOKEN` env var |
| Monitor connection | 🟢 Localhost:8090 | 🟡 Docker internal `monitor:8090` |
| Public URL | 🟢 Unset (empty) | 🔵 `https://crypto-monitor.org` |
| Logging level | 🔵 DEBUG | 🟡 INFO |

### 3.3 telegram-bot-app — No `testnet` or `prod-like` profiles

- **No `testnet` profile**: telegram-bot is unaware of testnet execution mode. It polls monitor independently of engine profile.
- **No `prod-like` profile**: In production deployment (`deploy/docker-compose.yml`), telegram-bot runs with `SPRING_PROFILES_ACTIVE=staging` while monitor and engine run with `prod-like`. This is the intended configuration.

---

## 4. Cross-Module Inconsistencies

### 4.1 Inconsistent Venue Access Mode Defaults

| Module | Property | Default | Profile overriding |
|--------|----------|---------|-------------------|
| monitor-app | `trading.venue-access.mode` | **`production`** (`platform-core.yml`) | None |
| engine-app | `engine.trading-venue-access-mode` | **`testnet`** (`application.yml`) | testnet (set to `testnet` explicitly) |

**Impact:** In `local-safe`, `staging`, and `prod-like` profiles, monitor resolves venue credentials against `production` mode while engine resolves against `testnet` mode. This is **harmless** because execution is disabled in all three profiles. In `testnet` profile (engine-only), engine uses testnet mode explicitly.

### 4.2 Inconsistent Venue-Specific Defaults (platform-core.yml)

| Venue | Default `mode` | Testnet URL | Production URL | Same URL? |
|-------|:--------------:|:-----------:|:--------------:|:---------:|
| bybit | `testnet` | `api-testnet.bybit.com` | `api.bybit.com` | ❌ Different |
| gate | `testnet` | `api-testnet.gateapi.io` | `api.gateio.ws` | ❌ Different |
| bitget | `production` | `api.bitget.com` | `api.bitget.com` | ✅ **Same** |
| okx | `production` | `www.okx.com` | `www.okx.com` | ✅ **Same** |
| kucoin | `production` | `api-sandbox.kucoin.com` | `api-futures.kucoin.com` | ❌ Different |

### 4.3 YAML Structure Inconsistencies

| Aspect | monitor-app | engine-app | telegram-bot-app |
|--------|:-----------:|:----------:|:----------------:|
| Uses `spring.config.activate.on-profile` in profile YAMLs | ✅ Yes | ✅ Yes | ❌ **No** (filename convention only) |
| Profiles with `on-profile` | local-safe, staging, prod-like | local-safe, testnet, staging, prod-like | **None** |
| `prod-like` profile exists | ✅ Yes | ✅ Yes | ❌ **No** |
| `testnet` profile exists | ❌ No | ✅ Yes | ❌ No |

### 4.4 Profile File Sizes (bytes)

| Profile | monitor-app | engine-app | telegram-bot-app |
|---------|:----------:|:----------:|:----------------:|
| Base (`application.yml`) | 164 | 864 | 509 |
| `local-safe` | 270 | 134 | 237 |
| `testnet` | — | 575 | — |
| `staging` | 268 | 136 | 326 |
| `prod-like` | 270 | 136 | — |

**Note:** engine-app's `application.yml` is the largest because it contains all venue URL default definitions (all other profiles inherit these).

---

## 5. Profile Inheritance Chains

### 5.1 Property Source Order (lowest → highest priority)

```
1. Spring Boot defaults
2. platform-core.yml (shared, imported by monitor-app only)
3. application.yml (module-specific base)
4. application-{profile}.yml (profile-specific overrides)
5. Environment variables / JVM args / CLI args
```

### 5.2 What Each Profile Inherits Unchanged

#### monitor-app (inherits from `platform-core.yml` + `application.yml`)

All profiles inherit these defaults (not overridden):

- `server.port: 8090`
- `spring.datasource.url: jdbc:sqlite:./data/fundingarb.db`
- `spring.jpa.hibernate.ddl-auto: validate`
- `spring.flyway.enabled: true`
- `monitor.engine-plan.*` — all plan defaults
- `monitor.dev-test-tool.*` — dev test defaults
- `trading.candidate-source.*` — external API poll (⚠️ enabled by default in all profiles)
- `trading.auto-approval.*` — auto-approval defaults
- `trading.bybit/gate/bitget/okx/kucoin.*` — venue-specific URLs and modes
- `trading.http.*` — HTTP client timeouts
- `ai.deepseek.*` — AI analysis defaults (disabled by default)

#### engine-app (inherits from `application.yml`)

All profiles inherit these defaults (not overridden):

- `server.port: 8091`
- `engine.bybit.*`, `engine.gate.*`, `engine.okx.*`, `engine.kucoin.*`, `engine.bitget.*` — all venue URL defaults
- `engine.execution-scheduler-tick-ms: 250`
- `engine.metrics-publish.interval-ms: 15000`
- `engine.monitor-base-url: http://localhost:8090`

Additionally, `local-safe`, `staging`, and `prod-like` inherit (not overridden):
- `engine.live-order-enabled: false`
- `engine.kill-switch-enabled: true`
- `engine.trading-venue-access-mode: testnet`
- `engine.live-enabled-venues: bybit,gate`
- `engine.internal-token: ` (empty, set to dev token via build.gradle)

---

## 6. Summary: What Changes Between Profiles

### 6.1 Profile Pairs Ranked by Difference Magnitude

| Pair | Modules Affected | Properties Changed | Impact |
|------|:---------------:|:-----------------:|--------|
| `local-safe` ↔ `testnet` | engine-app only | 10 changes | **Highest**: switches loop ON, live orders ON, kill switch OFF |
| `local-safe` ↔ `staging` | 3 modules | 8 changes | Medium: enables auth, credentials, metrics across modules |
| `local-safe` ↔ `prod-like` | 2 modules | 8 changes | Medium: same as staging + metadata credentials required |
| `staging` ↔ `prod-like` | 1 module | 1 change | **Lowest**: only metadata credential requirement differs |
| `testnet` ↔ `staging` | engine-app only | 4 changes | Medium: loop ON/OFF, live orders ON/OFF, kill switch OFF/ON |
| `testnet` ↔ `prod-like` | engine-app only | 4 changes | Medium: same as testnet↔staging |
| `staging` ↔ `prod-like` (engine) | engine-app only | 0 changes | **Identical** (byte-for-byte) |

### 6.2 Safety Progression

```
local-safe    →  staging          →  prod-like       →  testnet (engine only)
safest            more restrictive    most protective    most permissive
                                                        
Auth OFF         Auth ON             Auth ON             N/A
Creds OFF        Creds ON            Creds ON            N/A
Loop OFF         Loop OFF            Loop OFF            Loop ON
Live OFF         Live OFF            Live OFF            Live ON
Kill N/A         Kill ON             Kill ON             Kill OFF
```

### 6.3 Properties That Change Most Between Profiles

| Property | Changed in # of profiles | Values |
|----------|:-----------------------:|--------|
| `engine.execution-loop-enabled` | 4/4 profiles | `true` (testnet), `false` (all others) |
| `engine.metrics-publish.enabled` | 4/4 profiles | `false` (local-safe, testnet), `true` (staging, prod-like) |
| `security.operators.auth-enabled` | Profile YAMLs: 3/3 | `false` (local-safe), `true` (staging, prod-like) |
| `credentials.storage.enabled` | Profile YAMLs: 3/3 | `false` (local-safe), `true` (staging, prod-like) |
| `trading.metadata.require-credentials-on-startup` | Monitor: 3/3 profiles | `false` (local-safe, staging), `true` (prod-like) |
| `trading.metadata.sync-on-startup` | Monitor: 2/3 profiles | `false` (local-safe), default `true` (staging, prod-like) |

### 6.4 Properties That Never Change Across Profiles

These properties are set in base configs and **never overridden** by any profile:

| Property | Value | Module |
|----------|-------|--------|
| `spring.jpa.hibernate.ddl-auto` | `validate` | monitor-app |
| `spring.datasource.hikari.maximum-pool-size` | 2 | monitor-app |
| `engine.execution-scheduler-tick-ms` | 250 | engine-app |
| `monitor.engine-plan.lookahead-minutes` | 120 | monitor-app |
| `trading.http.*` | Various | monitor-app |
| `trading.bybit/gate/bitget/okx/kucoin.mode` | Per-venue | monitor-app |
| Server ports (8090/8091) | Per module | All |

---

## 7. Notable Findings

### Finding 1: engine-app staging and prod-like are byte-for-byte identical

Both files contain exactly:
```yaml
engine:
    execution-loop-enabled: false
    metrics-publish:
        enabled: true
```

There is **no YAML-level distinction** between staging and prod-like for engine-app. The behavioral difference depends entirely on environment variables set by the deployment infrastructure.

### Finding 2: Only testnet profile changes the 3 critical execution guards

The three runtime guards (`execution-loop-enabled`, `live-order-enabled`, `kill-switch-enabled`) are:
- **All OFF/ON respectively** in `local-safe`, `staging`, `prod-like` (via base defaults)
- **Reversed** in `testnet` only (loop ON, live ON, kill OFF)

No other profile combination reverses any of these three guards.

### Finding 3: 6 properties are explicitly duplicated across testnet profile for clarity

The testnet profile repeats 6 properties with values identical to their base defaults:
- `trading-venue-access-mode: testnet` (same as base default)
- `max-notional-usd: 25` (same as base default)
- `metadata-max-age-minutes: 240` (same as base default)
- `latency-max-age-minutes: 1440` (same as base default)
- `monitor-base-url: ${...}` (same as base default)
- `metrics-publish.enabled: false` (same as base default)

These are harmless documentation-style repeats but add maintenance burden if base defaults change.

### Finding 4: telegram-bot-app lags behind in profile structure

- Lacks `spring.config.activate.on-profile` declarations in both `application-local-safe.yml` and `application-staging.yml`
- No `application-prod-like.yml` exists
- The staging YAML declares `telegram.bot.token: ${TELEGRAM_BOT_TOKEN}` (no default) which means the profile **requires** the token — unlike base `application.yml` which has `${TELEGRAM_BOT_TOKEN:}` (default empty)

### Finding 5: local-safe is the only profile that explicitly disables external calls

- `trading.metadata.sync-on-startup: false` — only in local-safe
- `ai.deepseek.enabled: false` — explicitly in local-safe (though default is also false)
- `trading.metadata.require-credentials-on-startup: false` — local-safe and staging

### Finding 6: All profiles inherit `trading.candidate-source.enabled: true`

The candidate source (external API poll at `uainvest.com.ua` every 60s) is enabled by default in ALL profiles. No profile YAML explicitly overrides this. This means all local, staging, and production instances poll the external API unless the operator explicitly sets `TRADING_CANDIDATE_SOURCE_ENABLED=false`.

---

## 8. Configuration Risk by Profile

| Risk Dimension | `local-safe` | `testnet` | `staging` | `prod-like` |
|---------------|:------------:|:---------:|:---------:|:-----------:|
| Unauthorized API access | 🟢 No auth → no risk | 🟢 Engine has no user-facing API | 🔵 Auth ON | 🔵 Auth ON |
| Accidental live trading | 🟢 Loop OFF, Live OFF | 🔴 **Loop ON, Live ON** | 🟢 Loop OFF, Live OFF | 🟢 Loop OFF, Live OFF |
| Real money at risk | 🟢 None | 🟢 Gate testnet only, $25 max | 🟢 None | 🟢 None |
| External API calls (candidate source) | 🟡 ON (60s) | 🟡 ON (60s) | 🟡 ON (60s) | 🟡 ON (60s) |
| Exchange API calls (metadata) | 🟢 OFF | 🟢 N/A | 🟡 ON (startup + schedule) | 🟡 ON (startup + schedule) |
| Exchange API calls (live orders) | 🟢 OFF | 🔴 ON (Gate testnet) | 🟢 OFF | 🟢 OFF |
| Telemetry data published | 🟢 OFF | 🟢 OFF | 🟡 ON | 🟡 ON |
| Startup blocked by missing config | 🟢 No | 🟢 No (dev token provided) | 🟢 No (metadata creds not required) | 🔵 **Yes** (master key + metadata creds required) |

---

## 9. Recommendations

1. **Make engine-app staging and prod-like distinct** — Add at least one differentiating property (e.g., different logging level, different metrics interval) so operators can verify which profile is active at runtime.

2. **Add `trading.candidate-source.enabled: false` to local-safe profile** — Eliminates unexpected external API calls during local development.

3. **Add `spring.config.activate.on-profile` to telegram-bot-app profile YAMLs** — Consistent with the other two modules and makes intent explicit.

4. **Consider adding an `application-prod-like.yml` for telegram-bot-app** — Even if identical to staging, having a dedicated prod-like profile avoids operational confusion.

5. **Update CLAUDE.md and README.md profile tables** to include the `testnet` profile and note per-module availability.
