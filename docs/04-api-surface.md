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
GET /api/v1/armed-trades
GET /api/v1/armed-trades/{id}
POST /api/v1/armed-trades
GET /api/v1/armed-trades/{id}/journal
GET /api/v1/armed-trades/{id}/order-attempts
GET /api/v1/order-attempts
```

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
GET /api/v1/venues
GET /api/v1/venues/access-mode
POST /api/v1/venues/access-mode
GET /api/v1/venues/{venue}
POST /api/v1/venues/{venue}/sync
POST /api/v1/venues/{venue}/mode
POST /api/v1/venues/{venue}/check
GET /api/v1/venues/{venue}/instruments
GET /api/v1/venues/timings
```

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

## Monitor Internal Engine API

```text
GET /internal/v1/engine/plans
GET /internal/v1/engine/plans?includeAll=true
GET /internal/v1/engine/plans/{armedTradeId}
POST /internal/v1/engine/order-attempts
```

Monitor protects these endpoints with `X-Internal-Token`.

`POST /internal/v1/engine/order-attempts` is idempotent by `attemptKey`.

## Engine

```text
GET /internal/engine/summary
GET /internal/engine/plans
GET /internal/engine/plans/{armedTradeId}
POST /internal/engine/execution/run-once
POST /internal/engine/execution/run-once?force=true
```

Engine endpoints сейчас выполняют observable execution attempts. Без engine credentials попытки пишутся как `FAILED`; live order HTTP submission ещё guarded.
