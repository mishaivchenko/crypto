---
type: analysis
title: CI Staging Profile Verification — Which Profiles "Staging" Actually Uses
created: 2026-07-13
tags:
  - ci-cd
  - github-actions
  - spring-boot
  - profiles
  - staging
  - deploy
related:
  - '[[profile-differences-comparison]]'
  - '[[profile-inventory]]'
  - '[[profile-activation-audit]]'
  - '[[AUDIT-ROUND-3-03]]'
---

# CI Staging Profile Verification

## Question

When the CI-CD pipeline deploys to "staging" (the self-hosted Mac mini runner), which Spring Boot profiles are actually active for each module?

## Answer

| Service | Profile Used | Source |
|---------|-------------|--------|
| **monitor-app** | `prod-like` | `deploy/docker-compose.yml` line 10 — `SPRING_PROFILES_ACTIVE: prod-like` |
| **engine-app** | `prod-like` | `deploy/docker-compose.yml` line 42 — `SPRING_PROFILES_ACTIVE: prod-like` |
| **telegram-bot-app** | `staging` | `deploy/docker-compose.yml` line 67 — `SPRING_PROFILES_ACTIVE: staging` |

**CI staging does NOT use the `staging` profile for monitor-app or engine-app.** It uses `prod-like` instead. Only telegram-bot-app uses the `staging` profile.

## Deployment Flow

The CI-CD pipeline's `deploy-staging` job (`.github/workflows/ci-cd.yml` line 191-268):

1. **Triggers:** `push` to `main` branch after `build` job succeeds
2. **Runner:** Self-hosted `[mac-mini, staging]` on arm64
3. **Environment:** GitHub `environment: staging` (secret scoping — not a Spring profile)
4. **Build:** Compiles JARs natively via `./gradlew :*-app:bootJar`
5. **Docker:** Builds 3 images locally (`funding-monitor:staging`, `funding-engine:staging`, `funding-telegram-bot:staging`)
6. **Launch:** Runs `docker compose up` from `deploy/` directory with overridden image tags:

```yaml
IMAGE_MONITOR=funding-monitor:staging \
IMAGE_ENGINE=funding-engine:staging \
IMAGE_TELEGRAM_BOT=funding-telegram-bot:staging \
MONITOR_PUBLIC_URL=https://crypto-monitor.org \
docker compose up -d --remove-orphans monitor engine telegram-bot
```

The `deploy/docker-compose.yml` is the source of truth for per-service `SPRING_PROFILES_ACTIVE`.

## Conflicting vs Redundant Profile Sources

Three potential sources for `SPRING_PROFILES_ACTIVE` exist in the deploy:

| Source | Value | Resolution |
|--------|-------|------------|
| `deploy/docker-compose.yml` — `environment.SPRING_PROFILES_ACTIVE` | `prod-like` (monitor/engine), `staging` (telegram-bot) | Docker Compose `environment` takes precedence over `env_file` |
| `deploy/.env` — `SPRING_PROFILES_ACTIVE=prod-like` | `prod-like` | Loaded via `env_file: .env` — consistent but redundant for monitor/engine; does NOT affect telegram-bot (its compose-level `environment.SPRING_PROFILES_ACTIVE: staging` wins) |
| CI job environment variables | Not set | No `SPRING_PROFILES_ACTIVE` in CI job's `env:` block |

**No conflict:** Both compose-level `environment` and `env_file` agree on `prod-like` for monitor/engine. telegram-bot's compose-level `staging` takes precedence over the `.env` file.

## Effective Configuration Per Service

### monitor-app (prod-like profile)

From `monitor-app/src/main/resources/application-prod-like.yml`:
- `security.operators.auth-enabled: true`
- `credentials.storage.enabled: true`
- `credentials.require-master-key-on-startup: true`
- `monitor.engine-metrics.enabled: true`
- `trading.metadata.require-credentials-on-startup: true`

CI additionally supplies (from docker-compose environment):
- `MONITOR_ENGINE_METRICS_ENABLED: "true"` (redundant — already true in prod-like)
- `MONITOR_ENGINE_CONTROL_INTERNAL_TOKEN` from `deploy/.env`
- Exchange API keys from `deploy/.env`
- Logging, port, DB path from compose

### engine-app (prod-like profile)

From `engine-app/src/main/resources/application-prod-like.yml`:
- `engine.execution-loop-enabled: false` (must be explicitly enabled via env var)
- `engine.metrics-publish.enabled: true`

CI additionally supplies:
- `ENGINE_EXECUTION_LOOP_ENABLED: false` (from `deploy/.env` — safe double-lock)
- `ENGINE_LIVE_ORDER_ENABLED: false`
- `ENGINE_KILL_SWITCH_ENABLED: true`
- `ENGINE_METRICS_PUBLISH_ENABLED: true` (redundant — already true in prod-like)
- Engine credentials, internal token from `deploy/.env`

### telegram-bot-app (staging profile)

From `telegram-bot-app/src/main/resources/application-staging.yml`:
- `telegram.bot.token: ${TELEGRAM_BOT_TOKEN}` (no default — must be provided via env var)
- `logging.level: DEBUG`

CI supplies:
- `TELEGRAM_BOT_TOKEN` and other telegram config from `deploy/.env` env vars

## Why prod-like Instead of staging?

The audit's prior findings explain why this is reasonable:

1. **engine-app `staging` and `prod-like` are byte-for-byte identical** ([profile-differences-comparison]) — the only behavioral distinction comes from environment variables, not YAML overrides. Either profile produces the same runtime behavior for the engine.

2. **monitor-app `staging` vs `prod-like` differ by only one property:**
   - `staging`: `trading.metadata.require-credentials-on-startup: false`
   - `prod-like`: `trading.metadata.require-credentials-on-startup: true`
   - Using `prod-like` in staging makes the staging environment more closely resemble production behavior — arguably the correct choice for a pre-prod environment.

3. **telegram-bot-app has no `prod-like` profile** — `staging` is the closest available profile. The `deploy/docker-compose.yml` uses `staging` for telegram-bot while `prod-like` for the other two modules.

## Key Findings

### 1. Naming Mismatch (Cosmetic)
The CI job is named "Deploy → Staging" but uses the `prod-like` Spring profile. This is misleading for anyone reading CI logs or debugging profile-specific behavior. However, the decision is defensible given the findings above.

### 2. `deploy/.env` Redundancy
The `deploy/.env` file's `SPRING_PROFILES_ACTIVE=prod-like` is redundant — the docker-compose.yml already sets it per-service. This is not harmful but creates ambiguity about the source of truth.

### 3. Implicit Profile Inheritance Risk
All settings in monitor/engine `base` `application.yml` and `platform-core.yml` apply because they are loaded regardless of profile. This includes:
- `trading.candidate-source.enabled: true` (matchIfMissing) — `uainvest.com.ua` polled every 60s
- `server.shutdown: immediate` — no graceful shutdown
- `spring.task.scheduling.pool.size: 1` — single thread for all scheduled methods

The `prod-like` profile only overrides ~5 properties — the rest come from these base files.

### 4. telegram-bot-app Profile Integration Gap
telegram-bot-app has no `prod-like` profile and its staging YAML lacks `spring.config.activate.on-profile` (previously flagged in [no-profile-behavior-verification]). In the staging deploy, this works correctly because `SPRING_PROFILES_ACTIVE=staging` is explicitly set, so filename-based loading matches. But this remains an inconsistency risk.

### 5. CI Doesn't Use `.env` Secrets for Auth
The `deploy/.env` file contains placeholder values (`change-me`, `change-me-too`, etc.) — real secrets must be provided externally. The CI runner relies on GitHub environment secrets (`environment: staging`) for `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `DEEPSEEK_API_KEY`, etc. But the Spring application authentication (`SECURITY_OPERATOR_BOOTSTRAP_USERS`, `INTERNAL_ENGINE_TOKEN`) must come from the `deploy/.env` file on the Mac mini — which is out of CI's scope.

## Smoke Test Verification

The CI staging deploy includes a smoke test (line 251-260):
```yaml
for svc in 8090 8091; do
  for i in $(seq 1 24); do
    curl -sf http://localhost:$svc/actuator/health && break
    sleep 5
  done
done
```

This tests `/actuator/health` only — no profile-specific validation (e.g., checking that auth is enabled, or that metrics endpoints respond). It confirms the apps started but not that the correct profile was applied.

## Summary

| Aspect | Finding |
|--------|---------|
| monitor-app profile | `prod-like` (not `staging`) |
| engine-app profile | `prod-like` (not `staging`) |
| telegram-bot-app profile | `staging` (no `prod-like` available) |
| Naming consistency | "Staging" deploy uses "prod-like" — misleading but functionally correct |
| Safety | ✅ All execution guards active (loop OFF, live orders OFF, kill switch ON) |
| Auth | ✅ Profile enables auth; real tokens from deploy/.env |
| Danger | ⚠️ `candidate-source.enabled: true` inherited from base — 60s external API poll |
