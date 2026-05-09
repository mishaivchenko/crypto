---
title: "OKX"
type: entity
tags: [venue, okx]
sources: []
updated: 2026-05-09
---

# OKX

## Funding Schedule

- Interval: variable (8h default, can switch to 1h or 4h for high-volatility instruments)
- Charge times (UTC): 00:00, 08:00, 16:00 (for 8h schedule)

## Status in Codebase

- Adapter present: `monitor-app/infrastructure/exchange/OkxFeignClient`
- Not in `engine.live-enabled-venues` by default

## Variable Schedule Note

OKX can change funding interval dynamically. Signal quality check must account for this — a 1h interval means lower rate threshold needed for profitability.

## Related

- [[funding-mechanics]]
- [[bybit]]
- [[uainvest-api]]
