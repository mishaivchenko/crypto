---
type: analysis
title: Staging Profile — Exact Configuration Verification
created: 2026-07-13
tags:
  - audit
  - profiles
  - staging
  - configuration
related:
  - '[[local-safe-profile-verification]]'
  - '[[testnet-profile-verification]]'
  - '[[profile-inventory]]'
  - '[[application-properties-audit]]'
---

# Staging Profile — Exact Configuration Verification

## 1. Files involved

| Module | File | `spring.config.activate.on-profile` |
|--------|------|-------------------------------------|
| monitor-app | `monitor-app/src/main/resources/application-staging.yml` | ✅ `on-profile: staging` |
| engine-app | `engine-app/src/main/resources/application-staging.yml` | ✅ `on-profile: staging` |
| telegram-bot-app | `telegram-bot-app/src/main/resources/application-staging.yml` | ❌ **Missing** — relies on filename convention only |

## 2. monitor-app staging — explicit overrides (5)

| Property | Staging value | Default (platform-core.yml) | Diff |
|----------|---------------|----------------------------|------|
| `security.operators.auth-enabled` | **`true`** | `false` | Auth ON |
| `credentials.storage.enabled` | **`true`** | `false` | Credential storage enabled |
| `credentials.storage.require-master-key-on-startup` | **`true`** | `true` | Same (unchanged) |
| `monitor.engine-metrics.enabled` | **`true`** | `false` | Engine metrics ON |
| `trading.metadata.require-credentials-on-startup` | **`false`** | `false` | Same (unchanged) |

**Inherited-but-notable defaults (not overridden):**

| Property | Default value | Assessment |
|----------|--------------|------------|
| `ai.deepseek.enabled` | `false` | ✅ Off — must be explicitly enabled via env |
| `trading.auto-approval.enabled` | `false` | ✅ Off — no automated trade creation |
| `monitor.dev-test-tool.enabled` | `false` | ✅ Off |
| `trading.metadata.sync-on-startup` | `true` | ⚠️ Metadata sync runs on startup — attempts exchange API connections |
| `trading.metadata.schedule-enabled` | `false` | ✅ Scheduled sync off |
| `trading.candidate-source.enabled` | `true` | ⚠️ External API (`uainvest.com.ua`) polled every 60s |
| `trading.venue-access.mode` | `production` | ⚠️ "production" mode — used for credential resolution, not order routing (execution is off) |

## 3. engine-app staging — explicit overrides (2)

| Property | Staging value | Default (engine application.yml) | Diff |
|----------|--------------|----------------------------------|------|
| `engine.execution-loop-enabled` | **`false`** | `false` | Same (unchanged — loop stays OFF) |
| `engine.metrics-publish.enabled` | **`true`** | `false` | Metrics publishing ON |

**Critical runtime guards (inherited defaults, not overridden):**

| Property | Default | Safe? |
|----------|---------|-------|
| `engine.live-order-enabled` | `false` | ✅ OFF |
| `engine.kill-switch-enabled` | `true` | ✅ ON |
| `engine.trading-venue-access-mode` | `testnet` | ✅ Testnet URLs |
| `engine.execution-loop-enabled` | `false` | ✅ OFF (explicitly confirmed) |
| `engine.live-enabled-venues` | `bybit,gate` | ⚠️ Listed but irrelevant (execution off) |
| `engine.max-notional-usd` | `25` | ⚠️ Irrelevant (execution off) |

## 4. telegram-bot-app staging — explicit overrides (6)

| Property | Staging value | Default (telegram application.yml) | Notes |
|----------|--------------|-----------------------------------|-------|
| `telegram.bot.token` | `${TELEGRAM_BOT_TOKEN}` | `${TELEGRAM_BOT_TOKEN:}` | Token from env var |
| `telegram.bot.notification-chat-id` | `${TELEGRAM_NOTIFICATION_CHAT_ID:}` | `${TELEGRAM_NOTIFICATION_CHAT_ID:}` | Same default |
| `telegram.bot.allowed-user-ids` | `${TELEGRAM_ALLOWED_USER_IDS:}` | `${TELEGRAM_ALLOWED_USER_IDS:}` | Same default |
| `monitor.base-url` | `${MONITOR_BASE_URL:http://monitor:8090}` | `${MONITOR_BASE_URL:http://localhost:8090}` | Points to Docker container name |
| `monitor.public-url` | `${MONITOR_PUBLIC_URL:https://crypto-monitor.org}` | `${MONITOR_PUBLIC_URL:}` | Public-facing URL configured |
| `logging.level.com.crypto.funding.telegram` | `INFO` | (not set) | Explicit INFO level |

## 5. Docker Compose usage of staging profile

From `deploy/docker-compose.yml`:

| Service | Profile | Purpose |
|---------|---------|---------|
| monitor | `prod-like` | Production-like monitoring |
| engine | `prod-like` | Production-like execution (loop OFF by default) |
| telegram-bot | **`staging`** | Uses staging because no `prod-like` exists for telegram-bot |

This means staging is the **intended production profile for telegram-bot-app** — it's the env-linked, non-default configuration that makes the bot functional in a deployed context.

## 6. Key findings

### 6.1 🟢 Safe — all critical trading protections active

- **Auth ON:** Monitor API requires operator authentication
- **Credential storage ON with master key required:** Encrypted exchange credentials, won't start without master key
- **Engine loop OFF:** No automated execution
- **Engine live orders OFF:** `CredentialAwareExecutionPort` returns FAILED for all order submissions
- **Engine kill switch ON:** Safety mechanism enabled
- **Auto-approval OFF:** No automated trade creation
- **DeepSeek AI OFF:** Requires explicit env override

### 6.2 🟡 Observation — engine and prod-like are identical

The engine-app `application-staging.yml` and `application-prod-like.yml` are **byte-for-byte identical**:
- Both set `engine.execution-loop-enabled: false`
- Both set `engine.metrics-publish.enabled: true`

This means staging and prod-like profiles currently have **identical behavioral impact on engine-app**. The distinction exists only in future intent (prod-like may enable execution loop; staging is pre-production verification).

### 6.3 🟡 Observation — candidate source polls external API

`trading.candidate-source.enabled` defaults to `true` (matchIfMissing) and is NOT overridden in monitor-app staging profile. The external API at `uainvest.com.ua` is polled every 60 seconds during staging operation. This is a network call to an external service — acceptable for staging but should be noted.

### 6.4 🟡 Observation — metadata sync runs on startup

`trading.metadata.sync-on-startup: true` (default, not overridden). On staging startup, monitor-app attempts to fetch instrument metadata from all 5 configured exchange APIs. Since `require-credentials-on-startup: false`, it won't block if credentials are absent, but it will make HTTP calls to exchange endpoints.

### 6.5 🟡 Observation — venue access mode mismatch (monitor vs engine)

| Module | `trading-venue-access-mode` | Source |
|--------|---------------------------|--------|
| monitor-app | `production` | `platform-core.yml` default |
| engine-app | `testnet` | `engine-app/application.yml` default |

In staging, monitor uses "production" mode for credential resolution while engine uses "testnet". This is **inconsistent** but doesn't cause problems because execution is disabled. Both are overridable via `TRADING_VENUE_ACCESS_MODE` env var.

### 6.6 ⚠️ Concern — telegram-bot-app staging YAML lacks profile declaration

Unlike monitor-app and engine-app, `telegram-bot-app/src/main/resources/application-staging.yml` does **not** declare `spring.config.activate.on-profile: staging`. It relies entirely on Spring Boot's filename-based convention (`application-{profile}.yml`). This works in practice (Spring Boot loads it when `staging` is active), but it's inconsistent and could cause confusion if someone tries to refactor profile loading.

### 6.7 ⚠️ Concern — no prod-like for telegram-bot-app

Telegram-bot-app lacks a `prod-like` profile. In production deployment (`deploy/docker-compose.yml`), it uses `staging` while monitor and engine use `prod-like`. This works but means telegram-bot-app's production configuration is labeled "staging", which is confusing for operations.

### 6.8 🟢 No `@Profile` annotations

Consistent with the rest of the codebase — staging behavior is entirely property-driven, not bean-registration-driven.

## 7. Safety verdict

**Safe for staging/pre-production use.** ✅

- All trading-critical protections active (auth, credentials, loop off, live orders off)
- Engine explicitly configured with `execution-loop-enabled: false`
- Monitor requires operator authentication and encrypted credential storage
- The only external calls are candidate source polling (60s) and metadata sync (on startup + scheduled) — both are read-only data ingestion

## 8. Comparison with other profiles

| Dimension | local-safe | staging | prod-like | testnet (engine only) |
|-----------|-----------|---------|-----------|----------------------|
| Auth | OFF | **ON** | ON | N/A (engine has no public API) |
| Credential storage | OFF | **ON** | ON | N/A |
| Credential master key req. | OFF | **ON** | ON | N/A |
| Engine loop | OFF | OFF | OFF | **ON (2s tick)** |
| Live orders | OFF | OFF | OFF | **ON (Gate testnet)** |
| Kill switch | N/A | **ON** | ON | **OFF** |
| Metrics publish | OFF | **ON** | ON | OFF |
| Metadata sync on startup | OFF | ON (default) | ON (default) | N/A |
| Candidate source poll | ON (⚠️) | ON (default) | ON (default) | N/A |
| DeepSeek AI | OFF | OFF (default) | OFF (default) | N/A |
| Dev test tool | OFF | OFF (default) | OFF (default) | N/A |
| Auto-approval | OFF | OFF (default) | OFF (default) | N/A |
