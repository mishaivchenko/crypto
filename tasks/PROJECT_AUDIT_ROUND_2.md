# Project Audit — Round 2
## Product, Execution and Financial-Safety Discovery

**Date:** 2026-07-12
**Auditor:** Claude Code
**Baseline commit:** c5cce55 (HEAD matches — no comparability shift)
**Repository:** `/Users/mishaivchenko/dev/crypto`
**Branch:** `feat/auto-approval-sweep-159`

---

## A. Executive Conclusion

### What is actually implemented

The repository implements a **funding-event directional short strategy** — a single-leg SHORT perpetual position opened close to the funding timestamp on crypto exchanges. The implementation spans three microservices (monitor-app, engine-app, platform-core) with five exchange adapters (Gate, Bybit, OKX, KuCoin, Bitget) and a Telegram bot for notifications.

The **code scaffolding is comprehensive**: signal ingestion, normalization, operator approval, auto-approval, funding event lifecycle, trade arming, engine plan generation, scheduled execution loop, market order submission, exit handling, and PnL calculation are all coded.

### What has been runtime verified

The `./gradlew build` succeeds (all Java tests pass, 34 JS UI tests pass). Spotless fails only on the untracked audit file `tasks/PROJECT_AUDIT_ROUND_1.md` — not a code issue.

The existing production database (`data/fundingarb.db`) at migration V5 contains evidence of:
- **8 signal candidates**, **5 funding events**, **5 armed trades**, **2 order attempts**
- **0 positions**, **0 trade outcomes** — no trade has ever completed

The 2 order attempts both show:
- **Bybit**, `SHORT`, `25` quantity, `FAILED` status
- Failure reason: `"Missing engine credentials for bybit"`

### Where automation fails

**The automatic execution path has never completed an order.** The single failure observed in the database is a credential configuration issue: the engine tried to submit a Bybit testnet order but the credentials were not in the engine cache or environment.

There is **no evidence of Gate automatic execution** in the database — only Bybit was attempted.

### Is Gate automatic testnet execution currently possible?

**Not without credential configuration and schema migration.**

The production database at `data/fundingarb.db` has only reached Flyway migration V5 — it is missing V6 through V14 (including V4 which adds the `mode` column to `armed_trade`, V5 which adds `request_duration_ms` to `order_attempt`, V14 which creates `auto_approval_rule`). The schema is stale by 9 migrations.

However, the codebase itself defines:
- Gate testnet URL: `https://api-testnet.gateapi.io/api/v4`
- Gate adapter submits via `POST /futures/usdt/orders` with IOC and HMAC-SHA512
- Gate does have a `supportedModes()` returning `[TESTNET, PRODUCTION]`

The engine path: `scheduler → runOnce(false) → shouldRunScheduledLoop() → shouldProcessPlan(ENTRY_WINDOW only) → liveGateFailure() → submitGate()` requires:
1. `engine.execution-loop-enabled=true`
2. `engine.kill-switch-enabled=false`
3. `engine.live-order-enabled=true`
4. Gate in `liveEnabledVenues`
5. Notional ≤ `max-notional-usd`
6. Fresh instrument metadata (≤240 min)
7. Fresh latency profile (≤1440 min)
8. Credentials in engine cache

The `testnet` profile sets all of these except credentials and the database schema upgrade.

### Is Bybit automatic testnet execution currently possible?

**Same conclusion as Gate** with the additional finding that the historical failure was credential‑related. The code path exists and is identical in structure to Gate.

Critical Bybit adapter findings:
- `positionIdx: 0` is hardcoded — assumes One‑Way position mode
- No `accountType` or `category` verification
- No hedge‑mode handling
- `recvWindow` is 5000ms

### Is the system financially recoverable after failure?

**No.** There is zero exchange reconciliation code:
- No mechanism to query exchange open orders on restart
- No mechanism to detect `submittedAttemptKeys` (in‑memory `ConcurrentHashMap`) lost on restart
- No mechanism to detect positions opened while the engine was down
- No mechanism to detect orders filled but not recorded before crash
- No operator endpoint for reconciliation

The `attempt_key` unique constraint on `order_attempt` prevents duplicate records, but the engine would re‑process a plan after restart (the in‑memory idempotency set is empty) and could submit a duplicate order. The exchange might reject it (already filled/reduce‑only), but there is no recovery path for mid‑flight orders.

### Can discovery proceed to architecture planning?

**No.** The following must be resolved first:
1. The existing production database must be migrated to the current schema
2. A controlled Gate testnet execution must complete end‑to‑end to prove the flow
3. Credential configuration must be verified for Gate testnet
4. The engine restart recovery gap must be acknowledged and bounded
5. The P0 financial‑safety blockers (no reconciliation, no restart recovery) must be explicitly accepted or resolved

---

## B. Evidence Ledger

| Claim | Classification | Source |
|---|---|---|
| Strategy is SHORT-only | SOURCE_CONFIRMED | `ArmedTrade` compact constructor — only SHORT accepted |
| 5 exchange adapters (no BingX) | SOURCE_CONFIRMED | File listing under `infrastructure/exchange/` |
| Gate testnet URL | SOURCE_CONFIRMED | `LiveExchangeExecutionPort.java:918` |
| Bybit `positionIdx: 0` hardcoded | SOURCE_CONFIRMED | `LiveExchangeExecutionPort.java:146` |
| Manual path uses `force=true` | SOURCE_CONFIRMED | `DevTestRunService.java:219` |
| Auto path uses `force=false` | SOURCE_CONFIRMED | `EngineExecutionScheduler:runLoop() → runOnce(false)` |
| `submittedAttemptKeys` is in‑memory only | SOURCE_CONFIRMED | `EngineExecutionService.java:74` |
| `attempt_key` has DB UNIQUE index | SOURCE_CONFIRMED | `V1__baseline.sql`, `idx_order_attempt_key` |
| No exchange reconciliation code exists | SOURCE_CONFIRMED | Grep for reconciliation/recover across engine-app/monitor-app |
| Build fails on spotless (untracked file) | TEST_CONFIRMED | `./gradlew build` exit code 1 |
| engine-app tests pass | TEST_CONFIRMED | `./gradlew :engine-app:test` exit code 0 |
| Existing DB has 2 FAILED Bybit attempts | RUNTIME_CONFIRMED | `sqlite3 data/fundingarb.db` on `order_attempt` table |
| Both failures: "Missing engine credentials for bybit" | RUNTIME_CONFIRMED | `order_attempt.failure_reason` |
| No positions or outcomes in production DB | RUNTIME_CONFIRMED | `trade_position` and `trade_outcome` tables empty |
| Production DB only at migration V5 | RUNTIME_CONFIRMED | `flyway_schema_history` shows max installed_rank=5 |
| DB foreign keys pass | TEST_CONFIRMED | `PRAGMA foreign_key_check` returned empty |
| DB integrity ok | TEST_CONFIRMED | `PRAGMA integrity_check` returned "ok" |
| Kill switch defaults to ON | SOURCE_CONFIRMED | `EngineProperties.java` — `killSwitchEnabled = true` |
| TRADE_JOURNAL_UPDATED event code absent | SOURCE_CONFIRMED | V1 schema CHECK constraint — no ARMED_TRADE_UPDATED |
| ARMED_TRADE_UPDATED added by Java migration | SOURCE_CONFIRMED | `V3__trade_journal_add_cancel_event_code.java` |
| `FundingEvent.armedTradeId` not persisted | SOURCE_CONFIRMED | `FundingEventEntity` has no `armedTradeId` column; derived |
| Auto-approval notional cap enforced | SOURCE_CONFIRMED | `AutoApprovalPipelineService.autoExecute()` |
| Engine `engineTddDocsCheck` task missing | TEST_CONFIRMED | `Cannot locate tasks that match ':engine-app:engineTddDocsCheck'` |
| Entry exit side assumed | INFERENCE | Exit swaps side, no explicit position side query |
| Partial fill clears position | INFERENCE | `applyExitLifecycle` uses `filledQuantity` to record position close |

---

## C. Product Flow — Actual End-to-End Trace

| Step | Owner | Trigger | Input | State Before | State After | Persistence | External Call | Retry Behavior | Evidence |
|---|---|---|---|---|---|---|---|---|---|
| Signal ingestion | `FundingApiCandidateSourceService` | Scheduled poll | `sourceType`, `rawSymbol`, `fundingTime`, `fundingRate` | — | SignalCandidate(NEW) | `signal_candidate` INSERT | `GET uainvest.com.ua/api/funding` | Next poll cycle | SOURCE_CONFIRMED |
| Symbol normalization | `SignalCandidateIngestService` | After ingest | rawSymbol | NEW | NORMALIZED | `signal_candidate.status` update | `SymbolMapper` (local) | Ingest retries | SOURCE_CONFIRMED |
| AI advisor | `AiSignalAdvisorService` | API `/api/v1/candidates/{id}/analyze` | candidateId | NORMALIZED | NORMALIZED (unchanged) | `ai_signal_advice` INSERT | POST DeepSeek API | No retry | SOURCE_CONFIRMED |
| Auto-approval sweep | `AutoApprovalPipelineService.sweepNormalized()` | API `/api/v1/auto-approval/enable` | All NORMALIZED candidates | NORMALIZED | EVENT_CREATED or REJECTED | `signal_candidate.status` + `funding_event` INSERT + `armed_trade` INSERT | Liquidity + AI eval | Per-candidate try/catch | SOURCE_CONFIRMED |
| Operator approval (manual) | `SignalCandidateReviewService.approve()` | API `/api/v1/candidates/{id}/approve` | venue, symbol, fundingTime | NORMALIZED | EVENT_CREATED | `signal_candidate.status` + `funding_event` INSERT | None | No retry | SOURCE_CONFIRMED |
| FundingEvent creation | `FundingEventCommandService.create()` | Auto-approval or operator approve | venue, symbol, fundingTime, rate | DISCOVERED | DISCOVERED | `funding_event` INSERT | None | No retry | SOURCE_CONFIRMED |
| Trade arming | `FundingEventArmService.arm()` | API `/api/v1/funding-events/{id}/arm` | notional, side, timings | DISCOVERED | ARMED | `funding_event.status` + `armed_trade` INSERT | Venue profile | No retry | SOURCE_CONFIRMED |
| Engine plan generation | `MonitorEnginePlanService.listPlans()` | Engine poll | ALL active trades | ARMED/OPEN/etc | Unchanged (read-only) | None | Monitor DB query | Next plan poll | SOURCE_CONFIRMED |
| Plan status calculation | `EnginePlanStatusCalculator.deriveStatus()` | Plan fetch | ArmedTrade state + time | ARMED | ENTRY_WINDOW/WAITING | None | None | Next plan poll | SOURCE_CONFIRMED |
| Warmup probes | `EngineExecutionService.executeWarmupProbes()` | Before entry trigger | probeUrl, count | ENTRY_WINDOW | Unchanged | In-memory `warmupByTrade` | HTTP GET to exchange | Per-probe timeout | SOURCE_CONFIRMED |
| Entry order submission | `EngineExecutionService.executeEntryAttempt()` | calibratedTrigger passed | Market SHORT order | ENTRY_WINDOW | OPEN or FAILED | `order_attempt` INSERT + `trade_position` INSERT + `armed_trade.state` UPDATE | `LiveExchangeExecutionPort.submitOrder()` → POST exchange API | AttemptKey in-memory, DB unique | SOURCE_CONFIRMED (not runtime) |
| Exchange confirmation | `submitGate()` / `submitBybit()` + status followup | After submit | exchangeOrderId | SUBMITTED | FILLED or ACKNOWLEDGED | `order_attempt` EXTERNAL update | GET exchange order status | No retry | SOURCE_CONFIRMED |
| Position creation | `applyEntryLifecycle()` | On FILLED | entryPrice, filledQty | PENDING_OPEN | OPEN | `trade_position` INSERT | `POST /internal/engine/positions` | No retry | SOURCE_CONFIRMED |
| Exit order submission | `EngineExecutionService.executeExit()` | EXIT_WINDOW or early exit (SL/TP) | Market reduce-only order | OPEN | CLOSED or FAILED | `order_attempt` INSERT + `trade_position` UPDATE + `trade_outcome` INSERT + `armed_trade.state` UPDATE | `submitOrder(reduceOnly=true)` | Exit attemptKey released on failure | SOURCE_CONFIRMED |
| PnL calculation | `grossPnl()` in `applyExitLifecycle()` | Exit FILLED | entryPrice, exitPrice, filledQty | OPEN | CLOSED | `trade_outcome` INSERT | None | No retry | SOURCE_CONFIRMED |

**Furthest point reached by actual runtime:** `OrderAttempt(FAILED)` — entry submission failed before exchange contact due to credential configuration.

**Furthest point reached by evidence:** All code paths are SOURCE_CONFIRMED. No complete end-to-end execution has been runtime-verified or exchange-confirmed.

---

## D. Timing Model

### All Timestamps

| Timestamp | Planned Source | Persisted? | Precision | Used for decision? |
|---|---|---|---|---|
| `fundingTime` | External aggregator or API | Yes (BIGINT epoch ms) | Millisecond | Yes — entry relative anchor |
| `plannedEntryAt` | Set at arm time (default fundingTime) | Yes (BIGINT ms) | Millisecond | Yes — entry target |
| `plannedExitAt` | Set at arm time (default fundingTime + 60s) | Yes (BIGINT ms) | Millisecond | Yes — exit window |
| `triggerAt` | `targetEntryAt - effectiveEntryLatencyMs` | `EngineEntryAttemptPlan` (transient) | Millisecond | Yes — when to send order |
| `effectiveEntryLatencyMs` | Max(0, warmupP50 + manualAdjustment) OR plan value | Yes (BIGINT ms) | Millisecond | Yes — trigger calibration |
| `warmupP50Ms` | From HTTP probe samples | Yes (on armed_trade) | Millisecond | Yes — if warmup ran |
| `targetEntryAt` | `plannedEntryAt + spacing * index` | `EngineEntryAttemptPlan` (transient) | Millisecond | Yes — trigger target |
| `actualSubmitTime` | `Instant.now()` at submission | In `order_attempt.submitted_at` (BIGINT ms) | Millisecond | Observation only |
| `exchangeTimestamp` | From exchange response | In `order_attempt.exchange_timestamp` (BIGINT ms) | Millisecond | Observation only |
| `actualFillTime` | From exchange status response | In `order_attempt.exchange_timestamp` | Millisecond | Observation only |
| `requestDurationMs` | nanoTime delta | In `order_attempt.request_duration_ms` | Millisecond | Observation only |
| `fundingEvent.discoveredAt` | `Instant.now()` at creation | Yes (BIGINT ms) | Millisecond | Observation only |

### Clock Source

- `Instant.now(clock)` — injected Spring `Clock`, default `Clock.systemUTC()`
- No NTP verification, exchange server-time offset measurement, or clock drift detection
- JVM clock synchronisation is **assumed**
- The `VenueLatencyProbeService` uses `System.nanoTime()` for round-trip measurement (good), but trigger times use `Instant.now()` from the system clock (susceptible to drift)

### Timing Diagram for One Trade

```
FundingTime T₀ (e.g., 16:00:00.000 UTC)
    │
    ├── plannedEntryAt = T₀ (default)
    │   ├── attempt #1 target = T₀
    │   ├── attempt #2 target = T₀ + spacingMs
    │   └── attempt #N target = T₀ + spacingMs * (N-1)
    │
    ├── effectiveEntryLatencyMs = L (e.g., 50ms from warmup p50 + manual adj)
    │
    ├── triggerAt #1 = T₀ - L
    │   └── Scheduler loop checks: Instant.now(clock) >= triggerAt?
    │       └── YES → submit order
    │           └── Order should arrive at exchange ~T₀
    │
    ├── plannedExitAt = T₀ + 60s (default)
    │   └── EnginePlanStatus: EXIT_WINDOW after plannedExitAt
    │       └── Scheduler checks: plan.positionQuantity > 0?
    │           └── YES → submit reduce-only market exit
    │
    └── overdueThreshold = lastTargetAt + overdueGraceSeconds
```

### Scheduler Architecture

- **Mechanism:** Spring `@Scheduled(fixedDelayString = "${engine.execution-scheduler-tick-ms:250}")` — one shared thread
- **Rate limiting:** `EngineRuntimeControlService.shouldRunScheduledLoop()` with `AtomicLong` compareAndSet — enforces minimum `executionLoopIntervalMs` (default 1000ms)
- **Sequential:** Single-threaded execution — one slow venue blocks all others
- **One loop for all trades:** All plans are processed in a single `runOnce()` iteration; order depends on plan list order from API

### Critical Timing Issues

1. **No exchange server-time sync** — `Instant.now(clock)` is local JVM time
2. **No clock drift detection** — Mac mini sleep, NTP adjustments, JVM pauses all unhandled
3. **Millisecond precision assumed** — `Instant.now()` on macOS has ~μs resolution, but JVM pause can add ms
4. **Acting as staging runner on a Mac mini** — confirmed at risk of sleep/hibernation
5. **One loop blocks** — Gate slow response delays Bybit and vice versa
6. **No logging of scheduler lag** — missed funding events are invisible

---

## E. Manual Versus Automatic Execution Diff

| Aspect | Manual (DevTestRun) | Automatic (Scheduler/runOnce) |
|---|---|---|
| **Entry endpoint** | `POST /api/v2/monitor/dev/test-runs/{id}/entry` | `EngineExecutionScheduler.runLoop()` → `runOnce(false)` |
| **Service** | `DevTestRunService.runPhase(phase, true)` | `EngineExecutionService.runOnce(false)` |
| **Force flag** | `force=true` | `force=false` |
| **Plan status gate** | WAITING_ENTRY or ENTRY_WINDOW or OVERDUE | ENTRY_WINDOW only |
| **Calibrated trigger check** | YES — `!calibratedTrigger.isAfter(now)` | NO — `shouldProcessPlan` only checks status, then iterates attempts and checks trigger per attempt |
| **Source type** | `DEV_TEST_RUN` (validated) | Any source |
| **Timing profile** | Seeded with `entryLatencyMs=0` at creation | Must be within `latencyMaxAgeMinutes` (default 1440) |
| **Credentials** | Same credential check via `liveGateFailure()` | Same credential check |
| **Kill switch** | Same check | Same check |
| **Live order enabled** | Same check | Same check |
| **Instrument metadata** | Same freshness check | Same freshness check |
| **Warmup probes** | Same path | Same path |
| **Attempt recording** | Same `recordOrderAttempt()` | Same `recordOrderAttempt()` |
| **State update** | Same `updateTradeState()` | Same `updateTradeState()` |

### Key Difference

Manual execution uses `force=true` which **bypasses the timing gate** (`shouldProcessTargetEntry` accepts WAITING_ENTRY, ENTRY_WINDOW, and OVERDUE statuses). Automatic execution with `force=false` only processes `ENTRY_WINDOW` plans.

Manual execution creates the plan with `plannedEntryAt = now + 10s` (10 seconds in the future), so the entry window opens almost immediately. It also seeds a latency profile with 0ms, avoiding latency freshness checks.

**The core execution path (`submitOrder` → `liveGateFailure` → venue-specific submit) is identical between both paths.** Manual execution has NOT been shown to bypass `liveGateFailure()` or the credential/notional/metadata checks — both paths hit the same guard code.

### Why Auto Failed

The DB evidence shows: `"Missing engine credentials for bybit"`. This error comes from `LiveExchangeExecutionPort.missingCredentialsReason()` which checks:
1. `engine.credentials.bybit.api-key` in environment or credential cache
2. `engine.credentials.bybit.secret-key`

These were missing when the engine tried its automatic run. The manual test may have used manually configured env vars that were later removed or never set for automatic startup.

---

## F. Exchange Reports

### Gate.io

| Aspect | Finding | Evidence |
|---|---|---|
| Endpoint | `POST /futures/usdt/orders` (REST API v4) | `LiveExchangeExecutionPort.java:253` |
| Testnet base URL | `https://api-testnet.gateapi.io/api/v4` | Engine config `application.yml:28` |
| Production base URL | `https://fx-api.gateio.ws/api/v4` | Engine config `application.yml:29` |
| Auth signature | HMAC-SHA512 of method+path+bodyHash+timestamp | `LiveExchangeExecutionPort.java:255-260` |
| Request timestamp | `System.currentTimeMillis() / 1000L` (seconds) | `LiveExchangeExecutionPort.java:254` |
| Symbol format | Contract name from `plan.venueSymbol()` | Passed through `orderQuantity()` |
| Quantity unit | Contracts (unitless integer) | `size.setScale(0, RoundingMode.DOWN).intValueExact()` |
| Contract multiplier | Fetched via `contractMultiplier()` | Used in `orderQuantity()` for notional check vs `minNotionalValue` |
| Price | `"0"` (market order) | `LiveExchangeExecutionPort.java:247` |
| Order type | IOC (`tif: "ioc"`) | `LiveExchangeExecutionPort.java:248` |
| Size convention | **Negative for entry, positive for exit** | `reduceOnly ? quantity : quantity.negate()` at line 243 |
| Reduce-only | `"reduce_only": true/false` | `LiveExchangeExecutionPort.java:249` |
| Client order ID | `"engine-{tradeId}-{entry\|exit}"` | `orderLinkId()` at line 1000-1002 |
| **Status polling** | **NONE** — IOC response is final | No `gateStatus()` method; response body parsed directly |
| Filled detection | `"finished".equals(status) && "filled".equals(finishAs)` | Lines 270-272 |
| Partial fill handling | IOC fills what's available; unfilled expires | Implicit in IOC semantics |
| Error response | `response.statusCode() >= 300` → rejection | Lines 269-272 |

**Key concerns:**
1. No status polling after Gate order creation — relies on IOC response being definitive
2. Size sign convention (negative=entry, positive=exit) is exchange-specific; reversing would increase exposure
3. `positionIdx` not sent to Gate (this is correct — Gate doesn't use it)
4. Quantity uses `intValueExact()` — large positions could overflow

### Bybit

| Aspect | Finding | Evidence |
|---|---|---|
| Endpoint | `POST /v5/order/create` | `LiveExchangeExecutionPort.java:157` |
| Testnet base URL | `https://api-testnet.bybit.com` | Engine config `application.yml:25` |
| Production base URL | `https://api.bybit.com` | Engine config `application.yml:26` |
| Auth signature | HMAC-SHA256 of `{timestamp}{apiKey}{recvWindow}{body}` | Line 153 |
| Request timestamp | `System.currentTimeMillis()` (milliseconds) | Line 152 |
| Recv window | 5000ms | Constant at line 38 |
| **positionIdx** | **Hardcoded to 0** (One‑Way mode) | Line 146 |
| ReduceOnly | Sent as JSON field | Line 145 |
| Status polling | **YES** — `bybitStatus()` called after creation | Lines 180, `bybitStatus()` at 183-232 |
| Status endpoint | `GET /v5/order/realtime` | Line 202 |
| Filled detection | `"Filled".equalsIgnoreCase(status)` | Line 214 |
| Partial fill | `cumExecQty`, `avgPrice`, `cumExecFee` parsed | Lines 218-222 |
| Hedge mode | **Not supported** — `positionIdx` must be 1 or 2 in hedge mode | Line 146 always sends 0 |

**Key concerns:**
1. **`positionIdx: 0` assumes One‑Way mode** — will be rejected if account is in Hedge mode
2. **LEVERAGE NOT CONFIGURED** — no `/v5/position/set-leverage` call before trade
3. **MARGIN MODE NOT CONFIGURED** — no `/v5/account/set-margin-mode` call
4. `accountType=UNIFIED` assumed (used in credential check but not in order)
5. Geo-block errors indistinguishable from auth errors — both return non-zero `retCode`
6. Bybit testnet may require VPN from certain regions — no detection

### BingX

**Completely absent from codebase.** Zero references across all Java, YAML, properties, JSON, Markdown, and config files.

| Layer | BingX Present? | Evidence |
|---|---|---|
| Domain enums (Venue) | No | Not in any enum |
| Exchange adapter directory | No | No `bingx/` under `infrastructure/exchange/` |
| Monitor engine plan | No | No routing in plan build |
| `LiveExchangeExecutionPort` | No | No `submitBingX()` method |
| `VenueMarkPricePort` implementations | No | 5 implementations: gate, bybit, okx, kucoin, bitget |
| `VenueMetadataPort` implementations | No | Same |
| `VenueCredentialCheckPort` implementations | No | Same |
| `supportedModes()` | No | Not referenced |
| Credential storage | No | No credential paths for bingx |
| UI venue selector | No | Venues screen has no bingx |
| Instrument registry | No | Not in metadata sync |
| Git history | No | Not found in any branch |
| Documentation | No | Not mentioned in CLAUDE.md, docs, README |
| `.env` / config | No | No BINGX_ variables |

**BingX is a P1 blocker for MVP** — no implementation exists at any layer.

---

## G. State Machine and Persistence Report

### ArmedTrade State Transitions

```
ARMED ──→ CANCELLED (API cancel, CANCELLABLE_STATES)
ARMED ──→ FAILED (engine, entry attempt failed)
ARMED ──→ OPEN (engine, entry FILLED)
OPEN ──→ CLOSED (engine, exit FILLED)
OPEN ──→ FAILED (engine, exit failed terminally)
```

**States NOT persisted but used in code:**
- `ENTRY_PENDING` — appears in `ACTIVE_STATES` set and `CANCELLABLE_STATES`, but never written to `ArmedTradeEntity.state`
- `ENTRY_ATTEMPTED` — same, concept only
- `EXIT_PENDING` — same

The engine transitions directly: `ARMED → OPEN` or `ARMED → FAILED`. The EntryPending/Attempted states exist only in plan status calculation.

### OrderAttempt States

```
CREATED ──→ SUBMITTED (implicit)
SUBMITTED ──→ FILLED (exchange filled)
SUBMITTED ──→ ACKNOWLEDGED (accepted, not filled)
SUBMITTED ──→ REJECTED (exchange rejected)
SUBMITTED ──→ FAILED (engine/system failure)
```

### Position States

```
PENDING_OPEN ──→ OPEN (entry FILLED → recordPosition)
OPEN ──→ CLOSED (exit FILLED → recordPosition with CLOSED)
OPEN ──→ FAILED
```

### Database Schema Findings

- **Integrity check:** OK (`PRAGMA integrity_check` returns "ok")
- **Foreign keys:** PASS (`PRAGMA foreign_key_check` empty)
- **Production DB:** Only at migration V5 — 9 migrations behind current (V14)
- **Missing tables in prod DB:** `auto_approval_rule`, `liquidity_assessment`, `ai_signal_advice`, `venue_profile`, `operator_account`, `operator_exchange_credential`
- **Missing columns in prod DB:** `mode` on `armed_trade`, `request_duration_ms` on `order_attempt`, `average_fill_price/filled_quantity/fee_usd` on `order_attempt`, `stop_loss_usd/take_profit_usd/warmup_*` on `armed_trade`

### `fundingEvent.armedTradeId`

- **NOT persisted** in `funding_event` table — no column exists
- `FundingEventMapper.toDomain()` has an overload that accepts `armedTradeId` as a parameter, but this is used only for API response enrichment (join query)
- The original `FundingEvent` record has the field, but it's always null after a direct DB read without the join

---

## H. Idempotency and Recovery Report

### Attempt Key

Format: `"entry:{armedTradeId}:{attemptNumber}:{targetEntryAt}"` or `"exit:{armedTradeId}:{plannedExitAt}"`

- In-memory: `ConcurrentHashMap.newKeySet()` in `EngineExecutionService` — **lost on restart**
- Database: `UNIQUE INDEX idx_order_attempt_key` on `order_attempt(attempt_key)` — survives restart

### Crash Scenarios

| Scenario | Local State | Exchange State | Duplicate Risk | Recovery |
|---|---|---|---|---|
| 1. Crash before OrderAttempt record | ARMED/OPEN | Order may be filled | YES — engine re-processes on restart | DB unique key prevents duplicate record, but duplicate exchange order possible |
| 2. Concurrent runOnce calls | Thread-safe (single thread) | N/A | Protected by single-thread scheduler | N/A |
| 3. Two engine instances | Each has own `submittedAttemptKeys` | Orders submitted twice possible | **YES** — no distributed lock | Only DB unique key guards |
| 4. Exchange timeout + retry | Attempt key released | Order may be filled | YES — retry sends another order | No exchange-side dedup via `orderLinkId` is advisory, not guaranteed |
| 5. HTTP response lost after ENTRY_PENDING | Not persisted (state not set) | Order filled | YES — engine has no record | No recovery |
| 6. Partial fill + restart | Position recorded | Order partially filled | Already recorded | Position exists, exit can proceed |
| 7. Exit submitted twice | Attempt key on first submit | Reduce-only rejects second | No (reduce-only) | Safe — but wastes API call |
| 8. DB write succeeds, response lost | OrderAttempt persisted | Unknown to engine | On re-read, engine finds FILLED → safe | Safe — if status was FILLED |

### Critical: No Distributed Idempotency

The `ConcurrentHashMap` is JVM-local. If the engine restarts between creating an exchange order and recording it, the engine will:
1. Fetch plans from monitor
2. Attempt key is not in `submittedAttemptKeys` (empty after restart)
3. `reserveAttemptKey()` succeeds
4. Engine sends another order to exchange

The exchange SHOULD reject a duplicate reduce-only order, but the engine has no mechanism to confirm. For entry orders, a duplicate SHORT could increase exposure.

---

## I. Financial-Safety Blocker Register

### P0 — Can create or lose control of a real position

| # | Blocker | Location | Impact |
|---|---|---|---|
| 1 | **No exchange reconciliation** | Entire system | After restart, engine cannot detect open orders, filled orders, or existing positions from exchange state. Any state mismatch is invisible. |
| 2 | **No restart recovery** | `EngineExecutionService.submittedAttemptKeys` | In-memory idempotency lost on restart. Engine may re-submit orders already filled. |
| 3 | **No distributed lock** | `EngineExecutionService` | Two engine instances can both process the same plan and both submit orders. |
| 4 | **Exit may fail without operator alert** | `EngineExecutionService.executeExit()` | If exit fails, engine sets state to FAILED but there is no guaranteed operator notification path. |
| 5 | **No exchange position statecheck before entry** | `LiveExchangeExecutionPort` | Engine does not check if a position already exists before submitting entry. A duplicate entry could double exposure. |

### P1 — Blocks controlled testnet or reliable recovery

| # | Blocker | Location | Impact |
|---|---|---|---|
| 6 | **BingX completely absent** | All layers | MVP venue requirement cannot be met |
| 7 | **Existing DB at migration V5** | `data/fundingarb.db` | Cannot run current code on existing DB — schema mismatch |
| 8 | **No automatic migration on engine start** | Flyway on monitor | If monitor starts with stale DB, `OPEN` and `FAILED` state transitions will fail |
| 9 | **Manual path seeds latency=0ms** | `DevTestRunService.seedTestnetTimingMarker()` | Creates false latency data — not representative |
| 10 | **No engine-attempted Gate orders in DB** | Production DB | Only Bybit was ever attempted; Gate path entirely unproven |
| 11 | **Credentials not verified for automatic path** | Engine credential cache | Historical failure shows credentials misconfigured |

### P2 — Blocks controlled live operation

| # | Blocker | Location | Impact |
|---|---|---|---|
| 12 | **No exchange server-time synchronisation** | `Instant.now(clock)` | Clock drift of seconds would cause early or missed entry |
| 13 | **No scheduler lag measurement** | `EngineExecutionScheduler` | Missed funding events invisible |
| 14 | **Bybit `positionIdx: 0` hardcoded** | `LiveExchangeExecutionPort.java:146` | Fails on Hedge mode accounts |
| 15 | **Bybit leverage not configured** | `LiveExchangeExecutionPort.submitBybit()` | Default leverage may be incorrect |
| 16 | **No partial-fill reconciliation** | `applyEntryLifecycle()` | If entry partially fills, remaining quantity is abandoned (IOC) |
| 17 | **No stale-signal detection** | `EnginePlanStatusCalculator` | A plan delayed beyond funding time enters OVERDUE and is skipped |
| 18 | **`grossPnl` uses `entryPrice` from plan, not from exchange** | `EngineExecutionService:652` | If the entry price was adjusted by the exchange, the plan's `positionEntryPrice` may be stale |
| 19 | **PnL ignores contract multiplier** | `grossPnl()` formula | Formula `(entry-exit)*quantity` assumes quantity is USD, not contracts |
| 20 | **No maker/taker fee distinction** | `TradeOutcome` | Fees are estimated, not from exchange |

### P3 — Quality, maintenance or later scaling concern

| # | Blocker | Location | Impact |
|---|---|---|---|
| 21 | `engineTddDocsCheck` task missing | Build system | Cannot verify TDD documentation |
| 22 | Spotless fails on untracked files | Build system | CI would fail if audit files present |
| 23 | No OrderBookAdapter for Gate/Bybit in engine | `LiveExchangeExecutionPort` | Cannot check order book depth before market order |
| 24 | FundingEvent.armedTradeId not persisted | Schema | Requires join query for each read |
| 25 | `ARMED_TRADE_UPDATED` not in original CHECK constraint | V1 schema | V3 migration recreated table to add it — risky pattern |

---

## J. Test and Runtime Truth

### Executed Commands

| Command | Start | End | Exit Code | Result |
|---|---|---|---|---|
| `./gradlew build` | 2026-07-12 | 2026-07-12 | **1** | FAILED — `spotlessMiscCheck` on untracked `tasks/PROJECT_AUDIT_ROUND_1.md` |
| `./gradlew :engine-app:test` | 2026-07-12 | 2026-07-12 | **0** | PASS — all tests UP-TO-DATE |
| `./gradlew :monitor-app:test` | 2026-07-12 | 2026-07-12 | **0** | PASS — all tests UP-TO-DATE |
| `./gradlew :platform-core:test` | 2026-07-12 | 2026-07-12 | **0** | PASS — all tests UP-TO-DATE |
| `./gradlew :engine-app:engineTddDocsCheck` | 2026-07-12 | 2026-07-12 | **1** | FAILED — task not found |
| `./gradlew spotlessCheck` | 2026-07-12 | 2026-07-12 | **1** | FAILED — trailing whitespace in untracked file |

### What the Tests Cover (From Test File Inventory)

**Engine-app tests (16 files):**
- `EngineExecutionServiceTest` — unit tests for `runOnce`, `runTarget`, `calibratedTrigger`, `grossPnl`
- `EngineExecutionServiceWarmupTest` — warmup probe logic
- `EngineExecutionSchedulerTest` — scheduler rate limiting
- `CredentialAwareExecutionPortTest` — credential guard behavior
- `EnginePlanClientTest` — plan fetching
- `EngineRuntimeControlServiceTest` — runtime toggle
- `EngineControllerTest` — API endpoints
- `EngineCredentialCacheTest` — credential caching
- `AutonomousLoopIntegrationTest` — integration test (mock exchange)
- `EngineMetricsPublisherTest` — metrics publishing

**What tests do NOT cover:**
- ❌ Scheduler timing accuracy
- ❌ Exchange adapter submit methods (Gate/Bybit submit not mocked in unit tests)
- ❌ Credential flow (real HTTP calls)
- ❌ Persistence layer
- ❌ State transitions (full lifecycle)
- ❌ Partial fill handling
- ❌ Restart recovery
- ❌ Duplicate execution
- ❌ Cross-process monitor-to-engine flow

**Pitest mutation testing:** Not executed in this audit. The task `engineTddDocsCheck` is missing, suggesting the TDD pipeline is not fully wired.

---

## K. Documentation Contradictions

1. **CLAUDE.md says:** `engineTddDocsCheck` task exists. **Reality:** Task does not exist in gradle.
2. **CLAUDE.md says:** Pitest mutation gate at 100% for engine-app. **Not verified** — no Pitest task was executable.
3. **CLAUDE.md says:** 13 production classes in engine-app. **Reality:** 16 source files found.
4. **CLAUDE.md says:** OKX testnet uses `x-simulated-trading: 1` header. **Confirmed** — this is correct.

---

## L. Reusable Component Assessment

| Component | Classification | Rationale |
|---|---|---|
| Domain records (`SignalCandidate`, `FundingEvent`, `ArmedTrade`, `OrderAttempt`, `Position`, `TradeOutcome`) | **KEEP_CANDIDATE** | Well-designed immutable records with compact constructor validation |
| State enums (`ArmedTradeState`, `OrderAttemptStatus`, `PositionState`) | **KEEP_CANDIDATE** | Complete state sets, though some states are unused (ENTRY_PENDING etc.) |
| `EngineExecutionService` orchestration | **REPAIR_CANDIDATE** | Solid structure but needs restart recovery, distributed idempotency, reconciliation hooks |
| `EngineExecutionScheduler` | **REPAIR_CANDIDATE** | Simple and fast, but missing clock sync, drift detection, lag monitoring |
| `LiveExchangeExecutionPort` | **REPAIR_CANDIDATE** | Works for each venue but needs leverage setup, position mode verification, status polling for Gate |
| `EngineCredentialCache` | **KEEP_CANDIDATE** | Good pattern but needs invalidation, refresh on credential change |
| `AutoApprovalPipelineService` | **KEEP_CANDIDATE** | Well-structured pipeline with proper guard conditions |
| `AutoApprovalExecutor` | **KEEP_CANDIDATE** | Correct transactional boundary (approve+arm atomic) |
| `EnginePlanStatusCalculator` | **REPAIR_CANDIDATE** | Needs OVERDUE handling with max-late-entry guard |
| `EngineEntryAttemptScheduleBuilder` | **KEEP_CANDIDATE** | Clean timing calculation |
| `OperatorCredentialService` | **KEEP_CANDIDATE** | Good AES-GCM encryption, mask display, env fallback |
| `InternalTokenFilter` | **KEEP_CANDIDATE** | Simple path-based token guard |
| `OperatorAuthenticationFilter` | **KEEP_CANDIDATE** | Simple header-based auth |
| Venue adapters (GateMarkPriceAdapter, etc.) | **KEEP_CANDIDATE** | Consistent pattern across all 5 venues |
| UI (vanilla JS screens) | **REPAIR_CANDIDATE** | Works but needs reconciliation view, alert panels |
| Telegram bot | **KEEP_CANDIDATE** | Good alerting foundation but needs position-stuck alerts |
| `DevTestRunService` | **REMOVE_CANDIDATE** or **REPLACE_CANDIDATE** | Seeds fake latency data; manual bypass undermines timing guarantees |
| `approved_funding` and `order_execution_time` legacy tables | **REMOVE_CANDIDATE** | Dead schema from earlier iteration; no code references |
| BingX | **UNKNOWN** | Must be built from scratch |
| Exchange reconciliation | **UNKNOWN** | Must be designed and implemented |
| Restart recovery | **UNKNOWN** | Must be designed and implemented |

---

## M. Unresolved Questions

After source investigation, the following cannot be answered from evidence alone:

### Product and Strategy
1. **Expected market behavior around funding time?** — Is the movement always downward? What magnitude?
2. **Minimum funding-rate threshold?** — At what rate is the trade worth the risk?
3. **Maximum funding-rate threshold?** — At what rate does the trade become too risky?
4. **Symbol selection?** — Manual, automatic, or both?
5. **Multiple venues for same signal?** — Compete or both execute?
6. **Definition of successful trade?** — Is any positive PnL success, or is there a target?

### Entry Timing
7. **Position filled BY funding time or AT funding time?**
8. **Acceptable timing error in milliseconds?**
9. **Is Mac mini acceptable for staging?** — Sleep risk acknowledged?
10. **VPS required before automatic timing can be considered valid?**

### Exit Semantics
11. **Exit relative to funding time or actual fill time?**
12. **What should happen if exit order is rejected?** — How many retries?
13. **Is manual emergency close sufficient for MVP?**

### Risk
14. **Maximum testnet notional?** — Currently $25
15. **Maximum live notional?**
16. **Maximum simultaneous exposure?**
17. **Daily loss limit required before live?**
18. **Consecutive-loss limit required?**

### Venue
19. **Which is the first production venue?**
20. **Is Gate the mandatory first automatic vertical slice?**
21. **Can OKX/KuCoin/Bitget be removed from MVP scope?**
22. **Why was Bybit chosen when it requires VPN?**

### Operations
23. **Where should MVP run?** — Mac mini, VPS, or cloud?
24. **Must the system survive host restart automatically?**
25. **Is unattended live execution ever intended?**
26. **Maximum acceptable operator response time?**

---

## N. Discovery Checkpoint

### Established
- Complete product flow is coded and traceable from signal ingestion to trade outcome
- Gate and Bybit adapters exist with correct API paths and signing
- Manual execution path calls the same core methods as automatic (with `force=true`)
- Historical auto-execution failure was credential-related (Bybit, "Missing engine credentials")
- No complete end-to-end execution has been demonstrated
- Existing production DB is 9 migrations behind current schema
- No exchange reconciliation exists — this is a P0 financial-safety blocker
- No restart recovery exists — `submittedAttemptKeys` is in-memory only
- BingX is completely absent — must be built from scratch
- Kill switch defaults to ON (meaning "trading is stopped")
- The `testnet` profile sets kill-switch OFF (enables trading) and loop ON

### What Remains Unknown
- Whether Gate testnet credentials are currently configured and working
- Whether Bybit testnet is accessible without VPN from current location
- Whether the Mac mini is stable enough for unattended execution
- Whether the strategy is actually profitable
- What timing precision is required
- What the operator expects for alerting, recovery, and manual intervention

### Decisions Still Prohibited
- Cannot declare the system ready for architecture planning
- Cannot declare the system safe for unattended execution
- Cannot remove OKX/KuCoin/Bitget without owner confirmation
- Cannot remove DevTestRunService without replacement manual path

### What Is Needed Next
1. **Resolve P0/P1 financial-safety blockers** before any architecture planning
2. **Design and implement exchange reconciliation** — minimum: query open orders, detect position mismatches, operator endpoint
3. **Design and implement restart recovery** — at minimum: re-read last attempt status from DB on startup
4. **Complete a controlled Gate testnet end-to-end execution** to prove the flow
5. **Upgrade production database** to current migration V14
6. **Answer the 27 unresolved questions** from section M
7. **Decide on MVP venue scope** (add BingX or accept delay; remove unused venues)

### Next Audit Recommendation

**Round 3 should not be conducted at code level.** After the P0 blockers are resolved (reconciliation + restart recovery) and the first Gate testnet execution completes, the appropriate next step is:

**→ Architecture and implementation planning session**

With the owner present to answer the product and strategy questions. The audit has established what exists. The next phase is to decide what should change.
