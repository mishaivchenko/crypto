# API Surface

## Monitor Overview

```text
GET /api/v2/monitor/overview
```

Возвращает summary для UI: version, global venue mode, pending candidates, events, armed trades, venues, credentials status и request timing summary.

## Candidates

```text
GET /api/v1/candidates
GET /api/v1/candidates/{id}
POST /api/v1/candidates/{id}/approve
POST /api/v1/candidates/{id}/reject
DELETE /api/v1/candidates/{id}
```

Фильтры list endpoint:

- `status`
- `venue`
- `symbol`
- `detectedFrom`
- `detectedTo`
- pageable params

`approve` создаёт `FundingEvent`. `reject` и `delete` не создают trade decision.

## Funding Events

```text
GET /api/v1/funding-events
GET /api/v1/funding-events/{id}
POST /api/v1/funding-events
POST /api/v1/funding-events/{id}/arm
GET /api/v1/funding-events/{id}/journal
```

`arm` создаёт `ArmedTrade`.

Funding armed trades поддерживают только `SHORT`.

## Armed Trades

```text
GET    /api/v1/armed-trades
GET    /api/v1/armed-trades/{id}
POST   /api/v1/armed-trades
DELETE /api/v1/armed-trades/{id}
GET    /api/v1/armed-trades/{id}/journal
GET    /api/v1/armed-trades/{id}/order-attempts
GET    /api/v1/order-attempts
```

`DELETE /api/v1/armed-trades/{id}` — отменяет сделку, переводя в `CANCELLED`. Работает только для CANCELLABLE_STATES: `ARMED`, `ENTRY_PENDING`, `ENTRY_ATTEMPTED`, `OPEN`, `EXIT_PENDING`. Возвращает HTTP 204. Пишет `ARMED_TRADE_CANCELLED` в TradeJournal. При недопустимом state — 422.

## Trade Position And Outcome

```text
GET /api/v2/trades/{armedTradeId}/position
GET /api/v2/trades/{armedTradeId}/outcome
```

`GET /api/v2/trades/{armedTradeId}/position` — последняя записанная позиция: `state`, `quantity`, `entryPrice`, `exitPrice`, `openedAt`, `closedAt`. 404 если позиция ещё не записана.

`GET /api/v2/trades/{armedTradeId}/outcome` — последний записанный outcome: `grossPnlUsd`, `netPnlUsd`, `feesUsd`, `outcomeCode`, `evaluatedAt`. 404 если outcome ещё не записан.

Важные поля `ArmedTradeResponse`:

- `fundingEventId`
- `signalCandidateId`
- `venue`
- `symbol`
- `fundingTime`
- `notionalUsd`
- `intendedSide`
- `plannedEntryAt`
- `plannedExitAt`
- `entryAttemptCount`
- `entrySpacingMs`
- `measuredEntryLatencyMs`
- `manualLatencyAdjustmentMs`
- `effectiveEntryLatencyMs`
- `state`

## Venues

```text
GET  /api/v1/venues
GET  /api/v1/venues/access-mode
POST /api/v1/venues/access-mode
GET  /api/v1/venues/{venue}
POST /api/v1/venues/{venue}/sync
POST /api/v1/venues/{venue}/mode
POST /api/v1/venues/{venue}/check
POST /api/v2/monitor/venues/{venue}/latency-probe
GET  /api/v1/venues/{venue}/instruments
GET  /api/v1/venues/timings
```

`POST /api/v2/monitor/venues/{venue}/latency-probe` — выполняет GET к дешёвому публичному endpoint venue (Gate: `/futures/usdt/tickers?contract=BTC_USDT`, Bybit: `/v5/market/time`), измеряет wall-clock RTT, делает upsert `VenueTimingProfileEntity.latencySampledAt`. Возвращает `{ venue, durationMs, sampledAt }`.

`/api/v1/venues/{venue}/check` checks credentials for the current operator and current global access mode.

## Operator Credentials

```text
GET /api/v1/operators/me/credentials
PUT /api/v1/operators/me/credentials/{venue}/{mode}
DELETE /api/v1/operators/me/credentials/{venue}/{mode}
POST /api/v1/operators/me/credentials/{venue}/{mode}/check
```

Rules:

- `X-Operator-Token` is required when operator auth is enabled.
- raw secrets are accepted only on `PUT`.
- responses contain masks/status only, never raw secrets.
- credentials are isolated by `operator_id`.

Supported modes:

- `testnet`
- `production`
- `prod` alias for `production`

## Dev Tools (monitor-side)

```text
POST /api/v2/monitor/dev/engine/run-once
GET  /api/v2/monitor/dev/engine/runtime
POST /api/v2/monitor/dev/engine/runtime
GET  /api/v2/monitor/dev/test-runs/options
POST /api/v2/monitor/dev/test-runs
POST /api/v2/monitor/dev/test-runs/{armedTradeId}/entry
POST /api/v2/monitor/dev/test-runs/{armedTradeId}/exit
```

Dev test run flow используется для testnet validation без полного engine loop.

## Monitor Internal Engine API

```text
GET  /internal/v1/engine/plans
GET  /internal/v1/engine/plans?includeAll=true
GET  /internal/v1/engine/plans/{armedTradeId}
POST /internal/v1/engine/order-attempts
POST /internal/v1/engine/trades/{armedTradeId}/state
POST /internal/v1/engine/positions
POST /internal/v1/engine/outcomes
POST /internal/v1/engine/metrics-snapshot
```

Monitor protects these endpoints with `X-Internal-Token`.

`POST /internal/v1/engine/order-attempts` is idempotent by `attemptKey`.

## Engine

```text
GET  /internal/engine/summary
GET  /internal/engine/plans
GET  /internal/engine/plans/{armedTradeId}
POST /internal/engine/execution/run-once
POST /internal/engine/execution/run-once?force=true
POST /internal/engine/execution/target
GET  /internal/engine/runtime
POST /internal/engine/runtime
```

Live order submission доступен при `ENGINE_LIVE_ORDER_ENABLED=true`. Gate testnet подтверждён; Bybit geo-blocked для UA IPs.
