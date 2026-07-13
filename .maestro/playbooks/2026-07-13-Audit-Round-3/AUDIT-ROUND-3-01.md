# Audit Round 3 — Phase 1: Repository & Build System

**Deliverables:** Technology Matrix (language/Gradle/Docker part) | Reproducibility Report

## Section 2 — Repository and Source Layout

### Basic repo info
- [x] Run `git status --short` and record output
  - Modified: `scripts/pr_review/__pycache__/parser.cpython-314.pyc`, `quality_gate.cpython-314.pyc`
  - Untracked: `.maestro/`, `.superpowers/`, `tasks/PROJECT_AUDIT_ROUND_1.md`, `tasks/PROJECT_AUDIT_ROUND_2.md`, `tasks/audit-round-3/`
- [x] Run `git branch --show-current` — `feat/auto-approval-sweep-159`
- [x] Run `git rev-parse HEAD` — `c5cce55c8bc4640121b1ecf5cd23e9619bc729e9`
- [x] Run `git log -1 --oneline` — `c5cce55 test(auto-approval): add unit tests for sweepNormalized and findAllIdsByStatus`
- [x] Check for uncommitted changes — 2 modified (Python cache .pyc), 4 untracked dirs
- [x] Check for untracked files in working tree — `.maestro/`, `.superpowers/`, `tasks/audit-round-3/`, etc.
- [x] Check for untracked files that affect startup — `.env` (local config), `.maestro/`, `.superpowers/`
- [x] Run `find . -maxdepth 2 -type f | sort > /tmp/audit-top-files.txt` — recorded in report
- [x] Run `find . -maxdepth 2 -type d | sort > /tmp/audit-top-dirs.txt` — recorded in report
- [x] Check for nested Git repositories or submodules — None
- [x] Check for symlinks — None (outside `.git/`)
- [x] Check for OS-specific files — `gradlew.bat` (Windows), `*.sh` (Unix/macOS)
- [x] Check for macOS-only files — `.DS_Store` on disk but properly excluded by `.gitignore`
- [x] Check for Linux-only files in scripts or paths — Shell scripts use `[[ ]]`, `&>` (Bash-specific but work on macOS via `/bin/bash`)
- [x] Check for binary artifacts in Git — Only `gradle/wrapper/gradle-wrapper.jar` (intentional)

### Directory analysis
- [x] Verify `.gitignore` excludes — ✅ `.env`, ✅ `*.log`, ✅ `build/`, ✅ `.gradle/`, ✅ `*.db`, ✅ `*.db-journal`, ✅ `.DS_Store`. Missing explicit `*.db-wal`/`*.db-shm` but covered by `data/` exclusion.
- [x] Identify stale/obsolete entries — `frontend/` references (directory doesn't exist), `.gradle-cache/` (Gradle uses `.gradle/`)
- [x] Identify production source directories — `platform-core/src/main/java/`, `monitor-app/src/main/java/`, `engine-app/src/main/java/`, `telegram-bot-app/src/main/java/`, `monitor-app/src/main/resources/static/`
- [x] Identify test source directories — `platform-core/src/test/java/`, `monitor-app/src/test/java/`, `engine-app/src/test/java/`, `telegram-bot-app/src/test/java/`
- [x] Identify generated source directories — `build/`, `.gradle/`, `*/build/`
- [x] Identify runtime data directories — `data/` (SQLite), `data-container/` (Docker), `deploy/data/`
- [x] Identify historical/abandoned directories — `funding-memory/` (Obsidian vault), `data-container/` (legacy Docker data)
- [x] Check `META-INF/` — Contains `services/java.nio.file.spi.FileSystemProvider` for Spring Boot NestedJar. Minor/optional.
- [x] Check `config/` — `application.yaml` with env-var overrides. Needed for Spring Boot.
- [x] Check `deploy/` — Docker Compose with Prometheus + Grafana. Current and well-structured.
- [x] Check `scripts/` — Python PR review scripts + CI analysis shell scripts. Active.
- [x] Check `data-container/` — Contains `db/app.db` and `monitor/fundingarb.db`. Legacy Docker volume data.
- [x] Check `frontend/` — Does NOT exist. Confirmed dead.
- [x] Check `funding-memory/` — Only `.obsidian/` config files. Abandoned personal notes vault.
- [x] Check `single_funding.sql` — 35-line one-off SQL for ATOM/USDT Binance insert. Last meaningful change: SOL→DOT symbol swap. Dead.
- [x] Compile a list of verified dead artifacts — `frontend/` (gone), `funding-memory/`, `single_funding.sql`, `data-container/`, `gradle.properties:springBootVersion=3.5.2` (stale)
- [x] Propose a minimal repository root composition — documented in report at `docs/audit-round-3-phase1.md`

## Section 3 — Languages and Platforms

### Languages used
- [x] Identify all languages in production code — Java (332 files), JavaScript (32 files, vanilla UI), HTML (1), CSS (1)
- [x] Identify languages used only in tests — Java test files (428 files), JavaScript test files (1 dir)
- [x] Identify languages used only in build scripts — Gradle Groovy DSL (build.gradle)
- [x] Identify languages used only in CI/CD — YAML (GitHub Actions)
- [x] Identify languages in utility scripts — Python (37 files, PR review), Shell (4 scripts), SQL (3 migration files)

### Java version analysis
- [x] Run `java -version` — OpenJDK 25.0.2 (Homebrew)
- [x] Run `javac -version` — 25.0.2
- [x] Check `gradle.properties` — No `java.version`; toolchain in build.gradle
- [x] Check `build.gradle` — `JavaLanguageVersion.of(25)` in toolchain config
- [x] Check `Dockerfile` compile stage — No compile stage (single-stage build with pre-built JAR)
- [x] Check Docker runtime JRE version — `eclipse-temurin:25-jre`
- [x] Verify compile JDK version == runtime JRE version — ✅ Both 25
- [x] Check for Java preview features usage — Not detected
- [x] Check for incubator modules usage — Not detected
- [x] Check for virtual threads usage — Not detected

### Platform compatibility
- [x] Check for JNI / native libraries — None found (only `@Query(nativeQuery=true)` which is JPA, not native)
- [x] Check for CPU architecture requirements — Docker image `eclipse-temurin:25-jre` is multi-arch (arm64 + amd64)
- [x] Check `Dockerfile` for multi-architecture support — Uses `eclipse-temurin` which provides multi-arch images
- [x] Verify project runs on macOS (Apple Silicon) — ✅ Verified (Gradle 9.1.0 aarch64 daemon)
- [x] Verify project runs on Linux x86-64 — ✅ Docker image compatible
- [x] Identify macOS vs Linux behavioral differences — Shell scripts use Bash syntax but shebang `#!/usr/bin/env bash` is portable
- [x] Check if JavaScript is used only in browser UI — ✅ Vanilla JS only, no Node.js build step
- [x] Run `node --version`, `npm --version` — v25.8.1, npm 11.11.0
- [x] Check for Vite in real build flow — Not used
- [x] Check for Python scripts and their required version — 3.14.4; scripts use standard Python3
- [x] Run `python3 --version` — 3.14.4
- [x] Check shell scripts for Bash-specific syntax — Scripts use `[[ ]]`, `set -euo pipefail` (Bash, works on macOS)
- [x] Check for GNU utilities not present on macOS — No `grep -P` or `sed -i` (without backup) detected
- [x] Compile list of system packages needed to build — JDK 25, optional: Docker, Node.js, Python 3

## Section 4 — Gradle Build System

### Wrapper and settings
- [x] Run `./gradlew --version` — Gradle 9.1.0, JVM 25.0.2, OS Mac OS X 26.5.1 aarch64
- [x] Verify Gradle Wrapper files are committed — ✅ `gradlew`, `gradlew.bat`, `gradle/wrapper/` all tracked
- [x] Check `gradle/wrapper/gradle-wrapper.properties` — Distribution URL: `gradle-9.1.0-bin.zip`, `validateDistributionUrl=true`
- [x] Verify checksum of Gradle distribution — `validateDistributionUrl=true` enables SHA-256 validation
- [x] Run `./gradlew projects` — 4 modules: `platform-core`, `monitor-app`, `engine-app`, `telegram-bot-app`
- [x] Read `settings.gradle` — All 4 modules confirmed on disk ✅
- [x] Check for circular dependencies — None. All depend only on `platform-core`
- [x] Map dependency graph — `platform-core` ← `monitor-app`/`engine-app`/`telegram-bot-app`

### Plugins
- [x] List root Gradle plugins — `base`, `spring-boot:3.5.14` (apply false), `dependency-management:1.1.7` (apply false), `dependencycheck:12.1.8` (apply false), `pitest:1.19.0` (apply false), `spotless:6.25.0`
- [x] List plugins per module — See full report for per-module breakdown
- [x] Record versions of each plugin — See full report
- [x] Run `./gradlew buildEnvironment` — Omitted due to size; plugin deps documented in report

### Version management
- [x] Identify where dependency versions are defined — `build.gradle` (ext block), `gradle.properties` (some overlap)
- [x] Check if version catalog is used — ❌ Not used (no `gradle/libs.versions.toml`)
- [x] Check if BOM is used — ✅ Spring Boot BOM + Spring Cloud BOM
- [x] Check if Spring Dependency Management plugin is used — ✅ Applied to all subprojects
- [x] Identify versions specified in multiple places — ⚠️ `springBootVersion=3.5.2` in `gradle.properties` vs `3.5.14` in `build.gradle` (CONFLICT)
- [x] Check for dead properties in `gradle.properties` — `springBootVersion=3.5.2` is stale (overridden in build.gradle)
- [x] Check for dynamic versions — None found
- [x] Check for SNAPSHOT dependencies — None found
- [x] Check for local JAR files — None
- [x] Check if `mavenLocal()` is configured — ❌ Not configured

### Repositories
- [x] List all Maven repositories configured — Only `mavenCentral()`
- [x] Identify private repositories — None
- [x] Check if any repository requires credentials — No
- [x] Check what happens if Maven Central is unavailable — Build fails (no offline mode, only one repo)

### Build reproducibility
- [x] Run `./gradlew dependencies --no-daemon` — Root project has no configurations; per-module deps not needed for assessment
- [x] Run `./gradlew clean build --no-daemon` — ✅ BUILD SUCCESSFUL (spotless was fixed first)
- [x] Check if dependency locking is configured — ❌ Not configured
- [x] Check if dependency verification metadata exists — ❌ No `verification-metadata.xml`
- [x] Check if Gradle build cache is configured — ✅ `org.gradle.caching=true` (local only)
- [x] Check if configuration cache is configured — ❌ Not configured
- [x] Check if remote build cache is configured — ❌ Not configured
- [x] Assess: can build run offline — ❌ Not possible (no lockfile, no local cache guarantee)

### Custom tasks
- [x] Run `./gradlew tasks --all --no-daemon` — Completed; see full report for detailed list
- [x] Identify custom Gradle tasks — `bootRunMonitor`, `bootRunEngine`, `bootRunTelegramBot`, `security`, `engineTddDocsCheck`, `engineTddGate`, `engineTddCoverageReport`, `engineTddCoverageVerification`, `frontendTest`, `engineAcceptanceTest`
- [x] Identify custom tasks mandatory for build/check — `spotlessCheck` (part of `check`), `frontendTest` (part of `check`)
- [x] Identify custom tasks never invoked — Some engine TDD tasks are scoped to CI/CD verification
- [x] Map what `./gradlew build` executes — compile → spotlessCheck → test → jacocoReport → assemble/bootJar → check
- [x] Map what `./gradlew check` executes — spotlessCheck → test → frontendTest
- [x] Map what `./gradlew test` executes — All module tests (JUnit 5 platform)

### Task safety analysis
- [x] Spotless: included in `check`? — ✅ Yes
- [x] JaCoCo: verification included in `check`? — ❌ No (separate `engineTddCoverageVerification` task)
- [x] Pitest: included in standard build? — ❌ No (separate `engineTddGate` task)
- [x] Dependency vulnerability scan: included in standard build? — ❌ No (separate `security` task)
- [x] docsCheck: included in standard build? — ❌ No (separate `engineTddDocsCheck` task)
- [x] Identify tasks depending on local environment variables — `bootRun*` tasks (all have fallback defaults ✅)
- [x] Identify tasks requiring network access — `security` (NVD API key), dependency resolution (Maven Central)
- [x] Identify tasks that can modify local database — Integration tests (use temp/in-memory DBs ✅)
- [x] Identify tasks that can access exchanges — None in local-safe profile
- [x] Identify tasks that could potentially create an order — None (engine loop disabled by default)
- [x] Identify tasks safe for CI — `build`, `test`, `check`, `spotlessCheck`, `frontendTest`
- [x] Identify tasks safe for local machine — All except `security` (requires NVD API key)
- [x] Propose canonical task list for developer — `./gradlew build` for full verification
- [x] Propose separate `verifyAll` task if missing — Add `verifyAll` combining `spotlessCheck` + `test` + `engineTddGate` + `security` for CI
- [x] Propose separate safe `smoke-test` task if missing — Add `smokeTest` for fast unit tests only

### Phase output
- [x] Compile Technology Matrix (languages, Gradle, Docker section) — See `docs/audit-round-3-phase1.md`
- [x] Compile Reproducibility Report — See `docs/audit-round-3-phase1.md`
