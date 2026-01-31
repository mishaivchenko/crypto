# Funding MVP

## Goal
Deliver a minimal, end‑to‑end funding-arbitrage pipeline: ingest funding signals, approve targets, schedule and fire exchange orders just-in-time, and expose basic visibility.

## Completed
- ~~Persist approved fundings (symbol, exchanges, USDT, nextFundingAt) in SQLite with lifecycle flags (active/executed).~~
- ~~Scheduler picks nearest active funding, respects latency buffer, and triggers execution window.~~
- ~~Executor fetches live funding data & symbol rules, validates min notional/qty, and sends market test orders.~~
- ~~Integration safety: WireMock-backed e2e test from DB approval through scheduler to Bybit REST stubs.~~

## Next (priority order)
1) Add REST/Telegram command to list recent execution results (id, symbol, exchange, status, timestamps).  
2) Persist latency measurements per exchange over time; surface in logs/metrics to tune scheduling.  
3) Implement real order path toggle (test vs live) with per-exchange env flags and guardrails.  
4) Enrich funding refresher with retry/backoff and per-exchange fallbacks (REST + WS).  
5) UI: simple dashboard card showing watchlist funding rates, approved items, and next execution ETA.  
6) Alerting: Telegram/Slack notification on missed/failed executions and when funding data is stale.  
7) Hardening: property-driven integration tests for each exchange client covering error codes & signatures.  
8) CI: publish test coverage and run security scan (`./gradlew security`) on PRs.  
