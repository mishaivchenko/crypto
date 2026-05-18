# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
./gradlew test                # Run all backend + UI tests (multi-module)
./gradlew build               # Full build with verification (includes tests)
./gradlew spotlessCheck       # Format/lint check (Gradle, Markdown, YAML)
./gradlew security            # OWASP dependency-check (CVSS ≥ 7.0 fails build)
./gradlew bootRunMonitor      # Start monitor-app on :8090 (local-safe profile)
./gradlew bootRunEngine       # Start engine-app on :8091 (local-safe profile)
./gradlew engineTddGate       # Engine TDD mutation + coverage gate
./gradlew engineTddDocsCheck  # Verify engine TDD requirement IDs consistency
```

Requires JDK 25. `bootRun*` tasks automatically set `SPRING_PROFILES_ACTIVE=local-safe` and `INTERNAL_ENGINE_TOKEN=funding-local-internal-token` unless overridden via ENV.

To run tests for a single module: `./gradlew :engine-app:test` or `./gradlew :platform-core:test`.

## Architecture

Three-module Gradle project. The business flow is:

```
Funding API → SignalCandidate (ingest) → Operator Review
→ FundingEvent (created) → Operator Arms Trade
→ ArmedTrade (ARMED) → Engine reads plan from Monitor
→ EngineExecutionService attempts entry → OrderAttempt recorded → TradeJournal
```

### `platform-core`
Pure domain library — no Spring Boot, no persistence. Contains:
- **Immutable domain records** (compact constructors with inline validation): `SignalCandidate`, `FundingEvent`, `ArmedTrade`, `OrderAttempt`, `TradeJournalEntry`, `Position`
- Engine wire contracts: `EngineExecutionPlan`, `EngineMetricsSnapshot`
- Shared ports: `ExecutionPort`
- Utilities: symbol/venue normalization (`SymbolMapper`, `InstrumentRegistryService`), HMAC signing

### `monitor-app` (port 8090)
Operator control plane. Spring Boot 3.5, JPA+SQLite, Flyway, OpenFeign.
- `api/` — REST controllers for all domain entities
- `application/` — Command/Query services per domain area (candidate, event, trade, engine, execution, venue, security, observability)
- `infrastructure/persistence/` — JPA repositories; schema owned by Flyway (V1–V5 migrations), JPA runs in `validate` mode (no auto-DDL)
- `infrastructure/exchange/` — venue adapters (Bybit, Gate, OKX, KuCoin, Bitget) via Feign
- `infrastructure/security/` — AES-GCM credential encryption with master key rotation
- `static/` — vanilla JS UI (no framework); modules: `app.js` (state machine), `ui.js` (rendering), `api.js` (HTTP), `history.js` (navigation)

Internal engine API (`/internal/engine/*`) is guarded by `X-Internal-Token`.

### `engine-app` (port 8091)
Lightweight execution runtime. Spring Boot 3.5, no persistence layer — reads plans from monitor via REST only.

13 core production classes, every one covered by pitest mutation testing at 100%. Key classes:
- `EngineExecutionService` — main orchestrator (plan fetch → timing check → order submission)
- `EngineExecutionScheduler` — ticks at 250 ms default
- `EnginePlanClient` — Feign client to monitor `/internal/engine/plans`
- `CredentialAwareExecutionPort` — guards live order submission (fails to `FAILED` attempt if credentials missing)
- `EngineMetricsPublisher` — pushes metrics snapshots back to monitor

## Venue Adapter Pattern

Each exchange venue implements three interfaces (all in `monitor-app/infrastructure/exchange/<venue>/`):
1. **`VenueCredentialCheckPort`** — verifies API key/secret work; `supportedModes()` controls which access modes appear in the UI testnet/production toggle
2. **`VenueMetadataPort`** — fetches instrument metadata (symbol, min qty, etc.) for candidate matching
3. **`VenueMarkPricePort`** — fetches current mark price for a single instrument; used by SL/TP and latency calibration

Credential properties resolved via `VenueProfileService.resolveCredentials(venue)`:
```
trading.<venue>.testnet.base-url
trading.<venue>.testnet.api-key
trading.<venue>.testnet.secret-key
trading.<venue>.testnet.passphrase   # okx, bitget, kucoin only
trading.<venue>.production.base-url
...
```

Defined in `platform-core.yml` with `${ENV_VAR:default}` overrides.

**Venues requiring passphrase** (`okx`, `bitget`, `kucoin`): hardcoded in two places that both must be updated for a new passphrase venue:
- `VenueProfileService.requiresPassphrase()` in monitor-app
- `LiveExchangeExecutionPort.requiresPassphrase()` in engine-app

**Adding a new live-order venue to `LiveExchangeExecutionPort`** (engine-app):
1. Add `baseUrl()` switch cases for `"<venue>:testnet"` and `"<venue>:production"`
2. Implement `submit<Venue>(plan, intent, reduceOnly, attemptedAt)` following the Bybit/Gate pattern
3. Add `if ("<venue>".equals(normalizedVenue)) return submit<Venue>(...)` dispatch in `submitOrder()`
4. Engine credentials read from: `engine.credentials.<venue>.{api-key,secret-key,passphrase}`; base URL from `engine.<venue>.<mode>-base-url`

**OKX testnet**: uses `x-simulated-trading: 1` header on all private calls (same base URL for both modes).

## Key Invariants

**Safe-by-default**: execution loop is OFF (`ENGINE_EXECUTION_LOOP_ENABLED=false`), live orders are OFF (`ENGINE_LIVE_ORDER_ENABLED=false`), operator auth is OFF in local-safe profile. The codebase can be run locally without any risk of live exchange activity.

**Schema ownership**: monitor-app owns the SQLite database. Hibernate is in `validate` mode — schema changes go through Flyway migrations only, never via JPA auto-DDL.

**Engine is read-only from monitor's perspective**: engine reads plans, writes back `OrderAttempt` results. It cannot modify FundingEvent or ArmedTrade state directly.

**Audit trail**: every state transition is recorded in `TradeJournalEntry` with actor (`OPERATOR`/`ENGINE`/`SYSTEM`) and timestamp.

**Credential isolation**: exchange credentials are AES-GCM encrypted, stored per `operator_id + venue + mode (testnet/production)`. The API never returns raw secrets — masks/status only.

## Engine TDD Program

`engine-app` is treated as an executable specification. Rules:
- Every production class in `engine-app` must appear in `docs/engine-tdd/gap-matrix.md` and have requirement-mapped tests
- JaCoCo gates: 95% line, 90% branch for engine rule classes
- Pitest mutation gate: 100% for engine-app, 90% for engine-core classes
- Documentation lives in `docs/engine-tdd/` (program.md, gap-matrix.md, requirements/)

## Profiles

| Profile | Auth | Credentials | Engine loop | Live orders | Metadata sync on startup |
|---------|------|-------------|-------------|-------------|--------------------------|
| `local-safe` | OFF | OFF | OFF | OFF | OFF |
| `staging` | ON | ON | OFF | OFF | ON |
| `prod-like` | ON | ON | OFF (explicit ENV) | OFF (explicit ENV) | ON |

## Observability

Optional: `deploy/observability/` contains Prometheus + Grafana compose setup. Not part of the main flow; enable explicitly.

## Candidate Source

Primary signal source: `https://uainvest.com.ua/api/funding` (polled by `FundingApiCandidateSourceService`). Creates `SignalCandidate` records only — `FundingEvent` requires manual operator review.
