# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java` contains the Spring Boot backend (packages under `com.crypto.funding`).
- `src/test/java` contains JUnit tests for backend modules.
- `src/main/resources` holds configuration (`application.yml`) and static assets.
- `config/` contains runtime config overrides (for deployments).
- `data/` is used for runtime data like the SQLite database.

## Build, Test, and Development Commands
- `./gradlew bootRun` runs the backend locally (uses Java 21 toolchain).
- `./gradlew test` runs backend unit tests (JUnit 5).
- `./gradlew build` builds the backend jar and runs the full verification lifecycle.
- `./gradlew security` runs OWASP dependency-check with a CVSS threshold of 7.0.

## Coding Style & Naming Conventions
- Java: 4-space indentation, standard Spring Boot conventions, packages under `com.crypto.funding`.
- There is no active formatter/linter configured (Spotless is present but commented out).

## Testing Guidelines
- Backend tests use JUnit 5 with AssertJ and Mockito; keep tests alongside modules under `src/test/java`.
- Test class naming follows `*Test` (e.g., `MarketCacheTest`).

## Commit & Pull Request Guidelines
- This repository has no commit history yet, so no enforced commit message format.
- Prefer short, imperative subjects (e.g., “Add funding refresh endpoint”).
- PRs should include: summary, testing notes, and UI screenshots if UI changes affect the frontend.

## Security & Configuration Tips
- Runtime secrets should come from environment variables (see `src/main/resources/application.yml`).
- Avoid committing real API keys or bot tokens; use placeholders or `.env` in local setups.
- История Git переписана для удаления утёкших ключей; новые токены храните только в секретах/ENV.

## CI/CD
- GitHub Actions workflow `.github/workflows/ci-cd.yml` собирает и тестирует проект; для `main` образ пушится в Docker Hub (`DOCKERHUB_USERNAME`/`DOCKERHUB_TOKEN` secrets).
