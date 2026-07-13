---
type: audit
title: Docker Compose Profile Usage Audit — Which Profile Each Compose Uses
created: 2026-07-13
tags:
  - audit
  - docker
  - docker-compose
  - profiles
  - spring-boot
related:
  - '[[AUDIT-ROUND-3-03]]'
  - '[[profile-inventory]]'
  - '[[no-profile-behavior-verification]]'
  - '[[profile-activation-audit]]'
---

# Docker Compose Profile Usage Audit

## Summary

Three Docker Compose files exist. Only **2 of 3** set `SPRING_PROFILES_ACTIVE` explicitly. The **root `docker-compose.yml`** runs with **zero active profiles** — only base `application.yml` + `platform-core.yml` defaults apply.

## File-by-File Analysis

### 1. Root `docker-compose.yml` (local dev + observability stack)

| Service | Profile Set | Effective Profile | Source |
|---------|:-----------:|:-----------------:|--------|
| monitor | ❌ None | `default` (none) | No `SPRING_PROFILES_ACTIVE` in env |
| engine | ❌ None | `default` (none) | No `SPRING_PROFILES_ACTIVE` in env |
| prometheus | N/A | N/A | Not a Spring Boot app |
| grafana | N/A | N/A | Not a Spring Boot app |
| telegram-bot | ❌ Not included | N/A | Service absent from this file |

**Key characteristics:**
- **Builds images locally** (`funding-monitor:local`, `funding-engine:local`) from root `Dockerfile` (not pre-built)
- **Uses `.env` file** for variable substitution
- **Explicit env overrides** compensate for missing profile:
  - `MONITOR_ENGINE_METRICS_ENABLED: "true"` (replaces what `prod-like`/`staging` would set)
  - `ENGINE_METRICS_PUBLISH_ENABLED: "true"` (replaces what `prod-like`/`staging` would set)
  - `TRADING_VENUE_ACCESS_MODE: testnet` (overrides inconsistent venue defaults)
  - `ENGINE_EXECUTION_LOOP_ENABLED: false` (safety default — same as `prod-like`)
  - `ENGINE_LIVE_ORDER_ENABLED: false` (safety default — same as `prod-like`)
  - `ENGINE_KILL_SWITCH_ENABLED: true` (safety default — same as `prod-like`)
- **Notable behavior without profile:**
  - `trading.candidate-source.enabled` defaults to `true` (`matchIfMissing`) — external API polled every 60s
  - No auth (`SECURITY_OPERATOR_AUTH_ENABLED` not set, defaults to `false`)
  - No credential storage (`CREDENTIALS_STORAGE_ENABLED` not set, defaults to `false`)
  - Metadata sync runs on startup (`TRADING_METADATA_SYNC_ON_STARTUP: true`)
  - telegram-bot-app not included (no `TOKEN`, no `SECURITY_OPERATOR_BOOTSTRAP_USERS`)
  - 13 engine-app beans load unconditionally, loop ticks at 250ms but exits via runtime guard

**Safety verdict:** Safe for local dev — all trading-critical features are disabled. Risk: external API polling without credentials/auth configured.

### 2. `deploy/docker-compose.yml` (production deployment)

| Service | Profile Set | Effective Profile | Source |
|---------|:-----------:|:-----------------:|--------|
| monitor | ✅ Yes | `prod-like` | `SPRING_PROFILES_ACTIVE: prod-like` (line 10) |
| engine | ✅ Yes | `prod-like` | `SPRING_PROFILES_ACTIVE: prod-like` (line 42) |
| telegram-bot | ✅ Yes | `staging` | `SPRING_PROFILES_ACTIVE: staging` (line 67) |

**Key characteristics:**
- **Uses pre-built images** (`funding-monitor:2.0.0`, `funding-engine:2.0.0`, `funding-telegram-bot:2.0.0`)
- **Does NOT build** — images must exist before `docker compose up`
- **Uses `.env` file** for variable substitution
- **telegram-bot uses `staging` instead of `prod-like`** because `telegram-bot-app` has NO `prod-like` profile — `application-prod-like.yml` does not exist in `telegram-bot-app/src/main/resources/`
- **engine-app `prod-like` = engine-app `staging`** (byte-for-byte identical) — the behavioral difference between `staging` and `prod-like` production deployment comes from `ENGINE_*` environment variable overrides, not profile YAML settings
- **monitor-app `prod-like` vs `staging`**: only 1 difference — `trading.metadata.require-credentials-on-startup: true` (prod-like) vs `false` (staging)
- **Auth-protected**: `SECURITY_OPERATOR_AUTH_ENABLED: true` (from `prod-like` profile)
- **Credentials enabled**: `CREDENTIALS_STORAGE_ENABLED: true` (from `prod-like` profile)
- **Loop OFF, live orders OFF, kill switch ON** (from `prod-like` profile, with env var overrides possible via `.env`)
- **telegram-bot**: `staging` profile enables auth token, monitor communication, and notification scheduling

**Safety verdict:** Safe for production deployment — loop and live orders are OFF by default, require explicit env var override to enable.

### 3. `deploy/observability/docker-compose.yml` (monitoring/observability)

| Service | Profile Set | Effective Profile | Source |
|---------|:-----------:|:-----------------:|--------|
| monitor | ✅ Yes | `staging` | `SPRING_PROFILES_ACTIVE: staging` (line 15) |
| engine | ✅ Yes | `staging` | `SPRING_PROFILES_ACTIVE: staging` (line 47) |
| telegram-bot | ❌ Not included | N/A | Service absent from this file |

**Key characteristics:**
- **Builds images locally** from the project root context (`context: ../..`)
- **Uses `.env.observability`** as `env_file` (separate env file from production)
- **Auth DISABLED**: `SECURITY_OPERATOR_AUTH_ENABLED: "false"` (overrides `staging` profile's `auth-enabled: true`)
- **Credentials DISABLED**: `CREDENTIALS_STORAGE_ENABLED: "false"` (overrides `staging` profile's `credentials.storage.enabled: true`)
- **Engine loop ENABLED**: `ENGINE_EXECUTION_LOOP_ENABLED: "true"` (overrides `staging` profile's `execution-loop-enabled: false`)
  - This is significant — the observability compose intentionally enables the execution loop for monitoring purposes
- **Metrics publishing ON**: `ENGINE_METRICS_PUBLISH_ENABLED: "true"`, `INTERVAL_MS: 5000`
- **Candidate source ON**: `TRADING_CANDIDATE_SOURCE_ENABLED: "true"`
- **Public access**: monitor on port 18090, engine on 18091, Prometheus on 19090, Grafana on 13000 (offset from default ports)
- **telegram-bot NOT included** — no operator notification capability in this compose
- **Prometheus and Grafana** provisioned with SLI/SLO dashboard config

**Safety verdict:** Intentionally observability-focused. Auth and credentials are disabled for convenience, but engine loop is enabled. This compose is intended for monitoring/metrics development and testing, **not** for production use. The auth/credential disabling plus loop-enabling combination makes this unsafe to expose to a network.

## Profile by Compose File — Reference Table

| Compose File | Monitor | Engine | Telegram-Bot | Prometheus | Grafana |
|-------------|:-------:|:------:|:------------:|:--------:|:------:|
| `docker-compose.yml` (root) | **none** | **none** | ❌ absent | ✅ | ✅ |
| `deploy/docker-compose.yml` | **prod-like** | **prod-like** | **staging** | ❌ absent | ❌ absent |
| `deploy/observability/docker-compose.yml` | **staging** | **staging** | ❌ absent | ✅ | ✅ |

## Key Findings

1. **Root `docker-compose.yml` has NO `SPRING_PROFILES_ACTIVE`** — all 3 Spring services run with only base defaults. This was established in the prior audit (line 445 of the playbook) but now confirmed with env-by-env review of the compose file.

2. **telegram-bot-app uses `staging` in production deploy** — because no `prod-like` profile exists for telegram-bot-app. The `staging` profile is the closest available match. Telegram-bot-app's behavior is driven by `@ConditionalOnProperty` on `telegram.bot.token` being non-empty, not by profile-specific overrides.

3. **Observability compose overrides `staging` profile safety**: auth OFF, credentials OFF, but engine loop ON — a purpose-built testing configuration.

4. **No Docker Compose file sets `testnet` profile** — the engine-app `testnet` profile (enables loop + live orders for Gate testnet) is designed for manual activation only, not automated deployment.

5. **All three compose files are consistent** in that none enable the engine execution loop by default (the observability compose enables it intentionally for monitoring). The root compose sets it false via env var; the deploy compose inherits `false` from `prod-like` profile.

## Recommendations

1. **Add `SPRING_PROFILES_ACTIVE: local-safe` to root `docker-compose.yml`** — currently it sets no profile, which means candidate source polling is active and auth defaults to `false`. While the env var overrides keep trading safe, explicitly setting a profile would make the intent clearer and disable the candidate source polling.

2. **Create a `staging` or `prod-like` profile for telegram-bot-app** — reduce confusion when deploying. Currently the deploy compose uses `staging` for telegram-bot while monitor/engine use `prod-like`.

3. **Consider adding a compose override or separate compose file for the `testnet` execution profile** — the only path to execute testnet trades via Docker is manual `SPRING_PROFILES_ACTIVE=testnet` environment setting. An `override.yml` or separate `compose.testnet.yml` would make this reproducible.
