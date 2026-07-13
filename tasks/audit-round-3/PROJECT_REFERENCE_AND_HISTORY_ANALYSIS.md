# Project Reference and History Analysis

**Repository:** /Users/mishaivchenko/dev/crypto
**Analysis Date:** 2026-07-13
**Current Branch:** feat/auto-approval-sweep-159 (on `origin`, diverged from `main`)

---

## A. Ownership Map

Each module and top-level directory area mapped to its responsible team/role, with evidence from the codebase.

| Area | Owner / Role | Evidence |
|---|---|---|
| **platform-core/** | Domain team (pure library) | No Spring Boot dependencies, no persistence. `build.gradle` absent of Spring Boot plugin. Domain records in `com.crypto.funding.domain.*`, contracts in `com.crypto.funding.contract.engine.*`, ports in `com.crypto.funding.application.port.*`. |
| **monitor-app/** | Operations / UI team (control plane) | Spring Boot runtime on port 8090. REST controllers in `api/`, command/query services in `application/`, JPA persistence in `infrastructure/persistence/`, vanilla JS UI in `static/`. |
| **engine-app/** | Execution team (runtime) | Lightweight Spring Boot on port 8091. No persistence — reads plans from monitor via REST. 16 production classes. All production classes under `com.crypto.funding.engine.*`. |
| **telegram-bot-app/** | Notifications team | Spring Boot runtime, Telegram bot `@funding_arbitrage_bot_bot`. Polls monitor-app for signal/trade data. |
| **docs/** | Documentation | 14 numbered docs (00–13), engine TDD docs, superpowers specs. |
| **wiki/** | Knowledge base (Obsidian vault) | Schema defined in `wiki/CLAUDE.md`. Contains `raw/`, `pages/concepts/`, `pages/entities/`, `pages/sources/`. |
| **scripts/** | DevOps / Automation | Python + bash: `pr_review/` (PR review LLM pipeline), `error_monitor/` (GitHub Actions error analysis), CI helper scripts. |
| **deploy/** | Ops / Deployment | Docker Compose files. `docker-compose.yml` at root bundles monitor + engine + Prometheus + Grafana into one stack. `deploy/observability/` is optional standalone. |
| **config/** | Runtime config | `config/application.yaml` — override YAML for deployments. |

---

## B. Module Dependency Map

Verified against `settings.gradle` and each module's `build.gradle`.

```
telegram-bot-app ──┐
                    ├── platform-core   (ZERO external deps)
monitor-app ────────┘

engine-app ─────────┘
```

- **platform-core** has ZERO dependencies on any other module in the project. Its only dependency is test frameworks (AssertJ, Mockito, WireMock) and JUnit platform.
- **monitor-app** depends on `platform-core` (compile dependency). No dependency on `engine-app` or `telegram-bot-app`.
- **engine-app** depends on `platform-core` (compile dependency). No dependency on `monitor-app` or `telegram-bot-app`.
- **telegram-bot-app** depends on `platform-core` (compile dependency). Communicates with monitor-app via HTTP at runtime (not compile-time).
- **No circular dependencies** — verified from `settings.gradle` (line 7-10): all four modules included flat. Dependency is strictly one-directional from monitor/engine/telegram -> platform-core.
- The internal engine API (`/internal/engine/*`) is the only runtime coupling between monitor-app and engine-app, mediated by `X-Internal-Token` and Feign clients (`EnginePlanClient`, `MonitorApiClient`).

---

## C. Entry Points

### Runtime Entry Points

| Application | Class | Port | Module |
|---|---|---|---|
| Monitor control plane | `MonitorApplication.java` | 8090 | monitor-app |
| Engine execution runtime | `EngineApplication.java` | 8091 | engine-app |
| Telegram bot | `TelegramBotApplication.java` | 8092 | telegram-bot-app |

All three are Spring Boot applications launched via `SpringApplication.run()`.

### Build Entry Points

| File | Role |
|---|---|
| `build.gradle` (root) | Root build: plugins, subproject config, shared tasks (`bootRunMonitor`, `bootRunEngine`, `bootRunTelegramBot`, `security`, `engineTddGate`, `engineTddDocsCheck`). |
| `settings.gradle` | Defines project name (`funding-platform`) and includes all 4 modules. Uses `foojay-resolver-convention` 1.0.0 for JDK toolchain resolution. |
| `gradle.properties` | JVM args, parallelism cache, stale version properties (see stale docs section). |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle wrapper configuration. |

### Deploy Entry Points

| File | Role |
|---|---|
| `Dockerfile` | Single Dockerfile for both monitor and engine. Uses `eclipse-temurin:25-jre`. Multi-stage via build args `APP_MODULE`, `APP_CLASSIFIER`, `APP_PORT`. |
| `docker-compose.yml` (root) | Full stack: monitor (8090), engine (8091), Prometheus (9090), Grafana (3000). All services env-configurable. |
| `deploy/docker-compose.yml` | Separate observability stack (Prometheus + Grafana). |
| `deploy/.env.example` | Template `.env` for Docker deployments with all supported env vars documented. |
| `deploy/observability/` | Grafana dashboards (3 JSON), provisioning config, Prometheus config. |

### CI Entry Points

| File | Role |
|---|---|
| `.github/workflows/ci-cd.yml` | Main CI/CD: build, test, security check, Docker build + push (main branch). |
| `.github/workflows/pr-review.yml` | PR review automation: runs DeepSeek-based code review on PRs. |
| `.github/workflows/error-monitor.yml` | Error monitoring: parses CI failures, fingerprints, reports. |

### Other Entry Points

| File | Role |
|---|---|
| `.gitignore` | Ignores: IDE files, build artifacts, Python cache, Node modules, env files, `.claude/`. |
| `.dockerignore` | Excludes from Docker build context: `org/`, `META-INF/`, `funding-memory/`, `memory/`, `frontend/`, `scripts/`. |
| `.editorconfig` | Editor formatting conventions. |
| `AGENTS.md` | Agent instructions for Claude Code (duplicated scope with CLAUDE.md). |
| `CLAUDE.md` | Primary Claude Code instructions. |
| `README.md` | Project overview, quick start, architecture docs. |
| `HELP.md` | Generic help file. |

---

## D. Orphan Source Files

Files that appear unused, unreachable, or stale, with evidence for each claim.

### D.1 Root `org/` directory (Spring Boot compiled classes)

- **Path:** `/Users/mishaivchenko/dev/crypto/org/`
- **Content:** 80+ compiled `.class` files from `org.springframework.boot.loader` — `UrlJarFile.class`, `JarUrlConnection.class`, `DefaultCleaner.class`, `UrlNestedJarFile.class`, etc.
- **Evidence:** These are Spring Boot fat-jar loader classes extracted during a build or launch. They are NOT referenced by any build process, NOT compiled from source in this project, and are explicitly excluded from Docker builds via `.dockerignore` (line: `org/`). They serve no purpose in the development tree and are build artifacts that were accidentally committed or not cleaned up.
- **Git status:** Tracked (appear in `git ls-files` as unregistered entries? No — they are NOT in the tracked-files.txt output, meaning they are untracked but NOT in `.gitignore`.)

Wait — the `org/` directory was NOT listed in `tracked-files.txt`, but it was confirmed to exist at `/Users/mishaivchenko/dev/crypto/org/`. It is untracked and NOT covered by `.gitignore` (the `.gitignore` does not mention `org/`). It remains as a stray artifact.

### D.2 Root `META-INF/` directory

- **Path:** `/Users/mishaivchenko/dev/crypto/META-INF/services/java.nio.file.spi.FileSystemProvider`
- **Content:** Single ServiceLoader registration: `org.springframework.boot.loader.nio.file.NestedFileSystemProvider`
- **Evidence:** This ServiceLoader file pairs with the `org/` compiled classes above. It registers a Spring Boot `FileSystemProvider` that only makes sense inside a Spring Boot fat-jar (`NestedFileSystemProvider`). In a development tree it is dead code. No build process references this `META-INF/` directory. Also excluded via `.dockerignore`.
- **Git status:** Tracked (listed as tracked file #13).

### D.3 gradle.properties version properties (stale duplicates)

- **Path:** `/Users/mishaivchenko/dev/crypto/gradle.properties`
- **Stale values:**
  - `springBootVersion=3.5.2` — actual version in `build.gradle` (line 3): `3.5.14`
  - `owaspDepCheckVersion=10.0.4` — actual version in `build.gradle` (line 5): `12.1.8`
  - `assertjVersion=3.25.3` — matches `build.gradle` line 53
  - `mockitoVersion=5.12.0` — matches `build.gradle` line 54
  - `junitJupiterVersion=5.10.3` — NOT used in `build.gradle` (test framework version managed by Spring Boot BOM)
- **Evidence:** `gradle.properties` defines versions that are NOT read by `build.gradle`. The `build.gradle` re-declares `springBootVersion` in its `ext` block and uses it for the Spring Boot BOM. The `gradle.properties` versions (except `spotlessVersion`) are unused cargo-cult values. Only `spotlessVersion=6.25.0` is actually used (matches `build.gradle` line 7).

### D.4 single_funding.sql

- **Path:** `/Users/mishaivchenko/dev/crypto/single_funding.sql`
- **Content:** Historical SQL query against old schema (`approved_funding`, `approved_funding_exchange`, `order_execution_time` tables that no longer exist in the codebase). References a pre-split schema (before the current `SignalCandidate` -> `FundingEvent` -> `ArmedTrade` -> `OrderAttempt` model).
- **Evidence:** Tables `approved_funding`, `approved_funding_exchange`, `order_execution_time` do not exist in current Flyway migrations. No code references this file. It is a dead artifact from an earlier version of the platform.

### D.5 funding-memory/ (empty Obsidian vault)

- **Path:** `/Users/mishaivchenko/dev/crypto/funding-memory/`
- **Content:** Obsidian vault metadata only (`.obsidian/` with `app.json`, `appearance.json`, `core-plugins.json`, `graph.json`, `workspace.json`). No actual notes.
- **Evidence:** Empty vault — never populated. No files outside `.obsidian/`. This was a dead-end attempt to create an Obsidian knowledge base. The actual knowledge base lives at `memory/` (also mostly empty) and `wiki/` (populated). Also excluded via `.dockerignore`.

### D.6 memory/ (second empty Obsidian vault)

- **Path:** `/Users/mishaivchenko/dev/crypto/memory/`
- **Content:** Obsidian vault metadata (same structure as `funding-memory/`), plus one empty directory `Без названия/` (Russian: "Untitled").
- **Evidence:** Another empty vault. Contains `.obsidian/` config files and a zero-content directory. It appears the user started two Obsidian vaults (`funding-memory/` and `memory/`) and never populated either. The `wiki/` directory is the canonical knowledge base.

### D.7 .worktrees/ (empty directory)

- **Path:** `/Users/mishaivchenko/dev/crypto/.worktrees/`
- **Content:** Empty directory (only `.` and `..` entries).
- **Evidence:** Created 2026-06-12, never populated. Possibly a git worktree management artifact. Empty directories have no impact but add noise.

### D.8 package-lock.json (from removed frontend)

- **Path:** `/Users/mishaivchenko/dev/crypto/package-lock.json`
- **Content:** npm lockfile for a React/Vite/TypeScript frontend with dependencies on `@radix-ui/react-*`, `react`, `tailwind-merge`, `date-fns`, `sonner`, etc.
- **Evidence:** The project no longer has a separate frontend/ directory (confirmed: `frontend/` is in `.gitignore`). The actual UI is vanilla JS in `monitor-app/src/main/resources/static/`. There is no `package.json` in the root. This `package-lock.json` is a stray artifact from when the UI was a separate Vite/TS app that was later removed. Also excluded via `.dockerignore`.

### D.9 `tasks/` directory — no, these are active. But check...

The `tasks/` directory contains feature tasks (B-1 through B-5, F-1 through F-5) and README. These are working documents, not orphans. Kept.

### D.10 `scripts/pr_review/__pycache__/` — stale `.pyc` files

Git shows modified `__pycache__/parser.cpython-314.pyc` and `quality_gate.cpython-314.pyc`. These are compiled bytecache files that should be in `.gitignore`. The `.gitignore` does include `__pycache__/` and `*.pyc`, but these files appear to have been committed or are showing as modified due to a cache mismatch.

---

## E. Legacy / Stale Documentation

Claims in documentation files that are demonstrably inaccurate compared to the current codebase.

### E.1 CLAUDE.md: "13 core production classes" — actual count is 16

- **File:** `/Users/mishaivchenko/dev/crypto/CLAUDE.md`, line 54
- **Claim:** "13 core production classes, every one covered by pitest mutation testing at 100%"
- **Reality:** 16 production classes in `engine-app/src/main/java/com/crypto/funding/engine/`:
  1. `EngineApplication.java`
  2. `EngineController.java`
  3. `EngineCredentialCache.java`
  4. `EngineCredentialStatusController.java`
  5. `EngineExecutionScheduler.java`
  6. `EngineExecutionService.java`
  7. `EngineMetricsPublishProperties.java`
  8. `EngineMetricsPublisher.java`
  9. `EngineModuleConfiguration.java`
  10. `EnginePlanClient.java`
  11. `EnginePlanService.java`
  12. `EngineProperties.java`
  13. `EngineRuntimeControlService.java`
  14. `EngineTelemetryService.java`
  15. `CredentialAwareExecutionPort.java`
  16. `LiveExchangeExecutionPort.java`
- **Conclusion:** CLAUDE.md undercounts by 3 classes. The count was likely correct at some earlier point and not updated as new classes were added (`EnginePlanService.java`, `EngineTelemetryService.java`, `EngineRuntimeControlService.java`, `EngineCredentialStatusController.java`, `EngineMetricsPublishProperties.java`, `EngineCredentialCache.java`, `LiveExchangeExecutionPort.java` — several of these were added over time).

### E.2 CLAUDE.md: "V1–V5 migrations" — V14 exists

- **File:** `/Users/mishaivchenko/dev/crypto/CLAUDE.md`, line 44
- **Claim:** "schema owned by Flyway (V1–V5 migrations)"
- **Reality:** Flyway migrations V1 through V14 exist:
  - `V1__baseline.sql` (SQL)
  - `V2__order_attempt_fill_fields.java`
  - `V3__trade_journal_add_cancel_event_code.java`
  - `V4__armed_trade_mode.java`
  - `V5__order_attempt_request_duration.java`
  - `V6__armed_trade_sltp.java`
  - `V7__venue_default_latency.java`
  - `V8__liquidity_assessment.java`
  - `V9__armed_trade_warmup.java`
  - `V10__liquidity_signal_candidate.java`
  - `V11__ai_signal_advice.java`
  - `V12__drop_operator_token_hash.java`
  - `V13__restore_operator_token_hash.java`
  - `V14__auto_approval_rules.sql`
- **Additional stale references to V1-V5:** `docs/01-system-flow.md` (line 34), `docs/06-data-model.md` (line 145), `README.md` (line 55), `telegram-bot-app/src/main/resources/faq/settings.md` (line 24).
- **Conclusion:** Widespread stale documentation — 5 separate files all reference V1-V5 when V14 is the latest.

### E.3 CLAUDE.md: Mentions 3 venue adapter ports

- **File:** `/Users/mishaivchenko/dev/crypto/CLAUDE.md`, lines 63-66
- **Claim:** Each venue implements three interfaces: `VenueCredentialCheckPort`, `VenueMetadataPort`, `VenueMarkPricePort`.
- **Reality:** There is a 4th interface — `VenueOrderBookPort` — defined in `platform-core/src/main/java/com/crypto/funding/application/port/VenueOrderBookPort.java`, and implemented by all 5 venues (BybitOrderBookAdapter, GateOrderBookAdapter, OkxOrderBookAdapter, BitgetOrderBookAdapter, KucoinOrderBookAdapter). The order book port was added after the documentation was written.

### E.4 docs/06-data-model.md: "V1 baseline contains full schema"

- **File:** `/Users/mishaivchenko/dev/crypto/docs/06-data-model.md`, line 158
- **Claim:** "V1 baseline содержит полную schema включая operator_account, operator_exchange_credential, trade_position, trade_outcome и все остальные таблицы. Отдельных SQL-миграций V3–V5 нет — они были частью первоначального baseline."
- **Reality:** This was once true (V1 was a complete baseline), but V2-V14 now contain incremental Java-based and SQL-based migrations. The statement is historical but misleading without context — newer readers would think V2-V14 don't exist.

### E.5 gradle.properties stale version values

- **File:** `/Users/mishaivchenko/dev/crypto/gradle.properties`
- **Stale values:** `springBootVersion=3.5.2` (should be 3.5.14), `owaspDepCheckVersion=10.0.4` (should be 12.1.8), `junitJupiterVersion=5.10.3` (unused, version managed by Spring Boot BOM), `tyrusVersion=2.1.4` (unused), `tdlightVersion=3.3.0` (unused for TDLib/telegram).
- **Evidence:** `gradle.properties` contains version constants that `build.gradle` does not reference. The `ext` block in `build.gradle` re-declares `springBootVersion`. The OWASP plugin version is set inline in `build.gradle`. `tyrusVersion` and `tdlightVersion` appear to be remnants of an abandoned TDLib-based Telegram approach (the current bot uses standard Telegram Bot API via HTTP).

### E.6 CLAUDE.md: Only mentions Bybit and Gate in LiveExchangeExecutionPort doc

- **File:** `/Users/mishaivchenko/dev/crypto/CLAUDE.md`, line 86
- **Claim:** "Implement `submit<Venue>(plan, intent, reduceOnly, attemptedAt)` following the Bybit/Gate pattern"
- **Reality:** `LiveExchangeExecutionPort.java` implements submission for all 5 venues: Bybit, Gate, OKX, KuCoin, and Bitget. The documentation only mentions 2 venues as reference.

### E.7 `docs/00-current-state.md` through `docs/13-engine-tdd-migration-plan.md`

These files were reviewed for currency. The core architecture descriptions are still accurate (three modules, venue adapter pattern, signal ingestion flow, etc.). However, several have not been updated to reflect:
- Auto-approval pipeline (added in commit `aac34ce`)
- Unified Settings screen (added in commit `4137ce4`)
- Enrichment Sprint features (Sprints 0-9, June 2026)
- Auto-approval sweep (current branch)

---

## F. Agent Instruction Conflict Matrix

Three files govern Claude Code's behavior in this repository:

| Aspect | `CLAUDE.md` (project root) | `AGENTS.md` (project root) | `wiki/CLAUDE.md` |
|---|---|---|---|
| **Scope** | Full project overview | Technical guidelines for agents | Wiki schema + maintenance rules |
| **Build commands** | Identical set | Identical set | N/A |
| **Architecture** | Detailed 3-module flow | Module layout + CI/CD details | Domain context (funding scalping) |
| **Testing guidelines** | Engine TDD rules | Engine TDD rules (same) | N/A |
| **CI/CD** | Not mentioned | GitHub Actions details | N/A |
| **Security** | Credential isolation | Credential isolation + history rewrite note | N/A |
| **Naming conventions** | Not mentioned | Java package conventions | Wiki page naming |
| **Branch/PR** | Not mentioned | Short imperative subjects | N/A |

**Conflict Assessment:** The three files are **largely compatible** — they cover different scopes:
- `CLAUDE.md` — intended as the primary agent instruction file for all work in the repo.
- `AGENTS.md` — a duplicate/subset that reinforces the same build/test commands and adds security and CI guidance. It does NOT contradict CLAUDE.md but repeats much of it without adding new architectural guidance.
- `wiki/CLAUDE.md` — strictly governs wiki maintenance. No overlap with the other two.

**Redundancy concern:** `CLAUDE.md` and `AGENTS.md` overlap significantly in build commands, module descriptions, and testing guidelines. `AGENTS.md` could potentially be merged into `CLAUDE.md` to reduce duplication.

---

## G. Development History and Recent Activity

### G.1 Git History Context

- **Default branch:** `main`
- **Current branch:** `feat/auto-approval-sweep-159` (diverged from main containing auto-approval sweep feature)
- **Recent merges to main (since June 2026):**
  - `feat/engine-metrics-panel-163` — Engine Metrics Panel on overview (#167)
  - `feat/order-waterfall-165` — Order Execution Waterfall chart (#168)
  - `feat/overview-snapshot-style-162` — Unify Operations Snapshot style (#166)
  - `feat/auto-approval-sweep-159` — Auto-approval sweep of NORMALIZED candidates (#164)
  - `feat/settings-screen-153` — Unified Settings screen (#158)
  - Multiple `feat/enrichment-sprint*` branches (Sprints 0-9, June 2026)

### G.2 Active Workstreams (from commit history)

1. **Enrichment Pipeline (Sprints 0-9):** Largest active workstream. Adding layered cards, enrichment delta, timeline, verdict blocks, auto-approval layer UI, mobile optimization, i18n. Mostly in `monitor-app/static/`.
2. **Overview Dashboard Refactoring:** Multiple recent fixes removing stale components (Venue pulse panel, Critical Metrics block, Dev Tools section). Unified Operation Snapshot style.
3. **Auto-Approval Pipeline:** Configurable rule-based auto-approval (merged), sweep of existing NORMALIZED candidates (current branch).
4. **Engine Metrics Panel:** New overview panel showing engine metrics.
5. **Order Waterfall:** New chart for order execution visualization.
6. **PR Review Automation:** Always-post behavior, DeepSeek JSON repair, CI fixes.

### G.3 Current Branch Context

`feat/auto-approval-sweep-159` adds the ability to sweep existing NORMALIZED candidates when auto-approval is enabled or a new rule is activated. The most recent commit on this branch: `c5cce55 test(auto-approval): add unit tests for sweepNormalized and findAllIdsByStatus`.

---

## H. Summary of Findings

### Orphan Files (9 items)

| File/Directory | Type | Recommendation |
|---|---|---|
| `org/` (root) | Extracted Spring Boot loader classes | Add to `.gitignore`, clean up |
| `META-INF/` (root) | ServiceLoader with dead registration | Add to `.gitignore`, clean up |
| `single_funding.sql` | Historical SQL against removed schema | Archive or delete |
| `funding-memory/` | Empty Obsidian vault | Delete (unused) |
| `memory/` | Empty Obsidian vault (with empty dir inside) | Delete (unused) |
| `.worktrees/` | Empty directory | Delete (unused) |
| `package-lock.json` | Stale npm lockfile from removed frontend | Delete (unused) |
| `gradle.properties` stale versions | Orphaned version constants | Clean up unused properties |
| `scripts/pr_review/__pycache__/*.pyc` | Compiled bytecache | Confirm in `.gitignore`, clean if tracked |

### Stale Documentation (7 findings)

| Document | Stale Claim | Reality |
|---|---|---|
| CLAUDE.md line 54 | "13 core production classes" | 16 classes exist |
| CLAUDE.md line 44 | "V1-V5 migrations" | V14 is latest |
| CLAUDE.md lines 63-66 | 3 venue ports | 4 ports (add VenueOrderBookPort) |
| CLAUDE.md line 86 | Bybit/Gate only as reference | 5 venues (add OKX, KuCoin, Bitget) |
| docs/01-system-flow.md line 34 | V1-V5 | V14 exists |
| docs/06-data-model.md line 145 | V1-V5 | V14 exists |
| README.md line 55 | V1-V5 | V14 exists |
| faq/settings.md line 24 | V1-V5 | V14 exists |

### Agent Instruction Redundancy

`AGENTS.md` and `CLAUDE.md` overlap significantly (>50% content duplication). Consider merging or clearly delineating scope.
