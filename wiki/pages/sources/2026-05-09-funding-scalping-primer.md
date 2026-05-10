---
title: "Funding Rate Scalping — Primer"
type: source
tags: [funding, scalping, venues, primer]
sources: [2026-05-09-funding-scalping-primer.md]
updated: 2026-05-09
---

# Funding Rate Scalping — Primer

**Source**: `raw/2026-05-09-funding-scalping-primer.md`
**Type**: Internal synthesis / domain primer

## Key Takeaways

1. **Core mechanic**: SHORT entry before 8h funding charge, exit after. Net = yield − fees − slippage.
2. **Rate threshold**: profitable only when rate > ~2× taker fee round-trip (e.g. > 0.15% for 0.06% fee venues).
3. **Timing is the hard part**: enter too early = price risk; too late = miss charge. Latency compensation is critical.
4. **uainvest API**: provides predicted rates but does not separate predicted vs. realized — rate collapse risk is not visible from this source alone.
5. **OKX variable intervals**: can switch to 1h/4h, which changes profitability calculus significantly.

## Contradictions / Open Questions

- Exact taker fee per venue needs confirmation from official API docs (numbers in primer are approximations)
- uainvest API field names unconfirmed — need live response sample

## Pages Updated

- [[funding-mechanics]] — rate formula, payment direction, cycle schedules
- [[window-timing]] — entry/exit logic, latency compensation
- [[signal-quality]] — rate threshold, rate collapse risk
- [[execution-strategy]] — burst entry, notional sizing
- [[bybit]] — fee note
- [[gate]] — fee note
- [[okx]] — variable interval risk noted
- [[uainvest-api]] — rate collapse risk noted
