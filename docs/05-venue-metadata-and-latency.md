# Venue Metadata And Latency

## Venue Metadata Registry

Metadata registry нужен, чтобы parser/source не решал instrument identity сам.

Он хранит:

- canonical symbol.
- venue symbol.
- base/quote assets.
- instrument type.
- trading status.
- min qty / qty step / min notional.
- last sync time.

Product venues:

- Bybit.
- Gate.
- Bitget.
- OKX.
- KuCoin.

## Sync

Синхронизация запускается:

- при старте monitor, если `TRADING_METADATA_SYNC_ON_STARTUP=true`;
- вручную через `POST /api/v1/venues/{venue}/sync`;
- по расписанию, если включить `TRADING_METADATA_SCHEDULE_ENABLED=true`.

## Credentials Status

UI показывает:

- configured mode.
- credentials configured.
- api key loaded.
- secret key loaded.
- passphrase loaded.
- connection status.
- last check result.

Если ключей нет, venue должен быть честно отображён как not connected.

## Request Timing

`VenueRequestTimingService` собирает:

- requests.
- successes.
- failures.
- average duration.
- last duration.
- last HTTP status.
- last error.
- last payload size.

Timing пишет metadata sync и credential check. В следующей execution-фазе сюда добавятся order submit/ack/fill timings из `engine-app`.

## Entry Latency

`VenueLatencyService` выбирает measured latency по приоритету:

1. future `order-submit` / `order-ack`
2. `credential-check`
3. `metadata-sync`

Effective latency:

```text
max(0, measuredEntryLatencyMs + manualLatencyAdjustmentMs)
```

Engine использует effective latency, чтобы сдвинуть entry trigger раньше `plannedEntryAt`.

## Burst Entry

`ArmedTrade` поддерживает:

- `entryAttemptCount`
- `entrySpacingMs`
- `measuredEntryLatencyMs`
- `manualLatencyAdjustmentMs`
- `effectiveEntryLatencyMs`

Пример:

- planned entry: `12:00:00.000`
- attempts: `3`
- spacing: `150 ms`
- effective latency: `40 ms`

Engine построит triggers:

- attempt 1 target `12:00:00.000`, trigger `11:59:59.960`
- attempt 2 target `12:00:00.150`, trigger `12:00:00.110`
- attempt 3 target `12:00:00.300`, trigger `12:00:00.260`
