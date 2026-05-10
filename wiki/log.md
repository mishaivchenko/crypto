# Wiki Log — funding-window-scalping

_Append-only. Parseable: `grep "^## \[" log.md | tail -10`_

---

## [2026-05-09] milestone | First testnet order FILLED on Gate

Full entry+exit cycle completed on Gate testnet. Symbol: ACT/USDT SHORT, qty=31, fill=$0.0159, externalOrderId=195625108917026449. ArmedTradeId=20, both entry and exit FILLED. Pipeline validated: DevTestRun → EngineExecutionService → LiveExchangeExecutionPort.submitGate() → FILLED OrderAttempt. Bybit blocked by geo-restriction (Ukrainian IP). Branch: claude/plan.

---

## [2026-05-09] ingest | Funding Rate Scalping — Primer

Source: `raw/2026-05-09-funding-scalping-primer.md`. Pages touched: funding-mechanics, window-timing, signal-quality, execution-strategy, bybit, gate, okx, uainvest-api. Key addition: rate collapse risk on uainvest API, OKX variable interval flag, fee thresholds.

---

## [2026-05-09] init | Wiki initialized

Schema, index, log, and stub pages created. Domain: funding rate scalping across Bybit/Gate/OKX/Bitget/KuCoin. No sources ingested yet.
