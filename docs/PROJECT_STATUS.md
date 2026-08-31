# Project Status

_Updated: 2026-08-31 (Phase 0 closure)_

## Where the project is

Modular monolith (Java 25, Spring Boot 3.5.14): `monitor-app` (8090), `engine-app` (8091), `telegram-bot-app` (8092), `platform-core`.

- **MVP Steps 1-6 complete**: execution ports, engine loop, latency calibration, position/exit lifecycle, trade outcome, and risk guardrails.
- **Live order submission works**: Gate testnet confirmed (ACT/USDT SHORT FILLED 2026-05-09). All 5 venues are implemented.
- **AI Signal Advisor**, **Telegram bot**, **Trade History UI**, and **Unified Settings** are shipped.
- **Staging deployment live** on Mac mini via Cloudflare Tunnel (crypto-monitor.org). monitor/engine run `prod-like`; telegram-bot runs `staging`.
- **Quality foundation implemented**: `quality`, `qualityReport`, `build`, and branch-protection checks are the active verification path.
- **Engine TDD foundation implemented**: `engineTddGate` and `engineTddDocsCheck` guard engine coverage, mutation, and requirement mapping.

## Source Of Truth

GitHub Issues and milestones are the authoritative task tracker. GitHub Projects/boards may be used as optional views, but they are not the source of truth.

Legacy local trackers (`tasks/`, `BACKLOG.md`) are removed; history remains available through git.

## Active Work

- **Phase 0 — Foundation Restoration**: closing/complete after the Phase 0 closure PR passes gates and receives human approval.
- **Phase 1 — Production Hardening**: next active milestone after Phase 0 approval.

## Phase Map

| Phase | Focus | Status |
|-------|-------|--------|
| 0 | Foundation restoration: tracker, docs/status, stale clutter cleanup, quality/TDD verification | **closing / complete** |
| 1 | Production hardening: secrets, Telegram prod-like, observability/logs, SQLite lock validation, autonomous loop testing | **next / active** |
| 2 | Go-live: VPS/Singapore production deployment, real-capital rollout, tag-based releases | planned |

## Definition Of Done Gates

- `./gradlew quality`
- `./gradlew qualityReport`
- `./gradlew build`
- `./gradlew engineTddGate`

## Phase 0 Closure Audit

- Phase 0 milestone contains only issue `#185`.
- Phase 1 milestone contains `#172`, `#174`, `#175`, `#176`, and `#177`.
- Phase 2 milestone contains `#171` and `#173`.
- Issues `#170`, `#136`, and `#140` are closed.
- Old empty sprint milestones are closed.
- `main` is protected: PR required, admins enforced, and required checks are Build & Test, Code Quality, and Engine TDD Gate.
