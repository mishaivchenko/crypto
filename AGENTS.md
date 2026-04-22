# Repository Guidelines

## Project Structure & Module Organization
- `platform-core/src/main/java` contains shared domain, contracts, ports, and utilities.
- `monitor-app/src/main/java` contains the operator/control-plane Spring Boot runtime.
- `engine-app/src/main/java` contains the lightweight execution-side Spring Boot runtime.
- `monitor-app/src/test/java` and `engine-app/src/test/java` contain JUnit tests; `monitor-app/src/test/js` contains static UI tests.
- `monitor-app/src/main/resources` and `engine-app/src/main/resources` hold runtime configuration and static assets.
- `config/` contains runtime config overrides (for deployments).
- `data/` is used for runtime data like the SQLite database.

## Build, Test, and Development Commands
- `./gradlew bootRunMonitor` runs the monitor runtime locally (uses Java 25 toolchain).
- `./gradlew bootRunEngine` runs the engine runtime locally (uses Java 25 toolchain).
- `./gradlew test` runs the multi-module backend and UI verification suite.
- `./gradlew build` builds both application jars and runs the full verification lifecycle.
- `./gradlew security` runs OWASP dependency-check with a CVSS threshold of 7.0.

## Coding Style & Naming Conventions
- Java: 4-space indentation, standard Spring Boot conventions, packages under `com.crypto.funding`.
- There is no active formatter/linter configured (Spotless is present but commented out).

## Testing Guidelines
- Backend tests use JUnit 5 with AssertJ and Mockito; keep tests alongside the owning module under `*/src/test/java`.
- Test class naming follows `*Test` (e.g., `MarketCacheTest`).

## Commit & Pull Request Guidelines
- This repository has no commit history yet, so no enforced commit message format.
- Prefer short, imperative subjects (e.g., “Add funding refresh endpoint”).
- PRs should include: summary, testing notes, and UI screenshots if UI changes affect the frontend.

## Security & Configuration Tips
- Runtime secrets should come from environment variables (see each module's `src/main/resources/application.yml`).
- Avoid committing real API keys or bot tokens; use placeholders or `.env` in local setups.
- История Git переписана для удаления утёкших ключей; новые токены храните только в секретах/ENV.

## CI/CD
- GitHub Actions workflow `.github/workflows/ci-cd.yml` собирает и тестирует проект; для `main` образ пушится в Docker Hub (`DOCKERHUB_USERNAME`/`DOCKERHUB_TOKEN` secrets).
