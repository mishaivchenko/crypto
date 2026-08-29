<div align="center">

# FUND `2.0.0`

operator desk for the 1–5s window **before** funding

[monitor](https://crypto-monitor.org) · [docs](docs/README.md) · [status](docs/PROJECT_STATUS.md) · [bot](https://t.me/funding_arbitrage_bot_bot)

[![ci](https://img.shields.io/github/actions/workflow-status/mishaivchenko/crypto/ci-cd.yml?style=for-the-badge&label=CI)](https://github.com/mishaivchenko/crypto/actions)
[![jdk](https://img.shields.io/badge/JDK-25-111827?style=for-the-badge)](#)
[![live](https://img.shields.io/badge/LIVE_ORDERS-OFF-b91c1c?style=for-the-badge)](#status)
[![stg](https://img.shields.io/badge/STAGING-UP-059669?style=for-the-badge)](https://crypto-monitor.org)
[![cap](https://img.shields.io/badge/REAL_CAPITAL-NO-6b7280?style=for-the-badge)](#status)

</div>

---

## status

| plane | where | state |
| --- | --- | --- |
| **monitor** `:8090` | [crypto-monitor.org](https://crypto-monitor.org) · Mac mini + Cloudflare Tunnel | `UP` · `prod-like` |
| **engine** `:8091` | same host as monitor | `UP` · loop **off** · live orders **off** |
| **telegram** `:8092` | `@funding_arbitrage_bot_bot` | `UP` · `staging` |
| **prod / SG** | not deployed | `NO` |
| **real size** | — | `NO` |

```
Phase 0 ██████████░░░░  foundation
Phase 1 ░░░░░░░░░░░░░░  harden
Phase 2 ░░░░░░░░░░░░░░  go-live SG + capital
```

MVP 1–6 done. Last confirmed live fill: **Gate testnet · ACT/USDT SHORT · 2026-05-09**.

---

## can / cannot

```mermaid
flowchart LR
  subgraph CAN["can"]
    A[ingest funding API]
    B[review + arm]
    C[AI GO / WATCH / PASS]
    D[burst SHORT]
    E[latency calibrate]
    F[PnL journal]
    G[5 venues wired]
  end
  subgraph CANT["cannot yet"]
    X[autonomous prod loop]
    Y[Singapore engine]
    Z[real capital]
  end
  CAN -.-> CANT
```

| capability | label |
| --- | --- |
| signal ingest + dedupe | `LIVE` |
| operator review / reject | `LIVE` |
| arm SHORT + burst entry | `LIVE` |
| DeepSeek advisor | `LIVE` |
| liquidity score | `LIVE` |
| cancel from any cancellable state | `LIVE` |
| position → exit → outcome | `LIVE` |
| trade history UI | `LIVE` |
| venue diagnostics + p50/p95/p99 | `LIVE` |
| Gate testnet fill | `PROVEN` |
| Bybit from UA | `VPN` |
| engine loop on prod | `LOCKED` |
| live orders on prod | `LOCKED` |
| SG co-location | `PLANNED` |

---

## loop

```mermaid
stateDiagram-v2
  [*] --> Candidate: funding API
  Candidate --> Dead: reject
  Candidate --> Event: approve
  Event --> Armed: operator ARM
  Armed --> Entry: burst SHORT
  Entry --> Open: fill
  Open --> Closed: flatten
  Armed --> Cancelled: cancel
  Closed --> [*]
```

Thesis: **SHORT 1–5s before the tick, ride the dump, flatten. Do not collect the coupon.** Human ARM is required.

---

## topology

```mermaid
flowchart TB
  SRC["uainvest funding API"] --> MON
  DS["DeepSeek"] -.-> MON
  subgraph STAGING["staging · Mac mini"]
    MON["monitor-app :8090<br/>control + UI + SQLite"]
    ENG["engine-app :8091<br/>plans + orders"]
    TG["telegram-bot :8092"]
    MON <-- internal token --> ENG
    TG --> MON
  end
  CF["crypto-monitor.org"] --> MON
  ENG --> V["Gate · Bybit · OKX · Bitget · KuCoin"]
  subgraph NEXT["phase 2"]
    SG["engine next to venues"]
  end
  MON -.-> SG
```

| module | job |
| --- | --- |
| `platform-core` | domain only |
| `monitor-app` | queue, arm, creds, UI |
| `engine-app` | fire · cannot mutate event/trade |
| `telegram-bot-app` | alerts |

---

## venues

| | creds | meta | orders | note |
| --- | :---: | :---: | :---: | --- |
| **Gate** | ● | ● | ● | testnet SHORT filled |
| **Bybit** | ● | ● | ● | UA geo-block |
| **OKX** | ● | ● | ● | `x-simulated-trading: 1` |
| **Bitget** | ● | ● | ● | passphrase |
| **KuCoin** | ● | ● | ● | passphrase |

wired ≠ edge.

---

## kill switches

```mermaid
flowchart LR
  L[local-safe] -->|auth + creds| S[staging]
  S -->|still locked| P[prod-like]
  P -->|two ENV flags| LIVE[live fire]
```

| flag | default |
| --- | --- |
| `ENGINE_EXECUTION_LOOP_ENABLED` | `false` |
| `ENGINE_LIVE_ORDER_ENABLED` | `false` |
| max concurrent armed | `3` |
| side | `SHORT` only |

Local `bootRun*` is dead on exchanges.

---

## run

```bash
./gradlew bootRunMonitor   # :8090
./gradlew bootRunEngine    # :8091
```

```bash
cp deploy/.env.example .env && docker compose up --build
```

JDK 25. Commands → [`AGENTS.md`](AGENTS.md)

---

## map

| want | go |
| --- | --- |
| now | [docs/00-current-state.md](docs/00-current-state.md) |
| phases | [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md) |
| safety | [docs/08-safety.md](docs/08-safety.md) |
| runbook | [docs/07-runbook.md](docs/07-runbook.md) |
| API | [docs/04-api-surface.md](docs/04-api-surface.md) |
| engine spec | [docs/engine-tdd/](docs/engine-tdd/) |

Private desk. Staging is on. Capital is not.
