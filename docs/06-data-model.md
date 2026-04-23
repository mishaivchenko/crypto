# Data Model

## Core Tables

### `signal_candidate`

Кандидат из Funding API.

Ключевые поля:

- source type / source ids.
- raw symbol.
- normalized symbol.
- source venue.
- venue hints.
- detected at.
- status.
- review decision / notes.
- normalization failure reason.
- source funding time.
- source funding rate.
- funding event link.

### `funding_event`

Подтверждённое событие фандинга.

Ключевые поля:

- venue.
- symbol.
- funding time.
- funding rate.
- status.
- source type/ref.
- signal candidate id.
- discovered at.

### `armed_trade`

Подготовленная funding-сделка.

Ключевые поля:

- funding event id.
- notional USD.
- intended side.
- planned entry / exit.
- armed at.
- entry / exit lead.
- entry attempt count.
- entry spacing ms.
- measured entry latency ms.
- manual latency adjustment ms.
- effective entry latency ms.
- arm source.
- state.
- notes.

Funding `armed_trade` должен быть `SHORT`.

### `trade_journal`

История решений и state transitions.

Ключевые поля:

- entity type.
- entity id.
- event code.
- old state.
- new state.
- actor type.
- actor ref.
- note.
- created at.

### `instrument_metadata`

Venue-specific instrument registry.

Ключевые поля:

- venue.
- canonical symbol.
- venue symbol.
- base asset.
- quote asset.
- instrument type.
- status.
- min order qty.
- qty step.
- min notional value.
- quantity precision.
- active flag.
- last synced at.

### `operator_account`

Оператор приложения.

Ключевые поля:

- username.
- token hash.
- enabled flag.

Raw token в базе не хранится.

### `operator_exchange_credential`

Зашифрованные exchange keys оператора.

Ключевые поля:

- operator id.
- venue.
- access mode.
- encrypted api key / secret / passphrase.
- masked key fields for UI.
- connection status / message.
- last checked at.

Raw secrets не возвращаются через API.

## Removed Tables

Старые таблицы могут физически остаться в локальной SQLite после предыдущих запусков, но код больше не имеет entity/repository mappings к ним:

- `approved_funding`
- `approved_funding_exchange`
- `order_execution_time`

## SQLite Notes

SQLite остаётся текущей локальной persistence базой.

Важно:

- schema теперь versioned через Flyway migration `V1__baseline.sql`;
- `monitor-app` запускает Flyway перед JPA `validate`, а не через Hibernate `ddl-auto=update`;
- существующие SQLite базы из pre-Flyway эпохи берутся под управление через baseline history table;
- application/domain слой по-прежнему задаёт defaults и invariants.
