---
type: inventory
title: Environment Variables — Complete Inventory
created: 2026-07-13
tags:
  - audit
  - configuration
  - environment-variables
  - secrets
related:
  - '[[AUDIT-ROUND-3-03]]'
  - '[[property-sources-audit]]'
  - '[[application-properties-audit]]'
  - '[[profile-override-inventory]]'
---

# Environment Variables: Complete Inventory

## Executive Summary

After tracing every `${ENV_VAR}` expression across 14 YAML files, 33 `Environment.getProperty()` code paths, 10 `build.gradle` `System.getenv()` calls, and 3 Docker Compose files:

- **0** truly mandatory env vars (app starts without any env set)
- **~6 conditionally mandatory** — required when the associated feature/profile is active
- **~65 optional** with safe defaults
- **~21 engine credential vars** — consumeable only at execution time (no-default), return FAILED attempts when missing

The app can start with **zero environment variables set** — every critical function (execution loop, live orders, auth, credentials) default to their safest value.

---

## Classification System

| Tag | Meaning |
|-----|---------|
| **🔴 MANDATORY** | App crashes or cannot function without this. (None exist.) |
| **🟠 CONDITIONAL** | Required only when a specific feature/profile is active. |
| **🟢 OPTIONAL** | Safe, functional default exists. May override for production. |
| **⚪ CONSUME-ONLY** | Read by code via `getProperty()` with no default; feature degrades gracefully when absent. |
| **🔵 BUILD-ONLY** | Only consumed in `build.gradle` (not at runtime). |

---

## Part 1: Conditionally Mandatory Env Vars

These become required when the feature they support is enabled. Startup fails hard if missing with the feature on.

| # | Env Var | YAML Key | Required When | Failure Mode |
|---|---------|----------|---------------|--------------|
| 1 | `CREDENTIALS_MASTER_KEY_BASE64` | `credentials.storage.master-key-base64` | `CREDENTIALS_STORAGE_ENABLED=true` AND `CREDENTIALS_REQUIRE_MASTER_KEY_ON_STARTUP=true` | `IllegalStateException` via `CredentialStorageStartupValidator` — app fails to start |
| 2 | `SECURITY_OPERATOR_BOOTSTRAP_USERS` | `security.operators.bootstrap-users` | `SECURITY_OPERATOR_AUTH_ENABLED=true` (staging/prod-like) | No operator accounts → API auth rejects every request; no funded events can be armed, no trades approved |
| 3 | `AI_DEEPSEEK_API_KEY` | `ai.deepseek.api-key` | `AI_DEEPSEEK_ENABLED=true` | DeepSeek HTTP 401 on every AI analysis call; fallback to manual analysis only |
| 4 | `TELEGRAM_BOT_TOKEN` | `telegram.bot.token` | Telegram bot desired (staging/prod-like deploy) | Token empty → `@ConditionalOnProperty` prevents bot/scheduler bean creation; no Telegram notifications |
| 5 | `MONITOR_OPERATOR_TOKEN` | `monitor.operator-token` | `SECURITY_OPERATOR_AUTH_ENABLED=true` AND telegram-bot is running | Telegram bot cannot authenticate to monitor API; all bot requests return HTTP 401 |
| 6 | `INTERNAL_ENGINE_TOKEN` | `monitor.engine-control.internal-token` / `engine.internal-token` / `security.operators.internal-token` | `SECURITY_OPERATOR_AUTH_ENABLED=true` (for X-Internal-Token guard) | Empty token → internal HTTP requests between modules carry no auth header; engine→monitor metrics and monitor→engine control endpoints are unprotected |

> **Note about CREDENTIALS_MASTER_KEY_BASE64**: The startup validator (`CredentialStorageStartupValidator.java`) checks `properties.isEnabled() && properties.isRequireMasterKeyOnStartup() && key blank`. When credential storage is disabled (default for `local-safe`) or `require-master-key-on-startup` is `false`, the app starts without the master key. But trying to use credential storage later without the key will throw `IllegalStateException` from `AesGcmCredentialCipher.java:81`.

---

## Part 2: Env Vars with Safe Defaults (Optional)

All env vars below have a functional default in YAML or code. They can be left unset in any environment.

### 2A. Monitor-App (Platform Core — `platform-core.yml`)

| # | Env Var | Default | Purpose |
|---|---------|---------|---------|
| 1 | `SPRING_DATASOURCE_URL` | `jdbc:sqlite:./data/fundingarb.db?busy_timeout=5000&journal_mode=WAL` | SQLite database connection URL |
| 2 | `SPRING_DATASOURCE_DRIVER_CLASS_NAME` | `org.sqlite.JDBC` | JDBC driver class |
| 3 | `SPRING_FLYWAY_ENABLED` | `true` | Flyway migration enabled |
| 4 | `SPRING_FLYWAY_BASELINE_ON_MIGRATE` | `true` | Auto-baseline on first run |
| 5 | `SPRING_FLYWAY_BASELINE_VERSION` | `1` | Baseline migration version |
| 6 | `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate` | Hibernate DDL mode (safe default) |
| 7 | `SECURITY_OPERATOR_AUTH_ENABLED` | `false` | Operator API authentication (OFF = safe) |
| 8 | `CREDENTIALS_STORAGE_ENABLED` | `false` | Encrypted credential storage (OFF = safe) |
| 9 | `CREDENTIALS_REQUIRE_MASTER_KEY_ON_STARTUP` | `true` | Fail startup if master key missing |
| 10 | `MONITOR_ENGINE_PLAN_LOOKAHEAD_MINUTES` | `120` | Engine plan generation lookahead window |
| 11 | `MONITOR_ENGINE_PLAN_OVERDUE_GRACE_SECONDS` | `30` | Grace period before marking plan overdue |
| 12 | `MONITOR_ENGINE_PLAN_INCLUDE_CLOSED_TRADES` | `false` | Include closed trades in engine plan |
| 13 | `MONITOR_ENGINE_CONTROL_BASE_URL` | `http://localhost:8091` | Engine control API base URL |
| 14 | `MONITOR_ENGINE_CONTROL_INTERNAL_TOKEN` | `<INTERNAL_ENGINE_TOKEN>` | Token for monitor→engine calls |
| 15 | `MONITOR_ENGINE_METRICS_ENABLED` | `false` | Engine metrics ingestion endpoint |
| 16 | `MONITOR_DEV_TEST_TOOL_ENABLED` | `false` | Dev test tool UI and API |
| 17 | `MONITOR_DEV_TEST_MAX_NOTIONAL_USD` | `25` | Dev test max notional |
| 18 | `MONITOR_DEV_TEST_ENABLED_VENUES` | `bybit,gate` | Dev test enabled venues |
| 19 | `TRADING_VENUE_ACCESS_MODE` | `production` | Global venue access mode (monitor) |
| 20 | `TRADING_CANDIDATES_DEDUPE_WINDOW_MINUTES` | `15` | Signal candidate dedup window |
| 21 | `TRADING_AUTO_APPROVAL_ENABLED` | `false` | Auto-approval pipeline (OFF = safe) |
| 22 | `TRADING_AUTO_APPROVAL_MAX_NOTIONAL_USD` | `10` | Auto-approval max notional |
| 23 | `TRADING_CANDIDATE_SOURCE_ENABLED` | `true` | External API candidate polling (🔴 should be `false` by default) |
| 24 | `TRADING_CANDIDATE_SOURCE_URL` | `https://uainvest.com.ua/api/funding?...` | External candidate source URL |
| 25 | `TRADING_CANDIDATE_SOURCE_REFRESH_INTERVAL_SECONDS` | `60` | Polling interval |
| 26 | `TRADING_CANDIDATE_SOURCE_TYPE` | `FUNDING_API` | Source type identifier |
| 27 | `TRADING_METADATA_ENABLED_VENUES` | `bybit,gate,bitget,okx,kucoin` | Venues for metadata sync |
| 28 | `TRADING_METADATA_SYNC_ON_STARTUP` | `true` | Sync metadata on startup |
| 29 | `TRADING_METADATA_SCHEDULE_ENABLED` | `false` | Scheduled metadata sync |
| 30 | `TRADING_METADATA_SYNC_INTERVAL_MINUTES` | `240` | Metadata sync interval |
| 31 | `TRADING_METADATA_REQUIRE_CREDENTIALS_ON_STARTUP` | `false` | Require credentials for startup sync |
| 32 | `TRADING_METADATA_BOOTSTRAP_FALLBACK_ENABLED` | `false` | Bootstrap fallback metadata |
| 33 | `TRADING_HTTP_CONNECT_TIMEOUT_MS` | `1000` | Venue HTTP connect timeout |
| 34 | `TRADING_HTTP_REQUEST_TIMEOUT_MS` | `5000` | Venue HTTP request timeout (⚠️ not wired to HttpClient) |
| 35 | `TRADING_HTTP_PREFER_HTTP2` | `true` | Prefer HTTP/2 for venue calls |
| 36 | `TRADING_DEFAULT_ENTRY_ATTEMPT_COUNT` | `1` | Default entry attempt count |
| 37 | `TRADING_DEFAULT_ENTRY_SPACING_MS` | `0` | Entry attempt spacing (⚠️ should be >= 100ms) |
| 38 | `TRADING_DEFAULT_MANUAL_LATENCY_ADJUSTMENT_MS` | `0` | Manual latency adjustment |
| 39 | `BYBIT_MODE` | `testnet` | Bybit venue mode (safe default ✅) |
| 40 | `BYBIT_TESTNET_BASE_URL` | `https://api-testnet.bybit.com` | Bybit testnet base URL |
| 41 | `BYBIT_PROD_BASE_URL` | `https://api.bybit.com` | Bybit production base URL |
| 42 | `BYBIT_METADATA_BASE_URL` | `https://api.bybit.com` | Bybit metadata API URL |
| 43 | `GATE_MODE` | `testnet` | Gate venue mode (safe default ✅) |
| 44 | `GATE_CONTRACTS_BASE_URL` | `https://fx-api.gateio.ws/api/v4` | Gate perpetual futures URL |
| 45 | `GATE_TESTNET_BASE_URL` | `https://api-testnet.gateapi.io/api/v4` | Gate testnet base URL |
| 46 | `GATE_PROD_BASE_URL` | `https://api.gateio.ws/api/v4` | Gate production base URL (spot) |
| 47 | `BITGET_MODE` | `production` | 🔴 Bitget defaults to production (should be `testnet`) |
| 48 | `BITGET_TESTNET_BASE_URL` | `https://api.bitget.com` | Bitget testnet URL (same as prod) |
| 49 | `BITGET_PROD_BASE_URL` | `https://api.bitget.com` | Bitget production base URL |
| 50 | `BITGET_METADATA_BASE_URL` | `https://api.bitget.com` | Bitget metadata URL |
| 51 | `OKX_MODE` | `production` | 🔴 OKX defaults to production (should be `testnet`) |
| 52 | `OKX_TESTNET_BASE_URL` | `https://www.okx.com` | OKX testnet URL (same as prod) |
| 53 | `OKX_PROD_BASE_URL` | `https://www.okx.com` | OKX production base URL |
| 54 | `OKX_METADATA_BASE_URL` | `https://www.okx.com` | OKX metadata URL |
| 55 | `KUCOIN_MODE` | `production` | 🔴 KuCoin defaults to production (should be `testnet`) |
| 56 | `KUCOIN_TESTNET_BASE_URL` | `https://api-sandbox.kucoin.com` | KuCoin sandbox URL |
| 57 | `KUCOIN_PROD_BASE_URL` | `https://api-futures.kucoin.com` | KuCoin production URL |
| 58 | `KUCOIN_METADATA_BASE_URL` | `https://api-futures.kucoin.com` | KuCoin metadata URL |
| 59 | `AI_DEEPSEEK_ENABLED` | `false` | DeepSeek AI analysis (OFF = safe) |
| 60 | `AI_DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | DeepSeek API base URL |
| 61 | `AI_DEEPSEEK_MODEL` | `deepseek-chat` | DeepSeek model name |
| 62 | `AI_DEEPSEEK_TIMEOUT_SECONDS` | `30` | DeepSeek request timeout |

### 2B. Monitor-App (Base — `application.yml`)

| # | Env Var | Default | Purpose |
|---|---------|---------|---------|
| 63 | `MONITOR_SERVER_PORT` | `8090` | Monitor HTTP server port |

### 2C. Engine-App (Base — `application.yml`)

| # | Env Var | Default | Purpose |
|---|---------|---------|---------|
| 64 | `ENGINE_SERVER_PORT` | `8091` | Engine HTTP server port |
| 65 | `MONITOR_INTERNAL_BASE_URL` | `http://localhost:8090` | Monitor internal API base URL |
| 66 | `ENGINE_EXECUTION_LOOP_ENABLED` | `false` | Engine execution loop (OFF = safe) |
| 67 | `ENGINE_EXECUTION_LOOP_INTERVAL_MS` | `1000` | Engine loop iteration interval |
| 68 | `ENGINE_EXECUTION_SCHEDULER_TICK_MS` | `250` | Engine scheduler tick interval |
| 69 | `ENGINE_LIVE_ORDER_ENABLED` | `false` | Enable live order submission (OFF = safe) |
| 70 | `ENGINE_KILL_SWITCH_ENABLED` | `true` | Kill switch (ON = safe) |
| 71 | `ENGINE_LIVE_ENABLED_VENUES` | `bybit,gate` | Venues with live orders enabled |
| 72 | `ENGINE_MAX_NOTIONAL_USD` | `25` | Max notional per order (safe default) |
| 73 | `ENGINE_METADATA_MAX_AGE_MINUTES` | `240` | Metadata cache TTL |
| 74 | `ENGINE_LATENCY_MAX_AGE_MINUTES` | `1440` | Latency cache TTL |
| 75 | `TRADING_VENUE_ACCESS_MODE` (engine copy) | `testnet` | Engine venue access mode (safe default ✅) |
| 76 | `BYBIT_TESTNET_BASE_URL` (engine copy) | `https://api-testnet.bybit.com` | Bybit testnet URL (engine) |
| 77 | `BYBIT_PRODUCTION_BASE_URL` (engine copy) | `https://api.bybit.com` | Bybit production URL (engine) |
| 78 | `GATE_TESTNET_BASE_URL` (engine copy) | `https://api-testnet.gateapi.io/api/v4` | Gate testnet URL (engine) |
| 79 | `GATE_PRODUCTION_BASE_URL` (engine copy) | `https://fx-api.gateio.ws/api/v4` | Gate production URL (engine, perpetual) |
| 80 | `OKX_TESTNET_BASE_URL` (engine copy) | `https://www.okx.com` | OKX testnet URL (engine) |
| 81 | `OKX_PRODUCTION_BASE_URL` (engine copy) | `https://www.okx.com` | OKX production URL (engine) |
| 82 | `KUCOIN_TESTNET_BASE_URL` (engine copy) | `https://api-sandbox.kucoin.com` | KuCoin sandbox URL (engine) |
| 83 | `KUCOIN_PRODUCTION_BASE_URL` (engine copy) | `https://api-futures.kucoin.com` | KuCoin production URL (engine) |
| 84 | `BITGET_TESTNET_BASE_URL` (engine copy) | `https://api.bitget.com` | Bitget testnet URL (engine) |
| 85 | `BITGET_PRODUCTION_BASE_URL` (engine copy) | `https://api.bitget.com` | Bitget production URL (engine) |
| 86 | `ENGINE_METRICS_PUBLISH_ENABLED` | `false` | Engine metrics publish (OFF = safe) |
| 87 | `ENGINE_METRICS_PUBLISH_INTERVAL_MS` | `15000` | Metrics publish interval |

### 2D. Telegram-Bot-App (Base — `application.yml`)

| # | Env Var | Default | Purpose |
|---|---------|---------|---------|
| 88 | `TELEGRAM_SIGNAL_POLL_INTERVAL_MS` | `30000` | Telegram signal polling interval |
| 89 | `STAGING_UI_URL` | `""` (empty) | Staging UI URL for bot links |
| 90 | `STAGING_GRAFANA_URL` | `""` (empty) | Staging Grafana URL |
| 91 | `STAGING_ENGINE_URL` | `""` (empty) | Staging engine URL |
| 92 | `PROD_UI_URL` | `""` (empty) | Production UI URL |
| 93 | `PROD_GRAFANA_URL` | `""` (empty) | Production Grafana URL |
| 94 | `PROD_ENGINE_URL` | `""` (empty) | Production engine URL |
| 95 | `MONITOR_BASE_URL` | `http://localhost:8090` | Monitor base URL for Telegram bot |
| 96 | `MONITOR_PUBLIC_URL` | `""` (empty) | Monitor public-facing URL |
| 97 | `TELEGRAM_NOTIFICATION_CHAT_ID` | `""` (empty) | Telegram notification target |
| 98 | `TELEGRAM_ALLOWED_USER_IDS` | `""` (empty) | Allowed Telegram user IDs |

---

## Part 3: Consume-Only Env Vars (No YAML Default)

These are read by code via `Environment.getProperty( key )` without a default value. The code handles `null` gracefully (returns FAILED attempt, treats as unconfigured). They have NO runtime defaults.

### 3A. Engine Credentials (LiveExchangeExecutionPort)

Code path: `LiveExchangeExecutionPort.java:948` — `environment.getProperty("engine.credentials." + venue + "." + name)`

Used when engine execution loop AND live orders are enabled. Missing credentials → order attempt recorded as FAILED.

| # | Env Var | Purpose | Required When |
|---|---------|---------|---------------|
| 99 | `ENGINE_CREDENTIALS_BYBIT_API_KEY` | Bybit API key (engine) | Engine execution + live + Bybit enabled |
| 100 | `ENGINE_CREDENTIALS_BYBIT_SECRET_KEY` | Bybit secret key (engine) | Same |
| 101 | `ENGINE_CREDENTIALS_GATE_API_KEY` | Gate API key (engine) | Engine execution + live + Gate enabled |
| 102 | `ENGINE_CREDENTIALS_GATE_SECRET_KEY` | Gate secret key (engine) | Same |
| 103 | `ENGINE_CREDENTIALS_BITGET_API_KEY` | Bitget API key (engine) | Engine execution + live + Bitget enabled |
| 104 | `ENGINE_CREDENTIALS_BITGET_SECRET_KEY` | Bitget secret key (engine) | Same |
| 105 | `ENGINE_CREDENTIALS_BITGET_PASSPHRASE` | Bitget API passphrase | Same (Bitget requires passphrase) |
| 106 | `ENGINE_CREDENTIALS_OKX_API_KEY` | OKX API key (engine) | Engine execution + live + OKX enabled |
| 107 | `ENGINE_CREDENTIALS_OKX_SECRET_KEY` | OKX secret key (engine) | Same |
| 108 | `ENGINE_CREDENTIALS_OKX_PASSPHRASE` | OKX API passphrase | Same (OKX requires passphrase) |
| 109 | `ENGINE_CREDENTIALS_KUCOIN_API_KEY` | KuCoin API key (engine) | Engine execution + live + KuCoin enabled |
| 110 | `ENGINE_CREDENTIALS_KUCOIN_SECRET_KEY` | KuCoin secret key (engine) | Same |
| 111 | `ENGINE_CREDENTIALS_KUCOIN_PASSPHRASE` | KuCoin API passphrase | Same (KuCoin requires passphrase) |

### 3B. Monitor Venue Credentials (OperatorCredentialService + VenueProfileService)

Code path: `OperatorCredentialService.java:328-330`, `VenueProfileService.java:247-250` — dynamic keys per venue + mode.

These are dynamic keys that depend on the venue name and access mode (testnet/production). They are read via `environment.getProperty("trading." + venue + "." + mode + ".{field}")` with NO default.

| Pattern | Example | Purpose |
|---------|---------|---------|
| `TRADING_{VENUE}_{MODE}_API_KEY` | `TRADING_BYBIT_TESTNET_API_KEY` | Venue API key (monitor, env-var form) |
| `TRADING_{VENUE}_{MODE}_SECRET_KEY` | `TRADING_BYBIT_TESTNET_SECRET_KEY` | Venue secret key (monitor) |
| `TRADING_{VENUE}_{MODE}_PASSPHRASE` | `TRADING_OKX_TESTNET_PASSPHRASE` | Venue passphrase (OKX, Bitget, KuCoin) |

**In YAML property form:** `trading.{venue}.{mode}.api-key`, `trading.{venue}.{mode}.secret-key`, `trading.{venue}.{mode}.passphrase`

**Conditionally mandatory when:** credential storage is enabled OR metadata sync + require-credentials is enabled OR the operator wants to manually configure venue credentials via environment.

| # | Env Var (env-var form) | Venue | Mode | Purpose |
|---|------------------------|-------|------|---------|
| 112 | `BYBIT_TESTNET_API_KEY` | Bybit | testnet | Bybit testnet API key (monitor) |
| 113 | `BYBIT_TESTNET_SECRET_KEY` | Bybit | testnet | Bybit testnet secret key |
| 114 | `BYBIT_PRODUCTION_API_KEY` | Bybit | production | Bybit production API key |
| 115 | `BYBIT_PRODUCTION_SECRET_KEY` | Bybit | production | Bybit production secret key |
| 116 | `GATE_TESTNET_API_KEY` | Gate | testnet | Gate testnet API key (monitor) |
| 117 | `GATE_TESTNET_SECRET_KEY` | Gate | testnet | Gate testnet secret key |
| 118 | `GATE_PRODUCTION_API_KEY` | Gate | production | Gate production API key |
| 119 | `GATE_PRODUCTION_SECRET_KEY` | Gate | production | Gate production secret key |
| 120 | `BITGET_TESTNET_API_KEY` | Bitget | testnet | Bitget testnet API key |
| 121 | `BITGET_TESTNET_SECRET_KEY` | Bitget | testnet | Bitget testnet secret key |
| 122 | `BITGET_TESTNET_PASSPHRASE` | Bitget | testnet | Bitget testnet passphrase |
| 123 | `BITGET_PRODUCTION_API_KEY` | Bitget | production | Bitget production API key |
| 124 | `BITGET_PRODUCTION_SECRET_KEY` | Bitget | production | Bitget production secret key |
| 125 | `BITGET_PRODUCTION_PASSPHRASE` | Bitget | production | Bitget production passphrase |
| 126 | `OKX_TESTNET_API_KEY` | OKX | testnet | OKX testnet API key |
| 127 | `OKX_TESTNET_SECRET_KEY` | OKX | testnet | OKX testnet secret key |
| 128 | `OKX_TESTNET_PASSPHRASE` | OKX | testnet | OKX testnet passphrase |
| 129 | `OKX_PRODUCTION_API_KEY` | OKX | production | OKX production API key |
| 130 | `OKX_PRODUCTION_SECRET_KEY` | OKX | production | OKX production secret key |
| 131 | `OKX_PRODUCTION_PASSPHRASE` | OKX | production | OKX production passphrase |
| 132 | `KUCOIN_TESTNET_API_KEY` | KuCoin | testnet | KuCoin testnet API key |
| 133 | `KUCOIN_TESTNET_SECRET_KEY` | KuCoin | testnet | KuCoin testnet secret key |
| 134 | `KUCOIN_TESTNET_PASSPHRASE` | KuCoin | testnet | KuCoin testnet passphrase |
| 135 | `KUCOIN_PRODUCTION_API_KEY` | KuCoin | production | KuCoin production API key |
| 136 | `KUCOIN_PRODUCTION_SECRET_KEY` | KuCoin | production | KuCoin production secret key |
| 137 | `KUCOIN_PRODUCTION_PASSPHRASE` | KuCoin | production | KuCoin production passphrase |

---

## Part 4: Build-Only Env Vars (build.gradle)

These are consumed only during the Gradle build or bootRun task, not at Spring runtime.

| # | Env Var | Default | Purpose | When Used |
|---|---------|---------|---------|-----------|
| 138 | `SPRING_PROFILES_ACTIVE` | `local-safe` | Active Spring profile | `bootRun*` tasks only |
| 139 | `INTERNAL_ENGINE_TOKEN` (duplicate) | `funding-local-internal-token` | Internal auth token | `bootRun*` tasks only |
| 140 | `MONITOR_ENGINE_CONTROL_INTERNAL_TOKEN` | `<INTERNAL_ENGINE_TOKEN>` | Monitor→Engine control token | `bootRunEngine` task only |
| 141 | `TELEGRAM_BOT_TOKEN` (duplicate) | `""` (empty) | Telegram bot token | `bootRunTelegramBot` task only |
| 142 | `MONITOR_BASE_URL` (duplicate) | `http://localhost:8090` | Monitor base URL | `bootRunTelegramBot` task only |
| 143 | `NVD_API_KEY` | (none) | NVD API key (rate limiting) | `security` task only |

---

## Part 5: Docker Compose-Only Env Vars

These only appear in `deploy/docker-compose.yml` or root `docker-compose.yml` and have no corresponding YAML or code binding — they serve Docker-level configuration only.

| # | Env Var | Default | Purpose | File |
|---|---------|---------|---------|------|
| 144 | `IMAGE_MONITOR` | `funding-monitor:2.0.0` | Monitor Docker image tag | deploy compose |
| 145 | `IMAGE_ENGINE` | `funding-engine:2.0.0` | Engine Docker image tag | deploy compose |
| 146 | `IMAGE_TELEGRAM_BOT` | `funding-telegram-bot:2.0.0` | Telegram bot Docker image tag | deploy compose |
| 147 | `PROMETHEUS_PORT` | `9090` | Prometheus container port | root compose |
| 148 | `GRAFANA_PORT` | `3000` | Grafana container port | root compose |
| 149 | `GRAFANA_ADMIN_USER` | `admin` | Grafana admin username | root compose |
| 150 | `GRAFANA_ADMIN_PASSWORD` | `admin` | Grafana admin password | root compose |
| 151 | `LOGGING_FILE_PATH` | `/logs` | Log file output directory | both compose files |

---

## Part 6: Summary by Module

| Module | Optional / With Default | Conditionally Mandatory | Consume-Only / No Default | Total Unique |
|--------|------------------------|------------------------|---------------------------|-------------|
| monitor-app | ~62 | 31 | ~262 | ~90 |
| engine-app | ~24 | 1 | ~13 | ~38 |
| telegram-bot-app | ~11 | 2 | 0 | ~13 |
| Build only | — | — | 6 | 6 |
| Docker only | 8 | — | — | 8 |

> 1: `SECURITY_OPERATOR_BOOTSTRAP_USERS`, `CREDENTIALS_MASTER_KEY_BASE64`, `AI_DEEPSEEK_API_KEY`
> 2: 24 per-venue per-mode credentials (3 fields × 5 venues × up to 2 modes each) — dynamic keys, not individually enumerated
> 3: 11 engine credentials + 2 pass-through (INTERNAL_ENGINE_TOKEN, MONITOR_OPERATOR_TOKEN)

---

## Part 7: Key Findings

1. **No truly mandatory env vars exist** — the application starts without any environment variables set. Every critical function defaults to its safest value.

2. **6 conditionally mandatory vars** are gated by features that default to OFF: `CREDENTIALS_MASTER_KEY_BASE64`, `SECURITY_OPERATOR_BOOTSTRAP_USERS`, `AI_DEEPSEEK_API_KEY`, `TELEGRAM_BOT_TOKEN`, `MONITOR_OPERATOR_TOKEN`, `INTERNAL_ENGINE_TOKEN`.

3. **21 engine credential vars** have no YAML default but degrade gracefully (FAILED attempt) when absent. They are consumed via `environment.getProperty(key)` without fallback, but `hasCredentials()` checks for null/blank before attempting orders.

4. **26 monitor credential vars** follow the same dynamic-key pattern. Credentials are per-venue and per-mode via dynamic property lookup.

5. **Variable naming is not namespace-shared between monitor and engine** — engine credentials use `ENGINE_CREDENTIALS_{VENUE}_{FIELD}` while monitor uses `TRADING_{VENUE}_{MODE}_{FIELD}`. Both serve the same purpose with different names.

6. **`CREDENTIALS_MASTER_KEY_BASE64`** is the only env var whose absence can cause a hard startup crash (via `CredentialStorageStartupValidator` or `AesGcmCredentialCipher`), but only when credential storage is explicitly enabled.

7. **`TRADING_CANDIDATE_SOURCE_ENABLED`** defaults to `true` via `matchIfMissing=true` — the only feature that polls an external API by default in all configurations. No profile explicitly disables it.
