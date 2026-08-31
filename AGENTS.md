# Repository Guidelines

## Project Structure & Module Organization
- `platform-core/src/main/java` contains shared domain, contracts, ports, and utilities — no Spring, no persistence.
- `monitor-app/src/main/java` contains the operator/control-plane Spring Boot runtime.
  - `application/ai/` — AI Signal Advisor service (DeepSeek integration, GO/WATCH/PASS recommendations)
  - `infrastructure/ai/` — DeepSeek HTTP client
  - `infrastructure/exchange/` — venue adapters (Bybit, Gate, OKX, Bitget, KuCoin)
  - `infrastructure/persistence/` — JPA repositories; schema owned by Flyway (never Hibernate auto-DDL)
- `engine-app/src/main/java` contains the lightweight execution-side Spring Boot runtime.
- `telegram-bot-app/src/main/java` contains the Telegram bot runtime (`@funding_arbitrage_bot_bot`).
- `monitor-app/src/test/java` and `engine-app/src/test/java` contain JUnit tests; `monitor-app/src/test/js` contains static UI tests.
- `monitor-app/src/main/resources` and `engine-app/src/main/resources` hold runtime configuration and static assets.
- `config/` contains runtime config overrides (for deployments).
- `data/` is used for runtime data like the SQLite database.
- `docs/` contains the Phase 0 docs-reset stubs and the Engine TDD program (`docs/engine-tdd/`); narrative docs are intentionally removed until rewritten.

## Build, Test, and Development Commands
- `./gradlew bootRunMonitor` runs the monitor runtime locally (uses Java 25 toolchain).
- `./gradlew bootRunEngine` runs the engine runtime locally (uses Java 25 toolchain).
- `./gradlew test` runs the multi-module backend and UI verification suite.
- `./gradlew build` builds all application jars and runs the full verification lifecycle.
- `./gradlew quality` runs fast blocking quality gates (Spotless Java/text, monitor UI ESLint, monitor UI Prettier check).
- `./gradlew qualityReport` runs report-only PMD, SpotBugs/FindSecBugs, and dependency-analysis reports.
- `./gradlew engineTddGate` runs engine mutation + coverage gate (100% PIT, 95% line / 90% branch).
- `./gradlew spotlessCheck` runs the active Spotless formatter/linter (Java, Gradle, Markdown, YAML).
- `npm --prefix monitor-app run lint` runs ESLint for the static monitor UI.
- `npm --prefix monitor-app run format:check` checks static monitor UI formatting with Prettier.
- `./gradlew build -PerrorProneEnabled=false` temporarily disables Error Prone if a Java toolchain/plugin compatibility issue blocks compilation.
- `./gradlew security` runs OWASP dependency-check with a CVSS threshold of 7.0.
- `./gradlew engineTddDocsCheck` verifies requirement IDs in `docs/engine-tdd/gap-matrix.md`.

## Coding Style & Naming Conventions
- Java: 4-space indentation, standard Spring Boot conventions, packages under `com.crypto.funding`.
- Spotless IS active — run `./gradlew spotlessCheck` before committing; CI enforces it.
- Java is formatted by Palantir Java Format via Spotless; do not mix format-only churn with semantic changes.

## Testing Guidelines
- Backend tests use JUnit 5 with AssertJ and Mockito; keep tests alongside the owning module under `*/src/test/java`.
- Test class naming follows `*Test` (e.g., `AiSignalAdvisorServiceTest`).
- Every production class in `engine-app` must have 100% mutation coverage (PIT). New engine classes require entries in `docs/engine-tdd/gap-matrix.md`.
- Schema changes go through Flyway migrations only — never edit JPA entities expecting Hibernate auto-DDL.

## Commit & Pull Request Guidelines
- Prefer short, imperative subjects (e.g., "fix AI prompt strategy description").
- PRs should include: summary, testing notes, and UI screenshots if frontend changes are involved.
- GitHub Issues and milestones are the source of truth for tracked work; Projects/boards are optional views only.
- Never push directly to `main`; all changes go through pull requests.
- Pull requests require human approval before merge. Do not self-merge automation or agent-authored PRs.

## Security & Configuration Tips
- Runtime secrets come from environment variables (see each module's `src/main/resources/application.yml`).
- Avoid committing real API keys or bot tokens; use placeholders or `.env` in local setups.
- History has been rewritten to remove leaked keys; store new tokens only in secrets/ENV.
- Exchange credentials are AES-GCM encrypted per `operator_id + venue + mode`; raw secrets are never returned by the API.

## CI/CD
- GitHub Actions workflow `.github/workflows/ci-cd.yml` builds and tests the project; for `main` the image is pushed to Docker Hub (`DOCKERHUB_USERNAME`/`DOCKERHUB_TOKEN` secrets).
