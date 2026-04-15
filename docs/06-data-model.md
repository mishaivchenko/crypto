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

## Legacy Tables

Эти таблицы могут существовать в базе, но не являются будущим core:

- `approved_funding`
- `approved_funding_exchange`
- `order_execution_time`

Их нельзя использовать как основу нового funding-event execution flow.

## SQLite Notes

SQLite остаётся текущей локальной persistence базой.

Важно:

- новые nullable-колонки безопаснее для schema evolution через Hibernate `ddl-auto=update`;
- application/domain слой задаёт defaults и invariants;
- production migration strategy через Flyway/Liquibase ещё не введена.

