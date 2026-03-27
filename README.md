# Funding-Event Trading Platform (Phase 0-1 Foundation)

Java 21 / Spring Boot монолит, который проходит контролируемый takeover и разворот домена от legacy funding-bot логики к funding-event trading системе.

## Что это сегодня
- Telegram/TDLib ingest остаётся рабочим: сигналы из канала продолжают читаться и обновлять наблюдаемые watchlists.
- Legacy funding flow всё ещё присутствует в коде для совместимости и диагностики.
- Новый домен уже введён параллельно legacy-коду:
  - `FundingEvent`
  - `ArmedTrade`
  - `OrderAttempt`
  - `Position`
  - `TradeOutcome`
  - `VenueTimingProfile`
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

Команды:
- `./gradlew bootRun`
- `./gradlew test`
- `./gradlew build`

Если в системе несколько JDK, запускай Gradle с Java 21.

## Основные контуры
- `telegram` / `watchlist`: ingest и наблюдаемое состояние
- legacy scheduler/execution: transitional code path, жёстко guard'ится
- `domain.*`: новый funding-event / trade / execution / profile домен
- `application.*`: command/query services и порты
- `infrastructure.persistence.*`: новая persistence-модель для core skeleton
- `api`: legacy endpoints + новый internal REST API

## Внутренний API нового домена
- `POST /api/v1/funding-events`
- `POST /api/v1/armed-trades`
- `GET /api/v1/armed-trades`
- `GET /api/v1/armed-trades/{id}`

## Документация
- [Overview](docs/00-overview.md)
- [Runtime config](docs/02-runtime-config.md)
- [DB schema](docs/03-db-schema.md)
- [Phase 0-1 foundation](docs/phase0-phase1-foundation.md)

## Статус направления
Репозиторий развивается как modular rewrite inside the repo:
- сейчас это всё ещё один deployable сервис,
- дальше возможен split на `signal-ingest`, `control-api`, `execution-engine`,
- но на текущей фазе приоритет — безопасный runtime, честные границы и чистый доменный фундамент.
