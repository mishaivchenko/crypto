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

## Engine

```text
GET /internal/engine/summary
GET /internal/engine/plans
GET /internal/engine/plans/{armedTradeId}
```

Engine endpoints сейчас internal/read-side. Они не размещают orders.

