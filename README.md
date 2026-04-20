# Funding Platform 2.0.0

`2.0.0` — это новая основная линия проекта с явным split на два независимо деплоимых runtime-модуля: `monitor-app` и `engine-app`.

Текущий бизнес-flow:

`Funding API -> SignalCandidate -> Review -> FundingEvent -> ArmedTrade -> Trade Journal`

## Что входит в 2.0.0

### `platform-core`
Общий contracts/core модуль:
- domain models `SignalCandidate / FundingEvent / ArmedTrade / OrderAttempt`
- shared ports and contracts
- engine plan DTOs
- crypto/symbol utilities
- no Spring Boot runtime
- no persistence ownership

### `monitor-app`
Операторский модуль:
- основной UI
- dashboard
- candidates review
- funding events
- armed trades
- venue diagnostics
- journal access
- encrypted per-operator exchange credentials
- internal API for engine plan sync

### `engine-app`
Лёгкий execution-preparation модуль:
- отдельный Spring Boot runtime
- отдельный порт
- no bot UI
- no candidate polling
- no metadata auto-sync on startup
- lightweight read/planning API for armed trades through monitor REST

Текущий engine не исполняет live orders. Его задача в `2.0.0` — быть минимальным и пригодным как будущая база для execution runtime.

## Источник кандидатов

Основной источник:
- [uainvest funding API](https://uainvest.com.ua/api/funding?sort_by=funding&sort_dir=asc&limit=30)

Сигнал из источника создаёт только `SignalCandidate`. `FundingEvent` появляется после operator review.

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

## Users And Keys

Operator API защищён `X-Operator-Token`.

Bootstrap операторов:
- `SECURITY_OPERATOR_BOOTSTRAP_USERS=alice:token,bob:token`

Exchange keys:
- хранятся в `operator_exchange_credential`
- шифруются AES-GCM
- master key приходит только из `CREDENTIALS_MASTER_KEY_BASE64`
- API отдаёт только masks/status, raw secrets не возвращаются
- ключи изолированы по `operator_id`

Credential API:
- `GET /api/v1/operators/me/credentials`
- `PUT /api/v1/operators/me/credentials/{venue}/{mode}`
- `DELETE /api/v1/operators/me/credentials/{venue}/{mode}`
- `POST /api/v1/operators/me/credentials/{venue}/{mode}/check`

## Safety

По умолчанию:
- старый execution code удалён
- live execution loop ещё не реализован
- engine только читает планы из monitor
- internal monitor→engine API защищён `X-Internal-Token`

То есть текущую ветку можно запускать локально без риска случайного live execution.

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
- shared core как contracts/domain фундамент

Следующий шаг после этой версии — развивать `engine-app` в сторону реального execution orchestration, не раздувая его UI- и monitor-зависимостями.
