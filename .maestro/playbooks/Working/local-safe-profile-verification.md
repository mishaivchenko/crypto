---
type: audit
title: `local-safe` Profile — Exact Configuration Verification
created: 2026-07-13
tags:
  - audit
  - configuration
  - spring-boot
  - profiles
  - local-safe
related:
  - '[[AUDIT-ROUND-3-03]]'
  - '[[profile-inventory]]'
  - '[[application-properties-audit]]'
---

# `local-safe` Profile — Exact Configuration Verification

## Scope

Verifies the exact configuration of the `local-safe` profile across all three modules (monitor-app, engine-app, telegram-bot-app). Documents both the explicit overrides in `application-local-safe.yml` files and the inherited defaults from `platform-core.yml`, `application.yml`, and Spring Boot auto-configuration defaults.

## Profile Activation

| Property | Value |
|----------|-------|
| Activation mechanism | `SPRING_PROFILES_ACTIVE` env var |
| Default value | `local-safe` (set in `build.gradle:13`) |
| Applied by | Each `bootRun*` Gradle task sets `SPRING_PROFILES_ACTIVE` to `System.getenv('SPRING_PROFILES_ACTIVE') ?: localBootProfile` |
| Scope | All 3 modules (monitor-app, engine-app, telegram-bot-app) |
| Bootstrap token | `INTERNAL_ENGINE_TOKEN` defaults to `funding-local-internal-token` (hardcoded in build.gradle:14) |

### Activation code (build.gradle)
```groovy
localBootProfile = 'local-safe'
localInternalEngineToken = 'funding-local-internal-token'

// monitor-app bootRun:
environment 'SPRING_PROFILES_ACTIVE', System.getenv('SPRING_PROFILES_ACTIVE') ?: localBootProfile
environment 'INTERNAL_ENGINE_TOKEN', System.getenv('INTERNAL_ENGINE_TOKEN') ?: localInternalEngineToken

// engine-app bootRun:
environment 'SPRING_PROFILES_ACTIVE', System.getenv('SPRING_PROFILES_ACTIVE') ?: localBootProfile
environment 'INTERNAL_ENGINE_TOKEN', System.getenv('INTERNAL_ENGINE_TOKEN') ?: localInternalEngineToken

// telegram-bot-app bootRun:
environment 'SPRING_PROFILES_ACTIVE', System.getenv('SPRING_PROFILES_ACTIVE') ?: localBootProfile
environment 'TELEGRAM_BOT_TOKEN', System.getenv('TELEGRAM_BOT_TOKEN') ?: ''
environment 'MONITOR_BASE_URL', System.getenv('MONITOR_BASE_URL') ?: 'http://localhost:8090'
```

---

## monitor-app — Exact Configuration

### File: `application-local-safe.yml`

Seven explicit property overrides:

```yaml
security.operators.auth-enabled: false
credentials.storage.enabled: false
credentials.storage.require-master-key-on-startup: false
monitor.engine-metrics.enabled: false
trading.metadata.require-credentials-on-startup: false
trading.metadata.sync-on-startup: false
ai.deepseek.enabled: false
```

### Inherited Defaults (from `platform-core.yml` — NOT overridden)

| Property | Default Value | Safety Assessment |
|----------|---------------|-------------------|
| `server.port` | `${MONITOR_SERVER_PORT:8090}` | ✅ Safe — defaults to 8090 |
| `management.endpoints.web.exposure.include` | `health,info,prometheus` | ✅ Safe — standard actuator |
| `spring.datasource.url` | `jdbc:sqlite:./data/fundingarb.db?busy_timeout=5000&journal_mode=WAL` | ✅ Safe — local SQLite file |
| `spring.datasource.hikari.maximum-pool-size` | `2` | ✅ Safe — small pool for dev |
| `spring.flyway.enabled` | `true` | ✅ Safe — runs migrations on startup |
| `spring.jpa.hibernate.ddl-auto` | `validate` | ✅ Safe — no auto-DDL |
| `security.operators.bootstrap-users` | `${SECURITY_OPERATOR_BOOTSTRAP_USERS:}` | ✅ Safe — empty (no bootstrap users) |
| `security.operators.internal-token` | `${INTERNAL_ENGINE_TOKEN:funding-local-internal-token}` | ✅ Safe — dev token |
| `credentials.storage.master-key-base64` | `${CREDENTIALS_MASTER_KEY_BASE64:}` | ✅ Safe — empty (storage disabled) |
| `monitor.engine-plan.lookahead-minutes` | `120` | ✅ Safe — plan-only, no execution |
| `monitor.engine-control.base-url` | `http://localhost:8091` | ✅ Safe — local engine |
| `monitor.engine-metrics.enabled` | `false` (overridden in local-safe) | ✅ Safe — already disabled |
| `monitor.dev-test-tool.enabled` | `${MONITOR_DEV_TEST_TOOL_ENABLED:false}` | ✅ Safe — disabled by default |
| `trading.venue-access.mode` | `${TRADING_VENUE_ACCESS_MODE:production}` | ⚠️ **Inconsistent** — defaults to `production` |
| `trading.candidate-source.enabled` | `${TRADING_CANDIDATE_SOURCE_ENABLED:true}` | ⚠️ **Still enabled** — polls external API |
| `trading.auto-approval.enabled` | `${TRADING_AUTO_APPROVAL_ENABLED:false}` | ✅ Safe — disabled |
| `trading.metadata.sync-on-startup` | `true` (overridden to `false` in local-safe) | ✅ Safe — overridden to false |
| `trading.metadata.schedule-enabled` | `${TRADING_METADATA_SCHEDULE_ENABLED:false}` | ✅ Safe — disabled |
| `trading.metadata.bootstrap-fallback-enabled` | `${TRADING_METADATA_BOOTSTRAP_FALLBACK_ENABLED:false}` | ✅ Safe — disabled |
| `trading.bybit.mode` | `${BYBIT_MODE:testnet}` | ✅ Safe — defaults to testnet |
| `trading.gate.mode` | `${GATE_MODE:testnet}` | ✅ Safe — defaults to testnet |
| `trading.bitget.mode` | `${BITGET_MODE:production}` | ⚠️ **Inconsistent** — defaults to production |
| `trading.okx.mode` | `${OKX_MODE:production}` | ⚠️ **Inconsistent** — defaults to production |
| `trading.kucoin.mode` | `${KUCOIN_MODE:production}` | ⚠️ **Inconsistent** — defaults to production |
| `ai.deepseek.enabled` | `false` (overridden in local-safe) | ✅ Safe — overridden to false |
| `TRADING_CANDIDATE_SOURCE_URL` | `https://uainvest.com.ua/api/funding?...` | ⚠️ External API call still configured |

### Network connections made by monitor-app in local-safe

| Connection | Direction | Status |
|-----------|-----------|--------|
| SQLite DB (local file) | Local | ✅ Active |
| External API `uainvest.com.ua` (candidate source) | Outbound | ⚠️ **Active** (candidate-source enabled by default, `matchIfMissing=true`) |
| Exchange APIs (metadata sync) | Outbound | ❌ Disabled — `sync-on-startup: false` |
| Exchange APIs (venue adapters) | Outbound | ❌ Disabled — `require-credentials-on-startup: false`, no credentials stored |
| DeepSeek API | Outbound | ❌ Disabled — `deepseek.enabled: false` |
| Engine (port 8091) | Local | ✅ Configured — `engine-control.base-url: http://localhost:8091` |
| Telegram API | Outbound | ❌ Disabled — bot token `TELEGRAM_BOT_TOKEN:` default empty |

### Key Finding: Candidate source still active

**`trading.candidate-source.enabled` defaults to `true`** via `matchIfMissing=true` in `platform-core.yml:77`:
```yaml
trading.candidate-source:
    enabled: ${TRADING_CANDIDATE_SOURCE_ENABLED:true}
```

This means `local-safe` profile does NOT disable external API polling. The `FundingApiCandidateSourceService.scheduledRefresh()` runs every 60 seconds, fetching funding rate data from `uainvest.com.ua`. While this only creates `SignalCandidate` records (requiring operator review to become trades), it produces real network traffic and database writes during local development.

**Recommendation:** Add `trading.candidate-source.enabled: false` to `monitor-app/src/main/resources/application-local-safe.yml` to eliminate external API calls during local development.

---

## engine-app — Exact Configuration

### File: `application-local-safe.yml`

Two explicit property overrides:

```yaml
engine.execution-loop-enabled: false
engine.metrics-publish.enabled: false
```

### Inherited Defaults (from `application.yml` — NOT overridden)

| Property | Default Value | Safety Assessment |
|----------|---------------|-------------------|
| `server.port` | `${ENGINE_SERVER_PORT:8091}` | ✅ Safe |
| `engine.monitor-base-url` | `http://localhost:8090` | ✅ Safe — local monitor |
| `engine.internal-token` | `${INTERNAL_ENGINE_TOKEN:}` | ✅ Safe — defaults to empty in base, but bootRun sets it to `funding-local-internal-token` |
| `engine.execution-loop-enabled` | `false` (overridden in local-safe to confirm) | ✅ Safe — explicitly disabled |
| `engine.execution-loop-interval-ms` | `1000` | ✅ Safe — not used |
| `engine.execution-scheduler-tick-ms` | `250` | ✅ Safe — not used |
| `engine.live-order-enabled` | `${ENGINE_LIVE_ORDER_ENABLED:false}` | ✅ Safe — disabled by default |
| `engine.kill-switch-enabled` | `${ENGINE_KILL_SWITCH_ENABLED:true}` | ✅ Safe — enabled by default |
| `engine.live-enabled-venues` | `bybit,gate` | ✅ Safe — not used |
| `engine.max-notional-usd` | `25` | ✅ Safe — low max |
| `engine.trading-venue-access-mode` | `${TRADING_VENUE_ACCESS_MODE:testnet}` | ✅ Safe — defaults to testnet |
| `engine.metrics-publish.enabled` | `${ENGINE_METRICS_PUBLISH_ENABLED:false}` | ✅ Safe — disabled (overridden in local-safe) |

### Network connections made by engine-app in local-safe

| Connection | Direction | Status |
|-----------|-----------|--------|
| Monitor (port 8090, plan fetch) | Local | ✅ Active (no auth required — auth is disabled in local-safe) |
| Exchange APIs (live orders) | Outbound | ❌ Disabled — loop OFF, live orders OFF |
| Exchange APIs (latency probe) | Outbound | ❌ Disabled — execution loop OFF |

### Safety Confirmation

| Safety Dimension | Status | Detail |
|-----------------|--------|--------|
| Execution loop running | ❌ OFF | Explicitly disabled |
| Live orders | ❌ OFF | Default disabled, no override |
| Kill switch | ✅ ON | Explicitly defaults to `true` |
| Credentials exposed | ❌ N/A | No credentials stored (monitor has storage disabled) |
| In-flight trades on SIGTERM | ❌ N/A | Loop not running — no trades possible |

---

## telegram-bot-app — Exact Configuration

### File: `application-local-safe.yml`

**Notable:** Does NOT use `spring.config.activate.on-profile` — relies on Spring Boot's `application-{profile}.yml` filename convention for automatic profile-based loading.

```yaml
telegram.bot.token: ${TELEGRAM_BOT_TOKEN:}             # Empty default
telegram.bot.notification-chat-id: ${TELEGRAM_NOTIFICATION_CHAT_ID:}  # Empty default
telegram.bot.allowed-user-ids: ${TELEGRAM_ALLOWED_USER_IDS:}           # Empty default
monitor.base-url: http://localhost:8090                 # Local monitor
logging.level.com.crypto.funding.telegram: DEBUG        # Debug logging
```

### Inherited Defaults (from `application.yml` — NOT overridden)

| Property | Default Value | Safety Assessment |
|----------|---------------|-------------------|
| `spring.main.web-application-type` | `none` | ✅ Safe — no embedded server |
| `telegram.bot.signal-poll-interval-ms` | `30000` | ✅ Safe — not active without token |
| `monitor.operator-token` | `${MONITOR_OPERATOR_TOKEN:}` | ✅ Safe — empty default |
| `monitor.public-url` | `${MONITOR_PUBLIC_URL:}` | ✅ Safe — empty default |

### Network connections made by telegram-bot-app in local-safe

| Connection | Direction | Status |
|-----------|-----------|--------|
| Telegram API | Outbound | ❌ Disabled — token defaults to empty; `@ConditionalOnProperty(token != null)` means bean not created |
| Monitor (port 8090) | Local | ⚠️ Feign client beans load (unconditional), but no calls made without bot events |

### Key Finding: BOT token defaults to empty

The `TELEGRAM_BOT_TOKEN` defaults to empty string (`${TELEGRAM_BOT_TOKEN:}`) in both `application-local-safe.yml` and base `application.yml`. This means the `@ConditionalOnProperty` gate on `TelegramBot`, `FundingBot`, `SignalNotificationScheduler`, and `TradeNotificationScheduler` will not match, and these beans will NOT be created. The bootstrap `build.gradle` also explicitly sets this:

```groovy
environment 'TELEGRAM_BOT_TOKEN', System.getenv('TELEGRAM_BOT_TOKEN') ?: ''
```

---

## Configuration Merge Summary

The `local-safe` profile is the **most restrictive** profile, designed to prevent any possibility of real trading or external system interaction. The following table shows what is explicitly disabled vs inherited from base defaults vs potentially unexpected:

| Domain | Status | Detail |
|--------|--------|--------|
| Engine execution loop | 🔒 Explicitly OFF | `execution-loop-enabled: false` |
| Engine live orders | 🔒 Default OFF | `live-order-enabled: false` (base default, not overridden) |
| Engine kill switch | 🔒 Default ON | `kill-switch-enabled: true` (base default, not overridden) |
| Engine metrics publishing | 🔒 Explicitly OFF | `metrics-publish.enabled: false` |
| Auth on API endpoints | 🔒 Explicitly OFF | `auth-enabled: false` |
| Credential storage (encrypted DB) | 🔒 Explicitly OFF | `credentials.storage.enabled: false` |
| Master key validation | 🔒 Explicitly OFF | `require-master-key-on-startup: false` |
| Metadata sync from exchanges | 🔒 Explicitly OFF | `sync-on-startup: false` |
| Metadata credential check on startup | 🔒 Explicitly OFF | `require-credentials-on-startup: false` |
| DeepSeek AI analysis | 🔒 Explicitly OFF | `deepseek.enabled: false` |
| Monitor engine metrics ingestion | 🔒 Explicitly OFF | `engine-metrics.enabled: false` |
| Telegram bot | 🔒 Disabled (no token) | Empty `TELEGRAM_BOT_TOKEN` |
| Dev test tool | 🔒 Default OFF | `enabled: false` (base default) |
| Auto-approval pipeline | 🔒 Default OFF | `auto-approval.enabled: false` (base default) |
| **Candidate source (external API poll)** | ⚠️ **Default ON** | `candidate-source.enabled: true` (`matchIfMissing=true`) |
| **Venue mode defaults** | ⚠️ **Inconsistent** | bitget/okx/kucoin default to `production`, bybit/gate default to `testnet` |

---

## Findings

### Finding 1: Candidate source polls external API unexpectedly

**Severity:** Low (local dev impact)

`trading.candidate-source.enabled` defaults to `true` with `matchIfMissing=true`. The `local-safe` profile does not override this. Result: every local development session polls `uainvest.com.ua` every 60 seconds, even when no trading features are being tested.

**Mitigation:** Only creates `SignalCandidate` records, which require operator review. No `FundingEvent` or trade is created automatically.

**Recommendation:** Add `trading.candidate-source.enabled: false` to `monitor-app/application-local-safe.yml`.

### Finding 2: Inconsistent venue mode defaults

**Severity:** Medium (confusion risk)

Three venues default to `production` mode in `platform-core.yml`:
- `trading.bitget.mode: production`
- `trading.okx.mode: production`
- `trading.kucoin.mode: production`

While two default to `testnet`:
- `trading.bybit.mode: testnet`
- `trading.gate.mode: testnet`

In `local-safe` profile this is irrelevant (no credentials stored, no execution loop). But operators reading the config files may be confused by the inconsistency.

**Mitigation:** Execution loop is OFF, credentials are not stored — no production URL could be accessed.

### Finding 3: telegram-bot-app local-safe YAML lacks `spring.config.activate.on-profile`

**Severity:** Informational

The telegram-bot `application-local-safe.yml` does not include `spring.config.activate.on-profile: local-safe`, unlike monitor-app and engine-app. Spring Boot still activates it by `application-{profile}.yml` filename convention, so this works correctly. Adding the declaration would make the intent explicit and be consistent with the other modules.

### Finding 4: All 13 engine-app beans still load

**Severity:** Informational

All 13 engine-app beans are created regardless of profile. The execution loop ticks at 250ms but immediately exits via `EngineRuntimeControlService.isExecutionLoopEnabled()` guard. This is acceptable — no resource leaks, just minimal CPU overhead.

### Finding 5: HikariCP pool and DB connections still created

**Severity:** Informational

Monitor-app creates a 2-connection HikariCP pool and runs Flyway migrations, even though no trading features are active. This is normal for local development — the DB is needed for `SignalCandidate` persistence and any UI testing.

---

## Conclusion

**The `local-safe` profile is safe for local development.** All trading-critical features are disabled:

- ✅ Engine execution loop: OFF
- ✅ Live orders: OFF
- ✅ Kill switch: ON
- ✅ No exchange credentials stored (storage: OFF)
- ✅ No exchange metadata sync (sync-on-startup: OFF)
- ✅ No AI API calls (DeepSeek: OFF)
- ✅ No Telegram activity (token: empty)
- ✅ No authentication required (auth-enabled: OFF)
- ⚠️ External funding rate API is polled (candidate-source: ON by default)
- ⚠️ Venue modes are inconsistent (mix of testnet/production defaults — irrelevant without credentials/execution)
