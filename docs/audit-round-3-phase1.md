---
type: report
title: Audit Round 3 — Phase 1: Repository & Build System
created: 2026-07-13
tags:
  - audit
  - technology-matrix
  - reproducibility
  - build-system
related:
  - '[[199-Audit-Project-Compass]]'
  - '[[CLAUDE]]'
---

# Audit Round 3 — Phase 1: Repository & Build System

## Section 2 — Repository and Source Layout

### Basic Repo Info

| Attribute | Value |
|-----------|-------|
| **Current branch** | `feat/auto-approval-sweep-159` |
| **HEAD SHA** | `c5cce55c8bc4640121b1ecf5cd23e9619bc729e9` |
| **HEAD message** | `test(auto-approval): add unit tests for sweepNormalized and findAllIdsByStatus` |
| **Git status** | 2 modified (Python `__pycache__/*.pyc`), 5 untracked items (`.maestro/`, `.superpowers/`, 3 audit task docs) |
| **Submodules** | None |
| **Symlinks** | None (outside `.git/`) |

### Version Control Findings

- **Gitignore coverage**: ✅ `.env`, ✅ `*.log`, ✅ `build/`, ✅ `.gradle/`, ✅ `*.db`, ✅ `*.db-journal`, ✅ `.DS_Store`
- **Missed in gitignore**: `*.db-wal` and `*.db-shm` not explicitly listed, but covered by `data/` and `/data/` exclusion patterns
- **Untracked startup-affecting files**: `.env` (local config), `.maestro/` (agent orchestration config), `.superpowers/`
- **Git-tracked binary artifacts**: only `gradle/wrapper/gradle-wrapper.jar` (intentional — `!gradle/wrapper/gradle-wrapper.jar`)
- **macOS-only files**: `.DS_Store` present on disk but properly excluded via `.gitignore`
- **Windows support**: `gradlew.bat` exists and is tracked
- **Stale gitignore entries**: `frontend/` exclusion rules — the `frontend/` directory does not exist on disk

### Directory Analysis

| Category | Directories |
|----------|-------------|
| **Production source** | `platform-core/src/main/java/`, `monitor-app/src/main/java/`, `engine-app/src/main/java/`, `telegram-bot-app/src/main/java/`, `monitor-app/src/main/resources/static/` |
| **Test source** | `platform-core/src/test/java/`, `monitor-app/src/test/java/`, `engine-app/src/test/java/`, `telegram-bot-app/src/test/java/` |
| **Generated** | `build/`, `.gradle/`, `*/build/` |
| **Runtime data** | `data/` (SQLite), `data-container/` (Docker), `deploy/data/` |
| **Historical/abandoned** | `funding-memory/` (Obsidian vault — only workspace config), `data-container/` (legacy Docker data mount) |

### Dead/Obsolete Artifacts

| Artifact | Status | Recommendation |
|----------|--------|---------------|
| `frontend/` | **Does not exist** — remove gitignore entries referencing it | Clean up gitignore |
| `funding-memory/` | Contains only `.obsidian/` config files — appears abandoned since ~May 2026 | Remove from repo (personal notes) |
| `single_funding.sql` | Manual SQL script for ATOM/USDT Binance insert — last meaningful change 2026-05-09 | Remove (one-off, schema has evolved) |
| `data-container/` | Older Docker volume data — superseded by `deploy/` and `docker-compose.yml` | Remove if no Docker Compose workflow uses it |
| `gradle.properties:springBootVersion=3.5.2` | **Outdated** — `build.gradle` overrides with `3.5.14` | Remove dead property |

### Proposed Minimal Repository Root

```
.config/              # application.yaml (Spring Boot overrides)
deploy/               # Docker Compose + observability
docs/                 # Architecture docs, engine TDD
engine-app/           # Execution runtime (Spring Boot)
gradle/wrapper/       # Gradle wrapper
monitor-app/          # Operator control plane (Spring Boot)
platform-core/        # Domain library (no Spring)
scripts/              # CI/PR utility scripts
telegram-bot-app/     # Telegram bot (Spring Boot)
.gitignore
build.gradle
gradle.properties
gradlew
gradlew.bat
settings.gradle
Dockerfile
docker-compose.yml
```

**Remove**: `funding-memory/`, `single_funding.sql`, `data-container/`, `META-INF/` (unused — root-level has no fat-jar), `wiki/` (personal notes).

---

## Section 3 — Languages and Platforms

### Language Inventory

| Language | Production | Test | Build/CI | Utility |
|----------|-----------|------|----------|---------|
| **Java** | 332 files | 428 files | — | — |
| **JavaScript** | 32 files (vanilla JS UI) | 1 test dir (`src/test/js`) | — | — |
| **HTML** | 1 file (`index.html`) | — | — | — |
| **CSS** | 1 file | — | — | — |
| **SQL** | — | — | — | 3 files (Flyway migrations) |
| **Python** | — | — | — | 37 files (PR review scripts) |
| **Shell** | — | — | — | 4 scripts |
| **Gradle DSL** | — | — | `build.gradle` (Groovy) | — |
| **YAML** | — | — | 19 files (CI, Compose) | — |
| **JSON** | — | — | — | 15 files (config) |

**Note:** No Kotlin, Groovy (for app source), or TypeScript used.

### Java Version Analysis

| Property | Value |
|----------|-------|
| **Local JDK** | OpenJDK 25.0.2 (Homebrew) |
| **Local javac** | 25.0.2 |
| **Gradle toolchain** | `JavaLanguageVersion.of(25)` |
| **Docker compile** | N/A (pre-built JAR, no multi-stage) |
| **Docker runtime** | `eclipse-temurin:25-jre` |
| **Compatibility** | ✅ Compile JDK (25) == Runtime JRE (25) |
| **Preview features** | Not detected |
| **Incubator modules** | Not detected |
| **Virtual threads** | Not detected (`Thread.ofVirtual()`, `Executors.newVirtualThreadPerTaskExecutor()` not found) |

### Platform Compatibility

| Concern | Status |
|---------|--------|
| **JNI / native libs** | Only `@Query(nativeQuery = true)` — no actual native libraries |
| **CPU architecture** | Docker: `arm64` compatible via `eclipse-temurin:25-jre` (multi-arch) |
| **macOS (Apple Silicon)** | ✅ Verified — Gradle 9.1.0 aarch64 daemon, build passes |
| **Linux x86-64** | ✅ Docker image uses multi-arch `eclipse-temurin` |
| **Bash-specific syntax** | Shell scripts use `[[ ]]`, `&>`, `set -euo pipefail` — **not macOS-compatible** via `/bin/sh` |
| **GNU-utils compatibility** | No `grep -P` or `sed -i` (without backup) detected — safe |
| **JavaScript** | Vanilla JS only (no build step, no Node.js runtime dependency) |
| **Node.js available** | v25.8.1, npm 11.11.0 |
| **Vite in build flow** | Not used — JS is static files in `monitor-app/src/main/resources/static/` |
| **Python version** | 3.14.4 |
| **Python runtime** | Required only for PR review scripts (`scripts/pr_review/`) |
| **System packages needed** | JDK 25 (`eclipse-temurin`), Docker (optional), Node.js (for UI tests, optional) |

### macOS vs Linux Behavioral Differences

The shell scripts use Bash-specific features (`[[ ]]`, `&>`, `set -euo pipefail`). They work on macOS via `/bin/bash` (macOS ships Bash 3.2+) but will fail if invoked via `/bin/sh` (which is `dash` on Debian/Ubuntu). The shebangs use `#!/usr/bin/env bash`, so they are safe if `bash` is installed on both platforms.

**Recommendation**: Add a shebang audit — `scripts/*.sh` already use `#!/usr/bin/env bash` ✅.

---

## Section 4 — Gradle Build System

### Wrapper and Settings

| Property | Value |
|----------|-------|
| **Gradle version** | 9.1.0 (2025-09-18) |
| **Wrapper committed** | ✅ `gradlew`, `gradlew.bat`, `gradle/wrapper/` all tracked |
| **Distribution URL** | `https://services.gradle.org/distributions/gradle-9.1.0-bin.zip` |
| **Checksum validation** | Enabled (`validateDistributionUrl=true`) |
| **Modules** | `platform-core`, `monitor-app`, `engine-app`, `telegram-bot-app` |
| **All modules on disk** | ✅ Verified |
| **Circular dependencies** | None — all modules depend only on `platform-core` |

### Plugins

| Plugin | Version | Scope |
|--------|---------|-------|
| `org.springframework.boot` | 3.5.14 | Root (apply false), monitor-app, engine-app, telegram-bot-app |
| `io.spring.dependency-management` | 1.1.7 | Root (apply false), all subprojects |
| `org.owasp.dependencycheck` | 12.1.8 | Root (apply false), all subprojects |
| `info.solidsoft.pitest` | 1.19.0 | Root (apply false), platform-core, engine-app |
| `com.diffplug.spotless` | 6.25.0 | Root |
| `java-library` | — | platform-core |
| `java` | — | All subprojects |
| `jacoco` | — | platform-core, engine-app |

### Version Management

| Mechanism | Status |
|-----------|--------|
| **Version catalog** (`libs.versions.toml`) | ❌ Not used |
| **BOM** — Spring Boot | ✅ `spring-boot-dependencies:${springBootVersion}` |
| **BOM** — Spring Cloud | ✅ `spring-cloud-dependencies:${springCloudVersion}` |
| **Spring Dependency Management plugin** | ✅ Applied to all subprojects |
| **Dead properties** | `gradle.properties:springBootVersion=3.5.2` — **overridden** by `build.gradle` `ext.springBootVersion = '3.5.14'` |
| **Dynamic versions** | None discovered |
| **SNAPSHOT dependencies** | None discovered |
| **Local JARs** | None |
| **`mavenLocal()` configured** | No |

### Repositories

| Repository | Type | Credentials Required |
|------------|------|---------------------|
| `mavenCentral()` | Public | No |

**Offline risk**: Only Maven Central is configured. If unavailable, the build cannot resolve dependencies.

### Build Reproducibility

| Concern | Status |
|---------|--------|
| **`./gradlew clean build` succeeds** | ✅ Passes (5s with cache, ~119s initial daemon + tests) |
| **Dependency locking** | ❌ Not configured |
| **Dependency verification** | ❌ No `verification-metadata.xml` or keyring |
| **Gradle build cache** | ✅ `org.gradle.caching=true` in `gradle.properties` (local only) |
| **Configuration cache** | ❌ Not configured |
| **Remote build cache** | ❌ Not configured |
| **Offline build** | ❌ Not possible after initial download — no dependency locking or verification |

### Custom Tasks

| Task | Group | Description |
|------|-------|-------------|
| `bootRunMonitor` | application | Run monitor-app with local-safe defaults |
| `bootRunEngine` | application | Run engine-app with local-safe defaults |
| `bootRunTelegramBot` | application | Run Telegram bot with local-safe defaults |
| `security` | verification | OWASP dependency check (all modules) |
| `engineTddDocsCheck` | verification | Verify engine TDD requirement IDs consistency |
| `engineTddGate` | verification | Full TDD gate (docs + tests + coverage + mutation) |
| `engineTddCoverageReport` | verification | Generate JaCoCo report (platform-core + engine-app) |
| `engineTddCoverageVerification` | verification | JaCoCo coverage thresholds (95% LINE, 90% BRANCH) |
| `frontendTest` | verification | Node.js UI tests |
| `engineAcceptanceTest` | test | Monitor-side engine integration tests |

### Task Build Maps

| Command | Executes |
|---------|----------|
| `./gradlew build` | Compile → spotlessCheck → test → jacocoReport → assemble/bootJar → check |
| `./gradlew check` | spotlessCheck → test → frontendTest |
| `./gradlew test` | All module tests (JUnit 5) |

### Task Safety Analysis

| Risk | Tasks |
|------|-------|
| **Local env vars required** | `bootRunMonitor`, `bootRunEngine`, `bootRunTelegramBot` (all have `local-safe` fallbacks ✅) |
| **Network access required** | `security` (NVD API key needed), dependency resolution (Maven Central) |
| **Local DB modification** | Integration tests (use SQLite in-memory/test DBs in `build/` ✅) |
| **Exchange access** | Engine acceptance tests pass without live credentials (mock-based) |
| **Order creation risk** | None — `ENGINE_LIVE_ORDER_ENABLED=false` in all profiles |

### Recommendations

1. **Fix springBootVersion conflict**: Remove `springBootVersion=3.5.2` from `gradle.properties` (dead — `build.gradle` uses `ext.springBootVersion = '3.5.14'`)
2. **Remove dead artifacts**: `funding-memory/`, `single_funding.sql`, `data-container/` (unless actively used)
3. **Remove stale gitignore entries**: `frontend/` references (directory doesn't exist)
4. **Add `verifyAll` task**: Combines `spotlessCheck` + `test` + `engineTddGate` + `security` for CI
5. **Add `smokeTest` task**: Fast subset of tests (platform-core + engine-app unit tests, no integration tests)
6. **Consider dependency locking**: `gradle.lockfile` for reproducible builds
7. **Consider configuration cache**: Gradle recommends it for build speed

---

## Reproducibility Report

### Summary

| Component | Reproducible? | Notes |
|-----------|---------------|-------|
| Build on clean machine | ✅ (partially) | Requires JDK 25 + Git + network |
| Cache-free build | ✅ | Builds with `--no-daemon` |
| Version pinning | ✅ | Static versions in `build.gradle`; no dynamic/SNAPSHOT deps |
| Dependency locking | ❌ | No lockfile — latest compatible versions resolved at build time |
| Verification metadata | ❌ | No checksum verification for transitive deps |
| Docker reproducibility | ⚠️ | Single-stage with pre-built JAR; `eclipse-temurin:25-jre` pinned but Docker image tag may drift |
| Commit traceability | ✅ | Full SHA tracked; `gradlew` from wrapper |
| Environment recovery | ❌ | `.env` files untracked; credentials in env vars only |

### Key Issues

1. **No dependency locking** — builds may produce different results over time as transitive dependencies release new versions
2. **Docker image tags** — `eclipse-temurin:25-jre` is a mutable tag; consider pinning to digest `@sha256:...`
3. **Environment variables** — Required env vars are documented but not verified at build time
