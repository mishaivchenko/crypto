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

Лёгкий planning runtime.

Отвечает за:

- summary of armed trade plans.
- listing execution plans fetched from monitor.
- future execution loop host near exchanges.

Порт по умолчанию: `8091`.

Engine сейчас read-only относительно execution. Он не размещает live orders.
