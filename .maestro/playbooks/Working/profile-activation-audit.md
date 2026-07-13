---
type: analysis
title: Profile Activation Audit — How Active Profile Is Selected
created: 2026-07-13
tags:
  - spring-boot
  - configuration
  - profiles
related:
  - '[[profile-inventory]]'
  - '[[profile-differences-comparison]]'
  - '[[local-safe-profile-verification]]'
  - '[[testnet-profile-verification]]'
  - '[[staging-profile-verification]]'
  - '[[prod-like-profile-verification]]'
---

# Profile Activation Audit

## Summary

Active Spring profiles are selected through **three mechanisms**: environment variable (`SPRING_PROFILES_ACTIVE`), JVM arguments (`--spring.profiles.active=<profile>`), and `build.gradle` bootRun defaults. There is **no single source of truth** — the mechanism varies by deployment method, and no `application.properties` or bootstrapping code sets a default profile.

---

## 1. Environment Variable (`SPRING_PROFILES_ACTIVE`)

### 1a. `build.gradle` — bootRun tasks (local development)

All 3 modules set `SPRING_PROFILES_ACTIVE` in their `bootRun` task via `build.gradle`:

```groovy
ext {
    localBootProfile = 'local-safe'  // build.gradle:13
}

project( ':monitor-app' ) {
    // ...
    environment 'SPRING_PROFILES_ACTIVE', System.getenv( 'SPRING_PROFILES_ACTIVE' ) ?: localBootProfile
}

project( ':engine-app' ) {
    // ... (same pattern)
}

project( ':telegram-bot-app' ) {
    // ... (same pattern)
}
```

- **Resolution order**: OS environment variable → `local-safe` default
- **Scope**: Only active when launched via `./gradlew bootRun*` or `./gradlew :module:bootRun`
- **Gradle tasks**: `bootRunMonitor`, `bootRunEngine`, `bootRunTelegramBot` are registered at `build.gradle:75-121`

### 1b. `deploy/docker-compose.yml` — Production deployment

Hardcoded per-service profile (not overridable via `.env`):

| Service | Profile |
|---------|---------|
| `monitor` | `prod-like` |
| `engine` | `prod-like` |
| `telegram-bot` | `staging` |

The deploy Compose file at `deploy/docker-compose.yml` explicitly sets `SPRING_PROFILES_ACTIVE: prod-like` (for monitor/engine) and `SPRING_PROFILES_ACTIVE: staging` (for telegram-bot) in each container's `environment:` block.

### 1c. `docker-compose.yml` — Local Docker (with observability)

**Does NOT set `SPRING_PROFILES_ACTIVE`** for any service. Containers run with Spring Boot's base defaults only — no profile-specific YAML is activated. This means:
- No `local-safe` profile applied
- No auth, no credentials, no execution loop (all OFF via base defaults)
- But: candidate source polling ON by default (`matchIfMissing=true`)

### 1d. CI/CD (`ci-cd.yml`)

| Job | Profile Selection |
|-----|-------------------|
| Build & Test | None — `./gradlew clean build --no-daemon` (not a `bootRun`) |
| Engine TDD Gate | None — `./gradlew engineTddGate --no-daemon` |
| Docker build & push | None — `./gradlew :module:bootJar --no-daemon` |
| Deploy → Staging | Uses `deploy/docker-compose.yml` → `prod-like`/`staging` |
| Deploy → Production | Not yet configured |

- CI build/test jobs compile and run tests, they do not `bootRun` the application
- `bootJar` packages the JAR but never selects a profile at build time
- The deploy steps inherit profile from `deploy/docker-compose.yml`

---

## 2. JVM Arguments

Profiles can be passed via `--spring.profiles.active=<profile>` as a Java argument:

```
java -jar monitor-app-2.0.0-monitor.jar --spring.profiles.active=staging
```

This is confirmed in test code at `EngineApplicationTest.java:17`:
```java
String[] args = {"--spring.profiles.active=local-safe"};
```

The root `Dockerfile` does NOT include any JVM profile args in its `ENTRYPOINT`:
```dockerfile
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

---

## 3. Programmatic Profile Selection

**None of the three Application main classes set profiles programmatically.** All three use:

```java
SpringApplication.run(MonitorApplication.class, args);     // monitor-app
SpringApplication.run(EngineApplication.class, args);      // engine-app
SpringApplication.run(TelegramBotApplication.class, args); // telegram-bot-app
```

No calls to:
- `setAdditionalProfiles()`
- `setActiveProfiles()`
- `SpringApplicationBuilder.profiles()`
- `@Profile` annotations exist (zero across all modules)

---

## 4. Profile-Specific YAML Activation Mechanisms

### Modules using `spring.config.activate.on-profile` (explicit declaration)

| Module | Profile YAMLs with `on-profile` declaration |
|--------|---------------------------------------------|
| monitor-app | `local-safe`, `staging`, `prod-like` (3 total) |
| engine-app | `local-safe`, `testnet`, `staging`, `prod-like` (4 total) |
| telegram-bot-app | **NONE** — 0 YAMLs use `on-profile` |

### Modules using filename convention only (no `on-profile`)

| Module | Profile YAMLs | Mechanism |
|--------|---------------|-----------|
| telegram-bot-app | `application-local-safe.yml`, `application-staging.yml` | Filename convention only |

**Inconsistency**: telegram-bot-app profile YAMLs lack `spring.config.activate.on-profile`, relying solely on Spring Boot's filename-based profile activation (`application-{profile}.yml` → profile `{profile}`). This works but is inconsistent with the other 2 modules.

---

## 5. What Happens When No Profile Is Set

Without `SPRING_PROFILES_ACTIVE` and without `--spring.profiles.active`:

1. **No profile-specific YAML files are loaded**
2. Only base files apply: `application.yml` + `platform-core.yml` (monitor-app)
3. All properties use their `${ENV_VAR:default}` expressions
4. Effective configuration:

| Property | Default | Value |
|----------|---------|-------|
| Auth enabled | `SECURITY_OPERATOR_AUTH_ENABLED:false` | OFF |
| Credential storage enabled | `CREDENTIALS_STORAGE_ENABLED:false` | OFF |
| Engine loop enabled | `ENGINE_EXECUTION_LOOP_ENABLED:false` | OFF |
| Live orders enabled | `ENGINE_LIVE_ORDER_ENABLED:false` | OFF |
| Kill switch enabled | `ENGINE_KILL_SWITCH_ENABLED:true` | ON |
| Candidate source enabled | `TRADING_CANDIDATE_SOURCE_ENABLED:true` | ON (matchIfMissing) |
| Metrics publish enabled | `ENGINE_METRICS_PUBLISH_ENABLED:false` | OFF |
| Trading venue access mode | `TRADING_VENUE_ACCESS_MODE:production` (monitor) / `testnet` (engine) | inconsistent |

**Key risk**: Candidate source polls external API (`uainvest.com.ua`) every 60s even with no profile set.

---

## 6. Profile Selection Decision Matrix

### By deployment method

| Method | Profile Selected | Mechanism | Overridable? |
|--------|-----------------|-----------|--------------|
| `./gradlew bootRunMonitor` | `local-safe` | build.gradle env default | Yes — set `SPRING_PROFILES_ACTIVE` |
| `./gradlew bootRunEngine` | `local-safe` | build.gradle env default | Yes — set `SPRING_PROFILES_ACTIVE` |
| `java -jar app.jar` (direct) | **None** | — | Yes — JVM arg |
| `java -jar app.jar --spring.profiles.active=X` | `X` | JVM arg | N/A |
| `docker compose up` (local) | **None** | No env set | Yes — add to environment: |
| `docker compose -f deploy/docker-compose.yml` (deploy) | `prod-like` / `staging` | Hardcoded in YAML | No — hardcoded |
| CI `build`/`test`/`bootJar` | **None** | No runtime profile needed | N/A — compile only |
| CI deploy → staging | `prod-like` / `staging` | Via deploy Compose | No — hardcoded |

### By module availability

| Profile | monitor-app | engine-app | telegram-bot-app |
|---------|-------------|------------|-------------------|
| `local-safe` | ✅ Explicit | ✅ Explicit | ✅ Filename only |
| `testnet` | ❌ | ✅ Explicit | ❌ |
| `staging` | ✅ Explicit | ✅ Explicit | ✅ Filename only |
| `prod-like` | ✅ Explicit | ✅ Explicit | ❌ |

---

## 7. Key Findings

1. **No single source of truth** — profile selection depends on how the app is launched
2. **build.gradle defaults only apply to `bootRun*` tasks** — running via `java -jar` requires explicit env or JVM args
3. **telegram-bot-app lacks `spring.config.activate.on-profile`** in its profile YAMLs — works via filename convention but inconsistent with other modules
4. **Root `Dockerfile` does not set profiles** — containers rely entirely on runtime environment
5. **`docker-compose.yml` (local with observability) sets no profile** — containers run with base defaults only
6. **`deploy/docker-compose.yml` hardcodes profiles** — not overridable via `.env` file
7. **CI build/test jobs never set profiles** — these are compile-only operations
8. **No programmatic profile selection exists** — all main classes use `SpringApplication.run()` without customization
9. **Running without any profile is safe** (no execution, no live orders, no auth) but candidate source still polls external API every 60s
10. **Test confirms JVM argument mechanism** — `EngineApplicationTest` tests `--spring.profiles.active=local-safe`
