# CI/CD

## Текущее состояние (2026-01-31)
- GitHub Actions (`.github/workflows/ci-cd.yml`):
    - build + test (`./gradlew clean build --no-daemon`)
    - Docker buildx
    - push образ в Docker Hub (теги `latest` и commit SHA) — требуются secrets `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`

## Deploy
- одна VM (Ubuntu)
- docker run с volume:
    - /opt/fundingarb:/data
- env vars через .env file или secret manager
