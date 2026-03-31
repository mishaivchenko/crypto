# Phase 0-1 Foundation

## What Was Implemented
- Введён safe-by-default execution model:
  - `ExecutionMode = DISABLED | SHADOW | LIVE`
  - `trading.execution.legacy-enabled`
  - `trading.execution.live-venues`
  - `trading.execution.blocked-venues`
- Legacy execution path теперь guard'ится единым `LegacyExecutionGuard`.
- `gate` заблокирован в legacy execution path по умолчанию.
- Legacy scheduler/executor больше не должны молча исполнять или мутировать состояние в safe mode.
- Telegram/TDLib ingest сохранён.
- Telegram bot переведён в ingest/diagnostic роль:
  - новые core actions не добавлялись,
  - legacy trading controls в bot отключены.
- Введён новый доменный скелет:
  - `FundingEvent`
  - `ArmedTrade`
  - `OrderIntent`
  - `OrderAttempt`
  - `Position`
  - `TradeOutcome`
  - `VenueTimingProfile`
- Добавлена новая persistence-модель и JPA repositories для нового домена.
- Добавлен внутренний REST API:
  - `POST /api/v1/funding-events`
  - `POST /api/v1/armed-trades`
  - `GET /api/v1/armed-trades`
  - `GET /api/v1/armed-trades/{id}`
- Добавлены Jakarta validation и centralized error handling через `@RestControllerAdvice`.

## What Was Intentionally NOT Implemented
- Полный funding-event trading engine
- Gate-first live MVP
- Автоматический entry/exit execution loop нового домена
- Реальные market data / order status adapters под новые порты
- Risk engine
- Auth / users / subscription model
- Flyway/Liquibase
- Microservice split
- Автоматический bridge от legacy approval к `ArmedTrade`

## Architecture Decisions
- Репозиторий развивается как modular rewrite inside the current monolith.
- Telegram остаётся ingest adapter'ом, а не control plane.
- Новый control plane начинается с internal REST API.
- Legacy funding scheduler/execution сохранён только как transitional layer.
- Новый домен отделён от Telegram и legacy approval-модели.
- SQLite оставлен как временное runtime storage ради безопасной эволюции.

## Migration Notes
- `ApprovedFundingEntity` и связанный scheduler flow больше не считаются целевой моделью.
- Legacy classes по-прежнему существуют, потому что на них опираются watchlists, Telegram UI и часть старых тестов, но их роль теперь explicitly legacy.
- New code не должен зависеть от legacy execution abstractions.
- Для unit-тестов сохранены permissive constructors у legacy execution classes, чтобы не ломать старую инфраструктурную тестовую сетку там, где нужна старая семантика.

## Safety Model
- Default:
  - `trading.execution.mode=DISABLED`
  - `trading.execution.legacy-enabled=false`
  - `trading.execution.blocked-venues=gate`
- Legacy execution разрешён только если:
  - mode = `LIVE`
  - legacy-enabled = `true`
  - venue явно включён в `live-venues`
- `SHADOW` и `DISABLED` должны оставлять legacy scheduler в passive режиме.
- Заблокированный execution не должен:
  - ставить ордера
  - помечать legacy funding как `executed`
  - silently continue as if trade happened

## Spec-to-Code Mapping
- Spec: execution safety  
  Code: `config.*`, `legacy.execution.*`, `scheduler.OrderExecutorService`, `trading.TestOrderEngine`

- Spec: legacy isolation  
  Code: `api.TelegramBot`, `scheduler.*`, `persistence.service.*`, `trading.*`

- Spec: new domain skeleton  
  Code: `domain.event.*`, `domain.trade.*`, `domain.execution.*`, `domain.profile.*`

- Spec: persistence direction  
  Code: `infrastructure.persistence.model.*`, `infrastructure.persistence.repository.*`

- Spec: controlled new-world entry  
  Code: `application.event.*`, `application.trade.*`, `application.query.*`, `api.FundingEventController`, `api.ArmedTradeController`

## Manual Verification
1. Запустить приложение с дефолтным конфигом.
2. Убедиться в логах, что execution mode = `DISABLED`.
3. Убедиться, что Telegram ingest стартует отдельно от live execution semantics.
4. Вызвать `POST /api/test-orders` и убедиться, что запрос блокируется.
5. Вызвать `POST /api/v1/funding-events` и `POST /api/v1/armed-trades`, затем проверить `GET /api/v1/armed-trades`.
6. При controlled legacy live test включить:
   - `TRADING_EXECUTION_MODE=LIVE`
   - `TRADING_LEGACY_EXECUTION_ENABLED=true`
   - `TRADING_LIVE_VENUES=<venue>`
7. Проверить, что `gate` не становится executable по умолчанию.

## Next Recommended Phase 2 Tasks
- Выделить явный candidate/event ingestion flow между Telegram signals и `FundingEvent`
- Построить real exchange adapter layer под новые `ExecutionPort` / `OrderStatusPort` / `MarketDataPort`
- Ввести outcome measurement и trade journal вокруг нового домена
- Спроектировать и реализовать первый Gate-focused execution path уже через новый домен, а не через legacy scheduler
- Ввести migration strategy для schema evolution
