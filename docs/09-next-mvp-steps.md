# Next MVP Steps

## Goal

Довести текущую 2.0.0 базу до MVP, где system can prepare, execute, observe and measure SHORT funding-event trades on 3+ venues.

## Step 1: Execution Ports Become Real

- Implement venue-specific execution adapters for selected venues.
- Start with one or two venues, but keep the adapter contract identical.
- Place orders only through new domain, not legacy scheduler.
- Persist `OrderAttempt`.

## Step 2: Engine Runtime Loop

- Engine polls actionable `ArmedTrade`.
- Engine uses `EngineExecutionPlan.entryAttempts`.
- For each attempt, engine places SHORT entry orders according to trigger time.
- Spacing supports sequential or parallel burst behavior.
- Engine records submit/ack/fill timestamps.

## Step 3: Latency Calibration

- Add safe latency probe command.
- Measure p50/p95/p99 per venue and operation.
- Allow manual override per venue.
- Surface measured/effective latency in UI.

## Step 4: Position And Exit

- Create `Position` from filled entry.
- Schedule exit around funding window.
- Persist exit `OrderAttempt`.
- Close `Position`.

## Step 5: Trade Outcome

- Calculate gross/net PnL.
- Store fees and slippage.
- Measure whether funding-event movement was captured.
- Feed result into trade history.

## Step 6: Risk Guardrails

- Max notional per venue.
- Max concurrent armed trades.
- Max attempts per event.
- Venue disabled flag.
- Kill switch.
- Refuse execution if latency profile is stale.

## Step 7: Production Deployment Shape

- Monitor/control plane can stay outside Singapore.
- Engine should run close to exchanges, likely Singapore.
- Secrets come from ENV/secret manager.
- Engine must fail fast if required credentials are absent.
- Observability should include request timing, order timing, fills and state transitions.

## Non-Goals For Immediate MVP

- Multi-user auth.
- Strategy marketplace.
- Complex PnL dashboard.
- Auto-selection without operator approval.
- Full microservice split before execution loop is proven.

