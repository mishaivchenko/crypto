# Funding-Event Trading Platform (Phase 3 Venue-Aware Control Plane)

Java 21 / Spring Boot монолит, который проходит контролируемый takeover и разворот домена от legacy funding-bot логики к funding-event trading системе.

## Что это сегодня
- Основной источник кандидатов — внешний funding API:
  - `https://uainvest.com.ua/api/funding?sort_by=funding&sort_dir=asc&limit=30`
- Сервис poll-ит funding API, обновляет наблюдаемые watchlists и формирует `SignalCandidate` без TDLib.
- Telegram bot, если включён, остаётся только operator/diagnostic интерфейсом и больше не участвует в candidate ingestion.
- Legacy funding flow всё ещё присутствует в коде для совместимости и диагностики.
- Новый домен уже введён параллельно legacy-коду:
  - `FundingEvent`
  - `ArmedTrade`
  - `OrderAttempt`
  - `Position`
  - `TradeOutcome`
  - `VenueTimingProfile`
- Phase 2 добавляет review-driven candidate flow:
  - `Telegram signal -> SignalCandidate -> Review -> FundingEvent`
- Phase 3 добавляет venue-aware preparation flow:
  - `FundingEvent -> ArmedTrade -> Trade Journal`
- Появился multi-venue metadata registry для Binance / Bybit / Gate с диагностикой sync/request timing.
- Внутренний REST API `/api/v1/**` стал первым правильным входом в новый домен.

## Чего это пока НЕ делает
- Это ещё не production trading engine для ловли funding-движения.
- Это не Gate MVP и не live execution orchestration нового домена.
- Это не подписочный multi-user продукт.
- Telegram bot больше не считается control plane для торговли.

## Safety by Default
- Runtime по умолчанию работает в `trading.execution.mode=DISABLED`.
- Legacy execution выключен по умолчанию через `trading.execution.legacy-enabled=false`.
- Реальное legacy execution возможно только при явном opt-in:
  - `trading.execution.mode=LIVE`
  - `trading.execution.legacy-enabled=true`
  - venue должен быть в `trading.execution.live-venues`
- `gate` намеренно заблокирован по умолчанию через `trading.execution.blocked-venues`.
- В safe-mode scheduler может работать как passive detector, но не должен ставить ордера и не должен молча помечать legacy funding как `executed`.

## Локальный запуск
Требования:
- JDK 21
- при необходимости Node будет скачан Gradle plugin'ом, но активного frontend pipeline в текущем состоянии репозитория нет
- SQLite runtime директория создаётся автоматически, если путь из `spring.datasource.url` указывает на отсутствующую папку

Команды:
- `./gradlew bootRun`
- `./gradlew test`
- `./gradlew build`

Если в системе несколько JDK, запускай Gradle с Java 21.

## Основные контуры
- `candidate-source` / `watchlist`: внешний funding ingest и наблюдаемое состояние
- `telegram.bot`: необязательный operator/diagnostic интерфейс
- legacy scheduler/execution: transitional code path, жёстко guard'ится
- `domain.*`: новый funding-event / trade / execution / profile домен
- `application.*`: command/query services и порты
- `infrastructure.persistence.*`: новая persistence-модель для core skeleton
- `api`: legacy endpoints + новый internal REST API

## Внутренний API нового домена
- `GET /api/v1/candidates`
- `GET /api/v1/candidates/{id}`
- `POST /api/v1/candidates/{id}/approve`
- `POST /api/v1/candidates/{id}/reject`
- `POST /api/v1/funding-events`
- `GET /api/v1/funding-events`
- `GET /api/v1/funding-events/{id}`
- `POST /api/v1/funding-events/{id}/arm`
- `POST /api/v1/armed-trades`
- `GET /api/v1/armed-trades`
- `GET /api/v1/armed-trades/{id}`
- `GET /api/v1/venues`
- `GET /api/v1/venues/{venue}`
- `POST /api/v1/venues/{venue}/sync`
- `GET /api/v1/venues/{venue}/instruments`
- `GET /api/v1/venues/timings`

## Документация
- [Overview](docs/00-overview.md)
- [Runtime config](docs/02-runtime-config.md)
- [Funding candidate source](docs/06-telegram-tdlib.md)
- [DB schema](docs/03-db-schema.md)
- [Phase 0-1 foundation](docs/phase0-phase1-foundation.md)
- [Phase 2 candidate review flow](docs/10-candidate-review-flow.md)
- [Phase 3 venue-aware arming](docs/11-phase3-venue-aware-arming.md)

## Статус направления
Репозиторий развивается как modular rewrite inside the repo:
- сейчас это всё ещё один deployable сервис,
- дальше возможен split на `signal-ingest`, `control-api`, `execution-engine`,
- но на текущей фазе приоритет — безопасный runtime, честные границы и чистый доменный фундамент.
