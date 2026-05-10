---
title: "Bybit"
type: entity
tags: [venue, bybit, testnet]
sources: []
updated: 2026-05-09
---

# Bybit

## Funding Schedule

- Interval: 8h
- Charge times (UTC): 00:00, 08:00, 16:00
- Predicted rate published ~1h before charge

## Status in Codebase

- Supported: `engine.live-enabled-venues` includes `bybit`
- Testnet available: yes (`engine.trading-venue-access-mode = testnet` by default)
- Adapter: `monitor-app/infrastructure/exchange/BybitFeignClient` (Feign)
- Credential storage: per-operator, AES-GCM encrypted, key `bybit/testnet` and `bybit/production`

## API Notes

**Geo-restriction (confirmed 2026-05-09):** Bybit testnet returns "regulatory restrictions" error for Ukrainian IPs even with valid API key and full trading permissions. Not a credential issue — account-level geo-block. Requires VPN through neutral country (DE/NL/SG) to trade. Bybit skipped for testnet validation; Gate used instead.

## Related

- [[gate]]
- [[funding-mechanics]]
- [[window-timing]]
- [[uainvest-api]]
