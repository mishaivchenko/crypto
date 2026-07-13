---
type: analysis
title: No-Profile Behavior Verification — What Happens With Zero Active Profiles
created: 2026-07-13
tags:
  - spring-boot
  - configuration
  - profiles
  - audit
related:
  - '[[profile-activation-audit]]'
  - '[[profile-inventory]]'
  - '[[application-properties-audit]]'
  - '[[AUDIT-ROUND-3-03]]'
---

# No-Profile Behavior Verification

## Question

What happens when a Spring Boot application runs with **zero active profiles** — no `SPRING_PROFILES_ACTIVE` environment variable, no `--spring.profiles.active` JVM argument, and no programmatic profile activation?

## Mechanism

When `SPRING_PROFILES_ACTIVE` is absent and `--spring.profiles.active` is not provided:

1. **Spring Boot activates the implicit `default` profile** — this is framework built-in behavior when no profiles are explicitly set
2. **Profile-specific YAML files are NOT loaded** for monitor-app and engine-app (they all declare `spring.config.activate.on-profile` which restricts loading to when the matching profile is active)
3. **telegram-bot-app's profile YAMLs ARE loaded unconditionally** — they lack `spring.config.activate.on-profile` and rely on filename convention only, so they load irrespective of the active profile
4. **Only base files apply**: each module's `application.yml` + `platform-core.yml` (monitor-app shared config via `spring.config.import`)
5. **All properties use their `${ENV_VAR:default}` expressions** — since no profile is mounted, every default value in the codebase applies

### Verified by code inspection

- All 3 Application.java classes use plain `SpringApplication.run(Class, args)` — no `setAdditionalProfiles()`, `setActiveProfiles()`, or `SpringApplicationBuilder.profiles()` calls
- Zero `@Profile` annotations exist across all modules
- Zero `application-default.yml` files exist anywhere in the project tree
- `spring.profiles.active` and `spring.profiles.default` are absent from all base `application.yml` files
- No `.env` auto-loading library on classpath
- No IDE run configuration files exist

## Per-Module Effective Configuration

### monitor-app (port 8090)

| Property | Default Expression | Effective Value | Safety |
|----------|-------------------|-----------------|--------|
| Auth enabled | `SECURITY_OPERATOR_AUTH_ENABLED:false` | OFF | ✅ Safe |
| Credential storage enabled | `CREDENTIALS_STORAGE_ENABLED:false` | OFF | ✅ Safe |
| Require master key on startup | `CREDENTIALS_REQUIRE_MASTER_KEY_ON_STARTUP:true` | Gated on `storage.enabled=false` → no throw | ✅ Safe |
| Engine metrics enabled | `MONITOR_ENGINE_METRICS_ENABLED:false` | OFF | ✅ Safe |
| Candidate source enabled | `TRADING_CANDIDATE_SOURCE_ENABLED:true` (matchIfMissing) | **ON — 60s external API poll** | 🔴 **Risk** |
| Metadata sync on startup | `TRADING_METADATA_SYNC_ON_STARTUP:true` | ON (pulls from 5 exchanges) | ⚠️ Unintended I/O |
| Metadata require credentials | `TRADING_METADATA_REQUIRE_CREDENTIALS_ON_STARTUP:false` | OFF | ✅ Safe |
| Metadata schedule | `TRADING_METADATA_SCHEDULE_ENABLED:false` | OFF | ✅ Safe |
| DeepSeek AI | `AI_DEEPSEEK_ENABLED:false` | OFF | ✅ Safe |
| Dev test tool | `MONITOR_DEV_TEST_TOOL_ENABLED:false` | OFF | ✅ Safe |
| Venue access mode | `TRADING_VENUE_ACCESS_MODE:production` | `production` | ✅ Safe (no credentials) |
| Venue-specific modes | `BYBIT_MODE:testnet`, `GATE_MODE:testnet`, `BITGET_MODE:production`, `OKX_MODE:production`, `KUCOIN_MODE:production` | Mixed | ⚠️ Inconsistent but harmless |
| Server port | `MONITOR_SERVER_PORT:8090` | 8090 | ✅ |
| Bootstrap users | `SECURITY_OPERATOR_BOOTSTRAP_USERS:` (empty) | Empty — no users created | ✅ Safe — auth is OFF anyway |

**All 7 `@ConditionalOnProperty(monitor.engine-metrics.enabled=true)` beans** are not created (property defaults to `false`).

**`CredentialStorageStartupValidator` bean exists** (unconditional `@Component`) but its `run()` method checks `properties.isEnabled()` first — since `credentials.storage.enabled: false`, it does not throw.

**`InstrumentMetadataSyncRunner` runs on startup** because `trading.metadata.sync-on-startup: true` — makes HTTP calls to all 5 exchange metadata endpoints. If no credentials are configured (typical for no-profile), the sync fails silently with logged warnings.

**`OperatorAccountService` runs on startup** — attempts to parse `SECURITY_OPERATOR_BOOTSTRAP_USERS` env var. With default empty string, it creates zero accounts (silent no-op).

---

### engine-app (port 8091)

| Property | Default Expression | Effective Value | Safety |
|----------|-------------------|-----------------|--------|
| Execution loop enabled | `ENGINE_EXECUTION_LOOP_ENABLED:false` | OFF | ✅ Safe |
| Live orders enabled | `ENGINE_LIVE_ORDER_ENABLED:false` | OFF | ✅ Safe |
| Kill switch enabled | `ENGINE_KILL_SWITCH_ENABLED:true` | ON | ✅ Safe |
| Metrics publish enabled | `ENGINE_METRICS_PUBLISH_ENABLED:false` | OFF | ✅ Safe |
| Live enabled venues | `ENGINE_LIVE_ENABLED_VENUES:bybit,gate` | bybit, gate | ✅ Safe (loop OFF) |
| Max notional USD | `ENGINE_MAX_NOTIONAL_USD:25` | $25 | ✅ Safe (loop OFF) |
| Venue access mode | `TRADING_VENUE_ACCESS_MODE:testnet` | `testnet` | ✅ Safe |
| Internal token | `INTERNAL_ENGINE_TOKEN:` (empty) | Empty (no token auth) | ⚠️ Modules communicate without token |
| Monitor base URL | `MONITOR_INTERNAL_BASE_URL:http://localhost:8090` | localhost:8090 | ✅ OK for local |
| Server port | `ENGINE_SERVER_PORT:8091` | 8091 | ✅ |

**`EngineMetricsPublisher` bean NOT created** — `@ConditionalOnProperty(prefix="engine.metrics-publish", name="enabled", havingValue="true")` requires the property to be explicitly `true`, and it defaults to `false`.

**All 12 other engine-app beans are unconditional** — they are loaded into the context:
- `EngineExecutionService` — exists but loop guard blocks execution
- `EngineExecutionScheduler` — exists but scheduler method returns early
- `CredentialAwareExecutionPort` — exists, returns FAILED for every order
- `EnginePlanClient` — exists, HTTP client ready but unused
- `EngineController` — exists, REST endpoints active (unversioned `/internal/engine/...`)

**Engine execution scheduler runs at 250ms** — the `@Scheduled` method is invoked, calls `EngineRuntimeControlService.isExecutionLoopEnabled()` which returns `false`, and exits immediately. This is a tiny overhead (~0.1ms) per tick.

---

### telegram-bot-app (no web server, `web-application-type: none`)

| Property | Default Expression | Effective Value | Safety |
|----------|-------------------|-----------------|--------|
| Web app type | `spring.main.web-application-type:none` | `none` | ✅ Correct |
| Bot token | `TELEGRAM_BOT_TOKEN:` (empty default) | Empty string (default) | ✅ Bot beans NOT created |
| Notification chat ID | `TELEGRAM_NOTIFICATION_CHAT_ID:` (empty) | Empty | ✅ |
| Allowed user IDs | `TELEGRAM_ALLOWED_USER_IDS:` (empty) | Empty | ✅ |
| Monitor base URL | `MONITOR_BASE_URL:http://localhost:8090` | localhost:8090 | ✅ |
| Monitor operator token | `MONITOR_OPERATOR_TOKEN:` (empty) | Empty | ⚠️ No auth to monitor |

**⚠️ Critical Finding: Profile YAMLs Always Load Without Profile Guard**

The telegram-bot-app's `application-local-safe.yml` and `application-staging.yml` **do NOT declare `spring.config.activate.on-profile`**. This means they are loaded in ALL profile scenarios, including when NO profile is active.

When no profile is set, property resolution occurs in this order (last wins):
1. `application.yml` → `telegram.bot.token: ${TELEGRAM_BOT_TOKEN:}` (default: empty)
2. `application-local-safe.yml` → `telegram.bot.token: ${TELEGRAM_BOT_TOKEN:}` (same default)
3. `application-staging.yml` → `telegram.bot.token: ${TELEGRAM_BOT_TOKEN}` (NO default)

The staging file overwrites the token property with `${TELEGRAM_BOT_TOKEN}` — a placeholder with **no default value**.

**Impact when `TELEGRAM_BOT_TOKEN` env var is NOT set:**
- `telegram.bot.token` property becomes the literal unresolved placeholder string `${TELEGRAM_BOT_TOKEN}`
- The **4 `@ConditionalOnProperty(name = "telegram.bot.token")` beans** evaluate this as a set, non-"false" value → **beans ARE created** (unexpected)
- The `FundingBot` starts with an invalid token string → Telegram API calls always fail
- The `SignalNotificationScheduler` and `TradeNotificationScheduler` start polling at 30s intervals → each poll triggers API calls that fail authentication
- This constitutes **unnecessary external API calls, unnecessary scheduler activity, and noise in the logs**

**When `TELEGRAM_BOT_TOKEN` IS set** (e.g., from `.env` or env var): the staging file's placeholder resolves normally, token works fine.

**This is a bug:** the profile YAMLs should declare `spring.config.activate.on-profile` to prevent cross-contamination. The same pattern was already flagged in prior audit findings for telegram-bot-app.

---

## When "No Profile" Occurs in Practice

| Scenario | Profile Active | Notes |
|----------|---------------|-------|
| `./gradlew bootRun*` | `local-safe` | ✅ build.gradle always sets it |
| `java -jar app.jar` (direct) | **None** | ⚠️ Must set `SPRING_PROFILES_ACTIVE` or `--spring.profiles.active` |
| `java -jar app.jar --spring.profiles.active=X` | `X` | ✅ User sets explicitly |
| `docker compose up` (root `docker-compose.yml`) | **None** | ❌ Does NOT set SPRING_PROFILES_ACTIVE |
| `docker compose -f deploy/docker-compose.yml up` | `prod-like`/`staging` | ✅ Hardcoded per service |
| CI `build`/`test`/`bootJar` | **None** | ✅ Compile-only — no runtime profile needed |
| CI deploy → staging | `prod-like`/`staging` | ✅ Via deploy Compose |
| Production via Docker only | **None** | ❌ Root Dockerfile has no ENTRYPOINT profile |

**`docker compose up` (root `docker-compose.yml`) is the most common real-world "no profile" scenario** — this Compose file bundles monitor + engine + prometheus + grafana for local development with observability. It does NOT set `SPRING_PROFILES_ACTIVE` for any service, though it does set many env vars directly (including `MONITOR_ENGINE_METRICS_ENABLED=true` and `ENGINE_METRICS_PUBLISH_ENABLED=true` which bypass the profile mechanism).

---

## Risk Summary

| Risk | Module | Severity | Impact |
|------|--------|----------|--------|
| Candidate source polls external API every 60s | monitor-app | 🔴 **Medium** | Unintended external calls from `uainvest.com.ua` — no profile disables this |
| telegram-bot-app profile YAMLs load without guard | telegram-bot-app | 🔴 **Medium** | If TELEGRAM_BOT_TOKEN unset: beans created with invalid token, unnecessary scheduler load |
| No token auth between modules | engine-app + monitor-app | ⚠️ **Low** | `INTERNAL_ENGINE_TOKEN` defaults to empty — no runtime impact since loop is OFF |
| Metadata syncs on startup (5 exchange API calls) | monitor-app | ⚠️ **Low** | Silently fails without credentials — harmless but unintended external I/O |
| Inconsistent venue defaults (production vs testnet) | monitor-app | ⚠️ **Low** | Harmless with no execution loop |
| All 13 engine-app beans loaded unconditionally | engine-app | ✅ **None** | Design choice — loop guard prevents any execution |

## Safety Verdict

**Safe for read-only use.** No execution loop, no live orders, kill switch ON. The application can start and serve UI/monitoring without risk of executing trades.

### What works correctly with no profile:
- Monitor UI loads and displays dashboard data
- Engine starts and ticks at 250ms (immediate exit via loop guard)
- All REST endpoints respond (no auth, read/write access to all data)
- No live exchange activity (loop OFF, live orders OFF, kill switch ON)

### The active risk is external API calls initiated without explicit user intent:
1. `uainvest.com.ua` polled every 60s for funding rate signals
2. 5 exchange metadata endpoints called on startup
3. Telegram bot polling (if TELEGRAM_BOT_TOKEN is somehow set)

## Recommendations

1. **Set `trading.candidate-source.enabled: false` in base `application.yml`** to prevent unintended external API polling when no profile is active (matches the pattern used for other safety-critical defaults)
2. **Add `spring.config.activate.on-profile`** to telegram-bot-app's `application-local-safe.yml` and `application-staging.yml` to prevent cross-profile contamination (previously flagged finding — this is the concrete impact demonstration)
3. **Add `SPRING_PROFILES_ACTIVE` to root `docker-compose.yml`** to ensure explicit profile activation when using the local observability setup
