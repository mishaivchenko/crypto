# Funding Rate Scalping — Primer

_Source: internal synthesis based on codebase analysis + domain knowledge. Ingested 2026-05-09._

## What is Funding Rate Scalping

Perpetual futures use a funding mechanism to keep contract price anchored to spot. Longs pay shorts (or vice versa) at fixed intervals. When the rate is high enough, you can:

1. Enter SHORT just before the charge
2. Collect the funding payment
3. Exit after the charge

Net P&L = funding_yield − round_trip_fees − slippage

## Why It Works

Market participants are long-biased in bull markets. Perp price drifts above spot. Funding rate goes positive to incentivize shorts. The rate can spike to 0.1%–0.5% per 8h window on volatile assets. Even modest rates exceed fee costs on liquid instruments.

## Critical Parameters

- **Rate threshold**: typically 2–3× round-trip cost (0.12% for 0.06% taker fee venues)
- **Entry timing**: must arrive before charge minus latency; too early = price risk
- **Notional**: limited by liquidity at charge time, liquidation distance, and capital efficiency
- **Instrument liquidity**: thinly traded assets have wide spreads near funding time

## Venue Comparison (8h cycle)

| Venue | Fee (taker) | Testnet | Notes |
|-------|------------|---------|-------|
| Bybit | ~0.06% | Yes | Most liquid perp market |
| Gate | ~0.075% | Yes | Good alt-coin coverage |
| OKX | ~0.05% | No (prod only for scalping) | Variable intervals possible |
| Bitget | ~0.06% | Yes | Less liquid |
| KuCoin | ~0.06% | Yes | Less liquid |

## Key Risks

1. **Rate collapse**: predicted rate published, but realized rate at T-0 drops significantly
2. **Price move**: entering early and price gaps against position
3. **Partial fill**: burst of orders not fully filled before charge
4. **Liquidation**: leverage too high + adverse move before fill

## Source Quality

uainvest.com.ua API provides predicted rates sorted by magnitude. Useful for discovering candidates but:
- Does not distinguish predicted vs. realized rate
- Symbol normalization needed (raw strings differ by venue)
- Polling interval matters — stale data near charge time is dangerous
