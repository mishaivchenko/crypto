---
type: analysis
title: Production Profile Recommendation — Which Profile Production Should Use
created: 2026-07-13
tags:
  - production
  - spring-boot
  - profiles
  - deployment
  - audit
related:
  - '[[profile-inventory]]'
  - '[[prod-like-profile-verification]]'
  - '[[staging-profile-verification]]'
  - '[[profile-differences-comparison]]'
  - '[[profile-activation-audit]]'
  - '[[ci-staging-profile-audit]]'
  - '[[AUDIT-ROUND-3-03]]'
---

# Production Profile Recommendation

## Question

Which Spring Boot profile should each module use in a production deployment?

## Answer

| Module | Recommended Profile | Source of Truth | Safety |
|--------|-------------------|-----------------|--------|
| **monitor-app** | `prod-like` | `monitor-app/src/main/resources/application-prod-like.yml` | ✅ Auth ON, credentials ON, master key required |
| **engine-app** | `prod-like` | `engine-app/src/main/resources/application-prod-like.yml` | ✅ Loop OFF (must be explicitly enabled), metrics ON |
| **telegram-bot-app** | `prod-like` **(needs creation)** | `telegram-bot-app/src/main/resources/application-prod-like.yml` — **does not exist yet** | ✅ Currently uses `staging` as closest available |

**No `prod` profile exists and none should be created** — `prod-like` is the intentional naming choice. The "like" suffix signals "production-ready but execution loop is always off by default." All documentation (CLAUDE.md, README.md, `docs/03-runtime-config.md`) consistently uses `prod-like` for production contexts. Creating a separate `prod` profile would duplicate `prod-like` with zero behavioral difference.

## Current Production Deployment

### `deploy/docker-compose.yml` (the deploy-compose)

| Service | Profile | How Set |
|---------|---------|---------|
| monitor-app | `prod-like` | `environment.SPRING_PROFILES_ACTIVE: prod-like` (line 10) |
| engine-app | `prod-like` | `environment.SPRING_PROFILES_ACTIVE: prod-like` (line 42) |
| telegram-bot-app | `staging` | `environment.SPRING_PROFILES_ACTIVE: staging` (line 67) — falls back because no `prod-like` profile exists |

The `deploy/.env` also redundantly sets `SPRING_PROFILES_ACTIVE=prod-like` (loaded via `env_file: .env`) — consistent with compose-level settings for monitor/engine, but does not affect telegram-bot (its compose-level `staging` wins).

### CI/CD `deploy-prod` job

The CI workflow at `.github/workflows/ci-cd.yml` line 269-278 has a stub `deploy-prod` job:

```yaml
deploy-prod:
    name: Deploy → Production (VPS)
    needs: deploy-staging
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    environment: production
    steps:
      - name: Not yet configured
        run: echo "VPS not provisioned yet — add VPS_HOST, VPS_USER, VPS_SSH_KEY secrets to enable"
```

Production deployment is **not yet implemented**. When built, it should follow the same profile pattern as `deploy/docker-compose.yml`.

## Per-Module Analysis

### monitor-app: `prod-like` ✅

**Why `prod-like` over `staging`:**

| Property | `staging` | `prod-like` | Impact |
|----------|-----------|-------------|--------|
| `security.operators.auth-enabled` | `true` | `true` | Same |
| `credentials.storage.enabled` | `true` | `true` | Same |
| `credentials.require-master-key-on-startup` | `true` | `true` | Same |
| `monitor.engine-metrics.enabled` | `true` | `true` | Same |
| `trading.metadata.require-credentials-on-startup` | `false` | **`true`** | **Only difference** — production should require credentials on startup |

The single difference (`require-credentials-on-startup: false` in staging vs `true` in prod-like) is the right choice for production. A production monitor should fail closed if credentials are missing at startup, not silently start without them.

**What `prod-like` does NOT override (inherited from base defaults):**

| Setting | Default | Risk | Recommendation |
|---------|---------|------|---------------|
| `trading.candidate-source.enabled` | `true` (matchIfMissing) | External API (`uainvest.com.ua`) polled every 60s | Consider disabling in production or accepting as intentional |
| `server.shutdown` | `immediate` | In-flight trades lost on SIGTERM | Set `server.shutdown: graceful` in production |
| `spring.task.scheduling.pool.size` | `1` | 6 scheduled methods share one thread | Set to 2-4 in production |
| Venue modes | Mixed defaults (bybit/gate=testnet, others=production) | Confusing but harmless without execution loop | Consider setting production defaults explicitly |

**These inherited defaults apply to ALL profiles, not just `prod-like`.** They are not production-specific gaps in the profile itself — they are codebase-wide concerns flagged in previous audit findings.

### engine-app: `prod-like` ✅

**Why `prod-like` over `staging`:**

engine-app `staging` and `prod-like` profiles are **byte-for-byte identical** (confirmed in [profile-differences-comparison]):

```
# application-staging.yml
engine:
    execution-loop-enabled: false
    metrics-publish:
        enabled: true

# application-prod-like.yml
engine:
    execution-loop-enabled: false
    metrics-publish:
        enabled: true
```

There is **zero behavioral difference** between the two profiles for engine-app. Either produces the same runtime behavior.

**Recommendation:** Use `prod-like` for consistency with monitor-app and to signal intent. The profile name communicates "this is a production deployment" to operators even though the behavioral result is identical.

**Live trading activation for production:**
When production trading is ready, the deploy must explicitly set:
- `ENGINE_EXECUTION_LOOP_ENABLED=true`
- `ENGINE_LIVE_ORDER_ENABLED=true`
- `ENGINE_KILL_SWITCH_ENABLED=true` (keep ON — kill switch must remain functional)
- `ENGINE_LIVE_ENABLED_VENUES=bybit,gate,bitget,okx,kucoin`
- `ENGINE_MAX_NOTIONAL_USD=<appropriate production limit>`
- `ENGINE_TRADING_VENUE_ACCESS_MODE=production`

The `prod-like` profile is designed so that **these must be explicit overrides** — it's impossible to accidentally have the engine execute live trades just by activating the profile.

### telegram-bot-app: `prod-like` ❌ **does not exist**

**Current situation:**
- telegram-bot-app has profiles: `local-safe`, `staging`
- It has **NO** `prod-like` profile
- In production (`deploy/docker-compose.yml`), it uses `staging` as the closest available
- Its profile YAMLs (`application-local-safe.yml`, `application-staging.yml`) **lack `spring.config.activate.on-profile`** declarations — they load unconditionally (previously flagged finding)

**Why this matters:**
1. **Inconsistent naming** — "staging" profile running alongside "prod-like" monitor/engine creates confusion
2. **Missing production guard** — no profile ensures production-specific settings (logging level, URL links, monitor operator token behavior)
3. **No YAML profile guard** — even the existing `staging` YAML loads unconditionally, which can cause issues when `TELEGRAM_BOT_TOKEN` env var is unset (beans created with invalid placeholder string — documented in [no-profile-behavior-verification])

**Recommendation:** Create `application-prod-like.yml` for telegram-bot-app:

```yaml
spring:
    config:
        activate:
            on-profile: prod-like

logging:
    level:
        root: INFO
        com.crypto.funding: INFO

telegram:
    bot:
        token: ${TELEGRAM_BOT_TOKEN}  # no default — must be set for production

monitor:
    operator-token: ${MONITOR_OPERATOR_TOKEN}  # no default — must be set for production
```

Additionally, add `spring.config.activate.on-profile` declarations to the existing `application-local-safe.yml` and `application-staging.yml` files (fixing the previously identified bug).

## CI `deploy-prod` Implementation Plan

When the CI production deployment is implemented, it should:

1. **Use the same profile structure** as `deploy/docker-compose.yml`:
   - monitor-app: `SPRING_PROFILES_ACTIVE=prod-like`
   - engine-app: `SPRING_PROFILES_ACTIVE=prod-like`
   - telegram-bot-app: `SPRING_PROFILES_ACTIVE=prod-like` (once the profile exists) or `staging` (interim)

2. **Set explicit env overrides** for production trading (when ready):
   - `ENGINE_EXECUTION_LOOP_ENABLED=true`
   - `ENGINE_LIVE_ORDER_ENABLED=true`
   - `ENGINE_KILL_SWITCH_ENABLED=true`
   - Production venue credentials and URLs
   - `MONITOR_PUBLIC_URL` for telegram-bot links

3. **Add production-specific environment variables**:
   - `SECURITY_OPERATOR_BOOTSTRAP_USERS` with real operator tokens
   - `INTERNAL_ENGINE_TOKEN` with a strong random value
   - `CREDENTIALS_MASTER_KEY_BASE64` with a properly generated key
   - Real exchange API keys via `ENGINE_CREDENTIALS_*` env vars

4. **Enhance smoke test** to validate profile-specific behavior:
   - Verify `/actuator/health` returns UP
   - Verify auth is enforced on public endpoints
   - Verify engine loop status is as expected

## Key Findings

1. **`prod-like` is the correct production profile** for monitor-app and engine-app — no change needed in current deploy Compose configuration.

2. **telegram-bot-app needs a `prod-like` profile created** — currently uses `staging` as a fallback. Priority: medium (functional gap, not a safety issue).

3. **`prod` profile should NOT be created** — `prod-like` is the intentional naming choice that signals "execution loop always off by default." Adding `prod` would create confusion and require maintaining two near-identical profiles.

4. **Base-application defaults apply to production too** — the inherited risks (`candidate-source.enabled: true`, `server.shutdown: immediate`, `spring.task.scheduling.pool.size: 1`) are not profile-specific but affect production behavior.

5. **No production env-vs-profile conflicts** — the `deploy/docker-compose.yml` `environment` block consistently sets `prod-like` for monitor/engine, `staging` for telegram-bot. The `deploy/.env` file's `SPRING_PROFILES_ACTIVE=prod-like` is consistent/redundant for monitor/engine.

6. **CI `deploy-prod` is stubbed** — the VPS-based production deployment is not yet implemented. When built, it should follow the same profile pattern as the deploy Compose.

7. **Production trading activation is a separate concern** — the `prod-like` profile does NOT enable execution. When trading is needed, operators must explicitly set env vars (`ENGINE_EXECUTION_LOOP_ENABLED=true`, etc.). This double-lock pattern is correct and should be preserved.
