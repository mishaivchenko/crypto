# System Flow

## Основной flow

1. `FundingApiCandidateSourceService` читает внешний funding API.
2. Записи фильтруются по enabled venues.
3. `InstrumentRegistryService` помогает нормализовать symbol и venue mapping.
4. `SignalCandidateIngestService` создаёт или обновляет `SignalCandidate`.
5. Оператор в `monitor-app` просматривает candidates.
6. Approve создаёт `FundingEvent`.
7. Reject/Delete меняет только candidate pipeline и не создаёт trade decision.
8. Оператор arm-ит `FundingEvent`.
9. `ArmedTradeCommandService` создаёт `ArmedTrade`.
10. `engine-app` строит execution plan для prepared trade.
11. `TradeJournalService` хранит историю state decisions.

## Разделение смыслов

- `SignalCandidate` — внешний сигнал или наблюдение, не trading decision.
- `FundingEvent` — подтверждённое событие фандинга.
- `ArmedTrade` — подготовленная SHORT-сделка вокруг события.
- `EngineExecutionPlan` — read-side план того, когда engine должен действовать.
- `TradeJournal` — журнал операторских и системных переходов.

## Почему это важно

Система намеренно не превращает сигнал сразу в сделку. Между ingestion и trade preparation есть ручной review, чтобы оператор видел источник, venue, symbol, funding time и риск ошибки нормализации.

## Последние изменения

- Добавлен burst-entry план: несколько входов с configurable spacing.
- `LONG` запрещён для funding armed trades.
- Entry trigger учитывает measured latency и manual latency adjustment.
- SQLite schema совместима со старыми базами: новые burst-колонки nullable на DDL-уровне, defaults задаются в домене и service layer.

