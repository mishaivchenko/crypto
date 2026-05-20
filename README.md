# Funding Platform

A modular, operator-controlled platform for funding arbitrage — signal ingestion, manual review, trade arming, and autonomous execution against perpetual futures venues.

```
Funding API → SignalCandidate → Operator Review → FundingEvent
  → ArmedTrade → Engine Plan → OrderAttempt → TradeJournal
```

---

## Modules

| Module | Runtime | Role |
|---|---|---|
| `platform-core` | Library | Domain records, ports, engine contracts, utilities — no Spring, no persistence |
| `monitor-app` | Spring Boot `:8090` | Operator control plane: review, arm, credentials, venue diagnostics, UI |
| `engine-app` | Spring Boot `:8091` | Execution runtime: reads plans from monitor, submits orders, records results |
| `telegram-bot-app` | Spring Boot | Signal notifications and status bot (`@funding_arbitrage_bot_bot`) |

---

## How It Works

**Signal ingestion** — `monitor-app` polls an external funding API, normalises symbols via the venue metadata registry, and creates `SignalCandidate` records.

**Operator review** — candidates land in the review queue. Approve creates a `FundingEvent`; reject/delete stays in the candidate pipeline. No trade is created without explicit approval.

**Trade arming** — the operator arms a `FundingEvent`, producing an `ArmedTrade`. All funding trades are `SHORT`-only. Burst-entry parameters (attempt count, spacing, latency offset) are configured per trade.

**Execution** — `engine-app` fetches the execution plan from monitor, computes per-attempt trigger times accounting for measured latency, submits orders, and writes `OrderAttempt` results back. The full entry → open position → exit → closed lifecycle is tracked end-to-end.

**Strategy** — all trades are `SHORT`-only. The goal is to enter SHORT 1–5 seconds *before* the funding payment fires and capture the compensating price drop (longs paying shorts triggers mass long exits → price falls). The position is closed seconds-to-minutes later. We do not hold for the funding payment itself.

**AI Signal Advisor** — after each signal is ingested, DeepSeek asynchronously analyses it and assigns a `GO` / `WATCH` / `PASS` recommendation with confidence score. The advisor understands the price-movement strategy and evaluates funding rate magnitude, time-to-funding, liquidity spread, and venue latency. Results appear as badges on signal cards in the UI.

**Audit** — every state transition is recorded in `TradeJournal` with actor (`OPERATOR` / `ENGINE` / `SYSTEM`) and timestamp.

---

## Safety

The platform is **off-by-default** at every execution boundary:

| Guard | Default | Override |
|---|---|---|
| Engine execution loop | `OFF` | `ENGINE_EXECUTION_LOOP_ENABLED=true` |
| Live order submission | `OFF` | `ENGINE_LIVE_ORDER_ENABLED=true` |
| Operator auth | `OFF` (`local-safe`) | enabled in `staging` / `prod-like` |
| Credential storage | `OFF` (`local-safe`) | enabled in `staging` / `prod-like` |
| Metadata sync on startup | `OFF` (`local-safe`) | `TRADING_METADATA_SYNC_ON_STARTUP=true` |

Running `./gradlew bootRunMonitor` or `bootRunEngine` locally with no extra ENV is safe: no live exchange activity, no auth, no credential persistence.

**Schema safety** — `monitor-app` owns the SQLite database. Flyway runs versioned migrations (V1–V5); Hibernate is in `validate` mode and never mutates schema.

**Risk guardrails** — max concurrent armed trades (default 3); per-venue disable list; `SHORT`-only funding direction enforced at the service layer.

---

## Quick Start

**Requirements:** JDK 25

```bash
# Run tests
./gradlew test

# Full build
./gradlew build

# Start monitor (port 8090)
./gradlew bootRunMonitor

# Start engine (port 8091)
./gradlew bootRunEngine

# Lint / format check
./gradlew spotlessCheck

# OWASP dependency audit
./gradlew security
```

Both `bootRun` tasks inject `SPRING_PROFILES_ACTIVE=local-safe` and a shared `INTERNAL_ENGINE_TOKEN` automatically unless you override them via ENV.

---

## Docker

A single `Dockerfile` builds either service:

```bash
docker build \
  --build-arg APP_MODULE=monitor-app \
  --build-arg APP_CLASSIFIER=monitor \
  --build-arg APP_PORT=8090 \
  -t funding-monitor:latest .

docker build \
  --build-arg APP_MODULE=engine-app \
  --build-arg APP_CLASSIFIER=engine \
  --build-arg APP_PORT=8091 \
  -t funding-engine:latest .
```

For a local smoke run:

```bash
cp deploy/.env.example .env
# Fill: SECURITY_OPERATOR_BOOTSTRAP_USERS, INTERNAL_ENGINE_TOKEN, CREDENTIALS_MASTER_KEY_BASE64
docker compose up --build
```

---

## Profiles

| Profile | Auth | Credentials | Engine loop | Live orders | Metadata sync |
|---|---|---|---|---|---|
| `local-safe` | OFF | OFF | OFF | OFF | OFF |
| `staging` | ON | ON | OFF | OFF | ON |
| `prod-like` | ON | ON | OFF¹ | OFF¹ | ON |

¹ Requires explicit `ENGINE_EXECUTION_LOOP_ENABLED=true` / `ENGINE_LIVE_ORDER_ENABLED=true`.

---

## Venues

Five perpetual futures venues supported:

| Venue | Credential check | Metadata sync | Order submission | Notes |
|---|---|---|---|---|
| Gate.io | ✓ | ✓ | ✓ | Testnet confirmed (SHORT FILLED) |
| Bybit | ✓ | ✓ | ✓ | Geo-blocked for UA IPs without VPN |
| OKX | ✓ | ✓ | ✓ | Testnet uses `x-simulated-trading: 1` header |
| Bitget | ✓ | ✓ | ✓ | Requires passphrase |
| KuCoin | ✓ | ✓ | ✓ | Requires passphrase |

**Exchange credentials** are stored AES-GCM encrypted per `operator_id + venue + mode`. Raw secrets are never returned by the API; only masks and status are exposed.

---

## Latency Calibration

The engine adjusts entry trigger times based on measured round-trip latency:

```
effectiveLatencyMs = max(0, measuredEntryLatencyMs + manualLatencyAdjustmentMs)
```

Latency is sampled automatically after each real order submission. Per-venue p50/p95/p99 percentiles and warm-up probe results are stored in the venue timing profile and surfaced in the UI. A manual probe endpoint is also available:

```
POST /api/v2/monitor/venues/{venue}/latency-probe
```

**Burst entry example** — `entryAttemptCount=3`, `entrySpacingMs=150`, `effectiveLatencyMs=40`:

```
Attempt 1: target 12:00:00.000 → trigger 11:59:59.960
Attempt 2: target 12:00:00.150 → trigger 12:00:00.110
Attempt 3: target 12:00:00.300 → trigger 12:00:00.260
```

---

## Operator API

All operator endpoints require `X-Operator-Token` when auth is enabled.

Bootstrap operators via ENV:

```
SECURITY_OPERATOR_BOOTSTRAP_USERS=alice:token,bob:token
```

Tokens are stored as SHA-256 hashes only.

**Core endpoints:**

```
# Candidates
GET    /api/v1/candidates
POST   /api/v1/candidates/{id}/approve
POST   /api/v1/candidates/{id}/reject

# Funding Events
GET    /api/v1/funding-events
POST   /api/v1/funding-events/{id}/arm

# Armed Trades
GET    /api/v1/armed-trades
DELETE /api/v1/armed-trades/{id}        # cancels; 422 on invalid state
GET    /api/v1/armed-trades/{id}/order-attempts

# Positions & Outcomes
GET    /api/v2/trades/{armedTradeId}/position
GET    /api/v2/trades/{armedTradeId}/outcome

# Venues
POST   /api/v1/venues/{venue}/sync
POST   /api/v1/venues/{venue}/check

# Credentials
PUT    /api/v1/operators/me/credentials/{venue}/{mode}
POST   /api/v1/operators/me/credentials/{venue}/{mode}/check
```

**Engine endpoints** (`X-Internal-Token` required):

```
GET  /internal/engine/summary
POST /internal/engine/execution/run-once
GET  /internal/engine/plans
```

---

## Telegram Bot

`@funding_arbitrage_bot_bot` — a companion bot for signal monitoring.

Commands: `/signals`, `/status`, `/links`, `/faq`

The bot polls `monitor-app` for live candidate and armed trade data and sends alerts when new signals appear. Configure with:

```
TELEGRAM_BOT_TOKEN=...
TELEGRAM_BOT_USERNAME=...
MONITOR_BASE_URL=http://monitor:8090
```

---

## Engine TDD

`engine-app` is treated as an executable specification. Every production class is mutation-tested at 100% with PIT. Coverage gates: 95% line / 90% branch.

```bash
./gradlew engineTddGate          # mutation + coverage gate
./gradlew engineTddDocsCheck     # verify requirement IDs in gap-matrix.md
```

Documentation: `docs/engine-tdd/`

---

## Observability

Optional Prometheus + Grafana stack in `deploy/observability/` — not part of the main flow, enable explicitly.

---

## Architecture Notes

- `engine-app` is **read-only from monitor's perspective** — it reads plans and writes `OrderAttempt` results back; it cannot modify `FundingEvent` or `ArmedTrade` state directly.
- The internal monitor→engine API is the only coupling point between the two runtimes; they can be deployed independently.
- Production topology: `monitor-app` on a control-plane VPS, `engine-app` co-located near the exchange (low-latency path).
