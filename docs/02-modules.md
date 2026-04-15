# Modules

## `platform-core`

Содержит общую бизнес-основу:

- domain models: candidate, event, trade, execution, venue.
- application services: ingest, review, event command/query, armed trade command/query, journal, venue diagnostics.
- persistence: SQLite/JPA entities и repositories.
- REST API для monitor/control plane.
- safety guards и execution mode config.
- Funding API candidate source.

## `monitor-app`

Основной operator UI и control plane.

Отвечает за:

- Dashboard.
- Candidates review.
- Funding Events.
- Armed Trades.
- Venue Diagnostics.
- Journal visibility.
- Static UI assets.

Порт по умолчанию: `8090`.

## `engine-app`

Лёгкий planning runtime.

Отвечает за:

- summary of armed trade plans.
- listing engine execution plans.
- planning entry/exit windows.
- burst-entry plan generation.
- latency-aware trigger time calculation.

Порт по умолчанию: `8091`.

Engine сейчас read/planning-only. Он не размещает live orders.

## Что не является целевой архитектурой

- Legacy `ApprovedFundingEntity` flow.
- Legacy scheduler execution.
- `/api/test-orders` как production execution API.
- Telegram bot как trading control plane.
- TDLib ingestion.
- Binance-first assumptions.

