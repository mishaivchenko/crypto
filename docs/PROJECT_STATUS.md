# Project Status

_Updated: 2026-08-22 (Phase 0 — Foundation Restoration)_

## Where the project is

Modular monolith (Java 25, Spring Boot 3.5.14): `monitor-app` (8090), `engine-app` (8091), `telegram-bot-app` (8092), `platform-core`.

- **MVP Steps 1-6 complete** (execution ports, engine loop, latency calibration, position/exit lifecycle, trade outcome, risk guardrails).
- **Live order submission works** — Gate testnet confirmed (ACT/USDT SHORT FILLED 2026-05-09). All 5 venues implemented.
- **AI Signal Advisor**, **Telegram bot**, **Trade History UI**, **Unified Settings** shipped.
- **Staging deployment live** on Mac mini via Cloudflare Tunnel (crypto-monitor.org). monitor/engine run `prod-like`, telegram-bot runs `staging`.
- **Phase 0 (this)**: tracker migration, docs revision, cleanup.

## Active work

- Milestone: **Phase 0 — Foundation Restoration** ([issues](https://github.com/mishaivchenko/crypto/issues?q=milestone%3A%22Phase+0+%E2%80%94+Foundation+Restoration%22))

## Tracked work (issues)

- Open bugs/features tracked as GitHub issues (see link above).
- Legacy `tasks/` and `BACKLOG.md` removed in Phase 0 — history preserved in git.

## Phase map

| Phase | Focus | Status |
|-------|-------|--------|
| 0 | Foundation restoration (tracker, docs, cleanup) | **in progress** |
| 1 | Production hardening: secrets, telegram prod-like, observability/logs, SQLite lock | planned |
| 2 | Go-live: real capital, engine in Singapore, tag-based releases | planned |
