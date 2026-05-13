# Modules

## `platform-core`

Содержит общие contracts/domain pieces:

- domain models: candidate, event, trade, execution, venue.
- shared application ports.
- engine plan contracts.
- symbol and crypto utilities.

`platform-core` не является Spring Boot runtime и не владеет persistence.

## `monitor-app`

Основной operator UI и control plane.

Отвечает за:

- Dashboard.
- Candidates review.
- Funding Events.
- Armed Trades.
- Venue Diagnostics.
- Journal visibility.
- Operator credential management.
- Internal engine plan API.
- Static UI assets.

Порт по умолчанию: `8090`.

## `engine-app`

Лёгкий execution runtime.

Отвечает за:

- summary of armed trade plans.
- listing execution plans fetched from monitor.
- live order submission через `LiveExchangeExecutionPort` (Gate и Bybit адаптеры).
- recording `OrderAttempt` results back to monitor.
- engine execution loop (disabled by default).

Порт по умолчанию: `8091`.

Engine может размещать live orders, если `ENGINE_LIVE_ORDER_ENABLED=true`. По умолчанию отключено — safe-by-default.
