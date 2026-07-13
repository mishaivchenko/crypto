---
type: audit
title: Profile Inventory — Existing and Documented Profiles
created: 2026-07-13
tags:
  - audit
  - configuration
  - spring-boot
  - profiles
related:
  - '[[AUDIT-ROUND-3-03]]'
  - '[[application-properties-audit]]'
---

# Profile Inventory — Existing and Documented Profiles

## Summary

Four named profiles exist across the three modules. `prod` does **not** exist as a YAML file — only `prod-like` is used. The `testnet` profile exists **only** in `engine-app` (not in `monitor-app` or `telegram-bot-app`).

| Profile | monitor-app | engine-app | telegram-bot-app | Activate via |
|---------|:-----------:|:----------:|:----------------:|--------------|
| `local-safe` | ✅ | ✅ | ✅ | `SPRING_PROFILES_ACTIVE=local-safe` (default via `build.gradle`) |
| `testnet` | ❌ | ✅ | ❌ | `SPRING_PROFILES_ACTIVE=testnet` |
| `staging` | ✅ | ✅ | ✅ | `SPRING_PROFILES_ACTIVE=staging` |
| `prod-like` | ✅ | ✅ | ❌ | `SPRING_PROFILES_ACTIVE=prod-like` |
| `prod` | ❌ | ❌ | ❌ | does not exist — only `prod-like` |

## Profile Configuration Files

### `local-safe`

| Module | File | Key Overrides |
|--------|------|---------------|
| monitor-app | `application-local-safe.yml` | `auth-enabled: false`, `credentials.storage.enabled: false`, `require-master-key-on-startup: false`, `engine-metrics.enabled: false`, `metadata.require-credentials-on-startup: false`, `metadata.sync-on-startup: false`, `deepseek.enabled: false` |
| engine-app | `application-local-safe.yml` | `execution-loop-enabled: false`, `metrics-publish.enabled: false` |
| telegram-bot-app | `application-local-safe.yml` | Empty token defaults (bot disabled), monitor base-url set to `localhost:8090`, DEBUG logging |

**Purpose:** Local development — no risk of live exchange activity. No auth, no credentials, no external API calls.

### `testnet`

| Module | File | Key Overrides |
|--------|------|---------------|
| engine-app | `application-testnet.yml` | `execution-loop-enabled: true` (interval 2000ms), `live-order-enabled: true`, `kill-switch-enabled: false`, `trading-venue-access-mode: testnet`, `live-enabled-venues: gate`, `max-notional-usd: 25`, `metrics-publish.enabled: false` |
| monitor-app | *(none)* | — |
| telegram-bot-app | *(none)* | — |

**Purpose:** Engine-only execution test against Gate testnet. Enables loop + live orders with kill switch disabled. Monitor stays in its base profile (safe defaults).

### `staging`

| Module | File | Key Overrides |
|--------|------|---------------|
| monitor-app | `application-staging.yml` | `auth-enabled: true`, `credentials.storage.enabled: true`, `require-master-key-on-startup: true`, `engine-metrics.enabled: true`, `metadata.require-credentials-on-startup: false` |
| engine-app | `application-staging.yml` | `execution-loop-enabled: false`, `metrics-publish.enabled: true` |
| telegram-bot-app | `application-staging.yml` | Bot token required (no default), monitor base-url `monitor:8090`, public URL set, INFO logging |

**Purpose:** Staging/test environment (e.g., Mac Mini deployment). Auth and metrics on, credentials stored, but execution loop and live orders remain disabled.

### `prod-like`

| Module | File | Key Overrides |
|--------|------|---------------|
| monitor-app | `application-prod-like.yml` | `auth-enabled: true`, `credentials.storage.enabled: true`, `require-master-key-on-startup: true`, `engine-metrics.enabled: true`, `metadata.require-credentials-on-startup: true` |
| engine-app | `application-prod-like.yml` | `execution-loop-enabled: false`, `metrics-publish.enabled: true` |
| telegram-bot-app | *(none)* | — |

**Purpose:** Production-like configuration. Everything enabled except execution loop and live orders (require explicit ENV override). **telegram-bot-app has no prod-like profile** — it relies on base `application.yml` defaults when running in production.

### `prod` (does not exist)

`prod` is listed in the task title's parenthetical but does **not** exist in the codebase. No `application-prod.yml` in any module. The intended production profile is `prod-like`. This is consistent with how `spring-boot` bootRun tasks, Docker Compose, and `.env.example` all reference `prod-like` as the production profile.

## Profile Activation Summary

| Mechanism | monitor-app | engine-app | telegram-bot-app |
|-----------|:-----------:|:----------:|:----------------:|
| `./gradlew bootRun*` (default) | `local-safe` | `local-safe` | `local-safe` |
| Docker Compose (`deploy/docker-compose.yml`) | `prod-like` | `prod-like` | `staging` |
| Observability Compose (`deploy/observability/docker-compose.yml`) | `staging` | `staging` | *(not included)* |
| `.env.example` | `prod-like` | `prod-like` | *(not separately specified)* |
| `Deployable Dockerfiles` | *(not set — ENV must be provided at runtime)* | *(not set)* | *(not set)* |

## Module-Specific Profile Availability

### monitor-app
- Has profiles: `local-safe`, `staging`, `prod-like`
- Missing profiles: `testnet`, `prod`
- **Note:** No `testnet` profile for monitor-app. Running engine in testnet mode while monitor stays in its base config is the intended design — monitor controls the operator UI and credential storage, while engine handles execution.

### engine-app
- Has profiles: `local-safe`, `testnet`, `staging`, `prod-like`
- Missing profiles: `prod`
- **Note:** The only module with a `testnet` profile. This makes sense since engine is the execution runtime.

### telegram-bot-app
- Has profiles: `local-safe`, `staging`
- Missing profiles: `testnet`, `prod-like`, `prod`
- **Note:** No `prod-like` profile. When deploying the telegram bot to production alongside `prod-like` monitor/engine, it runs with base `application.yml` defaults. The bot token must be provided via ENV regardless of profile (non-empty token enables the bean via `@ConditionalOnProperty`).

## Profile Activation

### Default activation (build.gradle)
```groovy
localBootProfile = 'local-safe'
// Each bootRun task:
environment 'SPRING_PROFILES_ACTIVE', System.getenv('SPRING_PROFILES_ACTIVE') ?: localBootProfile
```

### Platform-core.yml defaults
The shared `platform-core.yml` sets property values via `spring.config.import`. It is not a profile-specific file — it applies to all profiles unless overridden.

### Docker Compose activation
| Compose file | monitor-app | engine-app | telegram-bot-app |
|-------------|:-----------:|:----------:|:----------------:|
| `deploy/docker-compose.yml` | `prod-like` | `prod-like` | `staging` |
| `deploy/observability/docker-compose.yml` | `staging` | `staging` | *(not included)* |

### No `spring.profiles.default` or `spring.profiles.include`
No default profile is configured in any YAML. No profile includes another. Profiles are mutually exclusive — each module activates exactly one profile at a time.

## Test Profiles
- Zero `@ActiveProfiles` annotations exist in any test class
- `EngineApplicationTest` hardcodes `"--spring.profiles.active=local-safe"` as a program argument

## Summary of Gaps

1. **`prod` profile does not exist** — only `prod-like`. This is intentional and consistent, but the parenthetical in the task list may cause confusion.
2. **telegram-bot-app missing `prod-like` profile** — the bot has no production-specific config file. It runs base defaults in production.
3. **No `testnet` profile for monitor-app** — intentional (monitor uses property-based overrides, not profile switching).
4. **No `spring.profiles.default` fallback** — if no profile is active, Spring Boot uses its own defaults. All property defaults in `platform-core.yml` and module `application.yml` files are safe, so this is acceptable.
