# CI/CD

## Минимум для MVP
- GitHub Actions:
    - build (Gradle)
    - unit tests
    - docker build
    - push image в registry (ghcr)

## Deploy
- одна VM (Ubuntu)
- docker run с volume:
    - /opt/fundingarb:/data
- env vars через .env file или secret manager
