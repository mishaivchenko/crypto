---
title: "Funding Mechanics"
type: concept
tags: [funding, perpetuals, mechanics]
sources: []
updated: 2026-05-09
---

# Funding Mechanics

_Stub — expand on first relevant ingest._

Perpetual futures maintain price peg to spot via periodic funding payments between longs and shorts.

## Payment Direction

- Positive rate → longs pay shorts
- Negative rate → shorts pay longs

Scalping target: enter SHORT when rate is significantly positive → collect funding → exit after charge.

## Cycle Schedules

| Venue | Default Interval | Charge Times (UTC) |
|-------|-----------------|-------------------|
| Bybit | 8h | 00:00, 08:00, 16:00 |
| Gate | 8h | 00:00, 08:00, 16:00 |
| OKX | 8h (variable) | 00:00, 08:00, 16:00 |
| Bitget | 8h | 00:00, 08:00, 16:00 |
| KuCoin | 8h | 00:00, 08:00, 16:00 |

## Rate Formula

Rate = clamp(premium_index + clamp(interest_rate − premium_index, −0.05%, +0.05%), −0.75%, +0.75%)

Exact formula varies by venue. Predicted rate is published before the window; realized rate is locked at charge time.

## Related

- [[window-timing]]
- [[signal-quality]]
- [[execution-strategy]]
