---
title: "Gate.io"
type: entity
tags: [venue, gate, testnet]
sources: []
updated: 2026-05-09
---

# Gate.io

## Funding Schedule

- Interval: 8h
- Charge times (UTC): 00:00, 08:00, 16:00

## Status in Codebase

- Supported: `engine.live-enabled-venues` includes `gate`
- Testnet available: yes
- Adapter: `engine-app/src/main/java/com/crypto/funding/engine/exchange/LiveExchangeExecutionPort.java` (submitGate)
- **Testnet validated 2026-05-09**: full entry+exit cycle FILLED, externalOrderId=`195625108917026449`

## Testnet Order Details (2026-05-09)

| Field | Value |
|-------|-------|
| Symbol | ACT/USDT |
| Side | SHORT |
| Quantity | 31 contracts |
| Fill price | $0.0159 |
| Notional | ~$5 |
| External order ID | `195625108917026449` |
| Entry status | FILLED |
| Exit status | FILLED |

## Related

- [[bybit]]
- [[funding-mechanics]]
- [[uainvest-api]]
