# Funding Platform 2.0.0

`2.0.0` — это новая основная линия проекта с явным split на `monitor` и `engine`.

Текущий бизнес-flow:

`Funding API -> SignalCandidate -> Review -> FundingEvent -> ArmedTrade -> Trade Journal`

## Что входит в 2.0.0

### `platform-core`
Общий backend-контур:
- домен `SignalCandidate / FundingEvent / ArmedTrade`
- persistence и SQLite schema
- candidate review
- event creation
- armed trade lifecycle
- journal
- venue metadata registry
- venue diagnostics
- execution safety guards

### `monitor-app`
Операторский модуль:
- основной UI
- dashboard
- candidates review
- funding events
- armed trades
- venue diagnostics
- journal access

### `engine-app`
Лёгкий execution-preparation модуль:
- отдельный Spring Boot runtime
- отдельный порт
- no bot UI
- no candidate polling
- no metadata auto-sync on startup
- lightweight read/planning API for armed trades

Текущий engine не исполняет live orders. Его задача в `2.0.0` — быть минимальным и пригодным как будущая база для execution runtime.

## Источник кандидатов

TDLib больше не используется для формирования кандидатов.

Основной источник:
- [uainvest funding API](https://uainvest.com.ua/api/funding?sort_by=funding&sort_dir=asc&limit=30)

Telegram, если включён, остаётся только как optional bot surface для summary/launcher и не участвует в candidate ingestion.

## Запуск

Требования:
- JDK 21

Основные команды:
- `./gradlew test`
- `./gradlew build`
- `./gradlew bootRunMonitor`
- `./gradlew bootRunEngine`

Артефакты:
- monitor jar: `monitor-app/build/libs/*-monitor.jar`
- engine jar: `engine-app/build/libs/*-engine.jar`

Порты по умолчанию:
- monitor: `8090`
- engine: `8091`

## UI

Рабочий операторский интерфейс находится в `monitor-app`.

Что уже есть:
- Dashboard
- Candidates
- Funding Events
- Armed Trades
- Venues
- Journal drawer

UI рассчитан на operator workflow, а не на маркетинговую витрину:
- low-noise
- mobile-friendly
- чёткое различие между candidate / event / trade
- быстрые действия approve / reject / arm / sync

## Engine API

`engine-app` предоставляет lightweight internal API:
- `GET /internal/engine/summary`
- `GET /internal/engine/plans`
- `GET /internal/engine/plans/{armedTradeId}`

Это read-side слой для подготовки исполнения и operator visibility.

## Safety

По умолчанию:
- `trading.execution.mode=DISABLED`
- legacy execution выключен
- `gate` заблокирован в legacy execution path

То есть `2.0.0` можно запускать локально без риска случайного live execution.

## Тесты

Важные свойства текущей линии:
- core business flow покрыт тестами
- engine flow покрыт отдельными тестами
- multi-module build проходит целиком

Проверено:
- `./gradlew --no-daemon test build`

## Архитектурный смысл версии

`2.0.0` — это не “ещё один бот”.

Это новая модульная база для следующих фаз:
- monitor как operator/control plane
- engine как будущий lightweight execution runtime
- shared core как доменный и persistence фундамент

Следующий шаг после этой версии — развивать `engine-app` в сторону реального execution orchestration, не раздувая его UI- и monitor-зависимостями.
