# Safety And Legacy

## Safety Defaults

```env
TRADING_EXECUTION_MODE=DISABLED
TRADING_LEGACY_EXECUTION_ENABLED=false
TRADING_LIVE_VENUES=
TRADING_BLOCKED_VENUES=gate
```

Default runtime must not place live orders.

## Execution Modes

- `DISABLED` — no order placement.
- `SHADOW` — diagnostics and calculations only.
- `LIVE` — explicit opt-in only, venue allowlist required.

## Funding Trade Direction

Funding `ArmedTrade` is `SHORT-only`.

`LONG` is rejected in:

- domain invariant.
- command service.
- API integration tests.
- monitor UI arm form.

## Legacy Still Present

Some old classes remain because they are still used by tests or transitional endpoints:

- legacy scheduler packages.
- `ApprovedFundingEntity`.
- `OrderExecutorService`.
- `TestOrderEngine`.
- `/api/test-orders`.
- optional Telegram bot UI.

They are not the target product architecture.

## Legacy Not Product

Do not build new business flow on:

- `ApprovedFundingEntity`.
- legacy funding approval.
- legacy scheduler execution.
- Telegram bot action trees.
- Binance-first assumptions.

## Safe Use Of Test Orders

The old test-order endpoint can record timing if explicitly enabled, but it remains guarded. It is not the production execution API.

Future execution should use the new domain:

`FundingEvent -> ArmedTrade -> Engine Plan -> ExecutionPort -> OrderAttempt -> Position -> TradeOutcome`

