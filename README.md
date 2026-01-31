# Crypto Funding Arbitrage Bot

Spring Boot 3 + Java 21 сервис, который получает сигналы фандинга из Telegram, хранит подтверждения в SQLite и отправляет заявки на биржи (Binance, Bybit, Gate) перед выплатой фандинга. Frontend — Vite/React/TS (собирается через Gradle task `frontendBuild`).

## Быстрый старт (локально)
- JDK 21, Node скачает Gradle плагин сам.
- Прописать ENV (см. `docs/02-runtime-config.md`), минимум: `TG_API_ID`, `TG_API_HASH`, `TG_PHONE`, `TG_BOT_USERNAME`, `TG_BOT_TOKEN`, ключи бирж для выбранного режима.
- Запуск: `./gradlew bootRun -PtdNativesClassifier=$(./gradlew -q printOsDetector || echo linux_amd64_gnu_ssl3)` или проще `./gradlew bootRun`.
- Тесты: `./gradlew test --no-daemon`.

## Docker
- Сборка: `docker build --build-arg TD_NATIVES=linux_amd64_gnu_ssl3 -t yourrepo/crypto-funding:local .`
- Запуск: `docker run -p 8090:8090 --env-file .env -v $(pwd)/data:/data yourrepo/crypto-funding:local`
- Volume `/data` хранит SQLite и TDLib сессии.

## CI/CD
- GitHub Actions (`.github/workflows/ci-cd.yml`): build+tests на push/PR, для `main` дополнительно Docker build & push в Docker Hub (`DOCKERHUB_USERNAME`/`DOCKERHUB_TOKEN` secrets).

## Полезные ссылки
- Документация по модулям: `docs/00-overview.md`.
- Конфиг и переменные окружения: `docs/02-runtime-config.md`.
- CI/CD детали: `docs/08-ci-cd.md`.
