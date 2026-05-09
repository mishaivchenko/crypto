---
title: "Execution Strategy"
type: concept
tags: [execution, entry-burst, notional, spread]
sources: []
updated: 2026-05-09
---

# Execution Strategy

_Stub — expand on first relevant ingest._

## Entry Burst

Instead of one order, send N orders spaced by `entrySpacingMs`. Reasons:
- Partial fills are common near charge time (liquidity thins)
- Spreading attempts reduces slippage on larger notionals
- Burst count + spacing are operator-configurable per `ArmedTrade`

## Notional Sizing

Current max: `engine.max-notional-usd = 25` (hardcoded safety cap).

Sizing considerations:
- Larger notional → more funding collected, but more spread cost and liquidation risk
- Testnet mode: notional is safe to set higher for calibration

## Order Types

SHORT-side entry. Codebase currently supports:
- Limit and market orders (venue-dependent)
- `CredentialAwareExecutionPort` submits or records `FAILED` if credentials absent

## P&L Estimate

`net = funding_yield − (2 × taker_fee) − spread_cost − slippage`

## Codebase Mapping

- `ArmedTrade`: `entryBurstCount`, `entrySpacingMs`, `notionalUsd`, `intendedSide` (SHORT only)
- `EngineExecutionService`: evaluates timing, submits attempts
- `OrderAttempt`: records each attempt result (SUBMITTED/FAILED/FILLED)

## Related

- [[window-timing]]
- [[signal-quality]]
- [[funding-mechanics]]
