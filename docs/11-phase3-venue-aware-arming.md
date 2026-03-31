# Phase 3: Venue-Aware Event Preparation and Armed Trade Workflow

## Что дала Phase 3
Phase 3 перевела систему из состояния:

`Telegram signal -> SignalCandidate -> Review -> FundingEvent`

в состояние:

`Telegram signal -> SignalCandidate -> Review -> FundingEvent -> ArmedTrade -> Trade Journal`

При этом live execution нового домена всё ещё не включён. Система остаётся safe-by-default.

## Основные изменения

### 1. Multi-venue metadata registry
- Добавлен read-side слой venue metadata adapters:
  - `BinanceMetadataAdapter`
  - `BybitMetadataAdapter`
  - `GateMetadataAdapter`
- Добавлен `InstrumentRegistryService`, который:
  - синхронизирует metadata по биржам;
  - хранит инструменты в `instrument_metadata`;
  - даёт canonical symbol -> supported venues mapping;
  - делает старые snapshot-записи inactive, если инструмент пропал.

### 2. Реальное уважение `testnet/production` mode
- Metadata adapters больше не смотрят в несуществующие flat base-url properties.
- Для Binance и Bybit base URL теперь берётся из активного режима:
  - `trading.<venue>.mode`
  - `trading.<venue>.<mode>.base-url`
- Это позволяет реально переключать read-side трафик между test/prod окружениями.

### 3. Startup credential validation
- Добавлен/исправлен startup validator:
  - если `trading.metadata.require-credentials-on-startup=true`,
  - приложение падает при старте, если для enabled venue нет ключей/секретов в активном mode.
- Это сделано специально, чтобы конфигурация 3+ бирж была честной и не создавала ложное ощущение readiness.

### 4. FundingEvent -> ArmedTrade operator flow
- Добавлен новый operator action:
  - `POST /api/v1/funding-events/{id}/arm`
- Он:
  - валидирует статус события;
  - создаёт `ArmedTrade`;
  - переводит `FundingEvent` в `ARMED`;
  - пишет journal entries.

### 5. Trade journal
- Добавлена таблица `trade_journal` и сервис `TradeJournalService`.
- Сейчас journal пишет:
  - `CANDIDATE_APPROVED`
  - `CANDIDATE_REJECTED`
  - `FUNDING_EVENT_CREATED`
  - `FUNDING_EVENT_ARMED`
  - `ARMED_TRADE_CREATED`
- Это пока не execution journal, а журнал операторских решений и переходов состояния.

### 6. Venue diagnostics API
- Добавлены API для диагностики многобиржевого metadata-контура:
  - `GET /api/v1/venues`
  - `GET /api/v1/venues/{venue}`
  - `POST /api/v1/venues/{venue}/sync`
  - `GET /api/v1/venues/{venue}/instruments`
  - `GET /api/v1/venues/timings`
- Через них можно увидеть:
  - активный mode биржи;
  - текущий metadata base URL;
  - настроены ли ключи;
  - сколько инструментов синкануто;
  - среднее и последнее время metadata request-а.

## Что это даёт под будущие 3+ биржи
- Новая биржа добавляется через:
  1. `VenueMetadataPort` adapter,
  2. регистрацию Spring bean,
  3. конфигурацию `trading.<venue>.*`,
  4. контрактные/интеграционные тесты.
- Core domain не пришлось подстраивать под Binance/Bybit/Gate отдельно.
- Telegram ingest, candidate flow и event/trade domain не завязаны на конкретную биржу.

## Safety и границы
- Legacy execution defaults не менялись.
- Новый event/candidate/venue flow не ставит ордера.
- Telegram остался ingest/diagnostic surface.
- REST остаётся единственным местом approve/arm actions.

## Новые полезные точки наблюдаемости
- `VenueRequestTimingService` хранит:
  - число запросов,
  - число успехов/ошибок,
  - среднее время,
  - последнее время,
  - последний HTTP status,
  - размер последнего payload.
- Это пока read-side диагностика, но тот же паттерн дальше переносится в execution plane.

## Что всё ещё не сделано
- live execution через новый домен;
- real order orchestration;
- timing/profiling engine вокруг funding window;
- автоматическое решение `FundingEvent -> ArmedTrade`;
- market-data execution loop;
- полноценный risk engine;
- advanced search/filtering UI beyond REST basics.

## Практический смысл фазы
После Phase 3 репозиторий уже не просто хранит reviewed events, а умеет:
- нормализовать symbols через venue-aware registry;
- готовить события под несколько бирж;
- вручную arm'ить сделки;
- вести журнал решений;
- диагностировать, куда именно ходят metadata requests и насколько быстро это происходит.
