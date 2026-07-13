# Startup and Shutdown Audit

**Audit:** AUDIT-ROUND-3-03 Section 6 — Startup and Shutdown
**Date:** 2026-07-13

---

## 1. Graceful Shutdown Configuration

**Finding:** `server.shutdown=graceful` is **NOT configured** in any module.

- All 3 applications (monitor-app, engine-app, telegram-bot-app) use Spring Boot's **default `server.shutdown=immediate`**
- No `spring.lifecycle.timeout-per-shutdown-phase` property exists in any YAML/properties file
- No `@PreDestroy`, `DisposableBean`, `SmartLifecycle`, or `Lifecycle` implementations exist in any module
- No custom JVM shutdown hooks (`Runtime.getRuntime().addShutdownHook`) are registered
- No `registerShutdownHook()` calls exist

**Risk:** HIGH. On SIGTERM, the JVM exits promptly without waiting for in-flight operations.

---

## 2. Shutdown Timeout Settings

**Finding:** No shutdown timeout parameters are configured.

| Parameter | Status | Default |
|---|---|---|
| `server.shutdown` | Not set | `immediate` |
| `spring.lifecycle.timeout-per-shutdown-phase` | Not set | `30s` (not applicable without graceful) |
| `@PreDestroy` | 0 beans | — |
| `DisposableBean` | 0 beans | — |
| `SmartLifecycle` | 0 beans | — |

**Risk:** Even if `server.shutdown=graceful` were enabled, there are no beans implementing cleanup hooks. The phase timeout default of 30s has no effect.

---

## 3. What Happens to an Executing Trade on SIGTERM

**Finding:** No protection exists for in-flight trades during shutdown.

### Scenario: SIGTERM arrives while engine is executing a trade

```
SIGTERM → Spring Boot initiates shutdown → 
  - Tomcat stops accepting requests
  - TaskScheduler stops invoking @Scheduled methods
  - JVM terminates orphan threads
  - active HTTP requests to exchanges may be interrupted
```

### Threads active during a live order:

| Thread | What it's doing | What happens on SIGTERM |
|---|---|---|
| `task-scheduler-1` (shared) | `EngineExecutionScheduler.runLoop()` — 250ms tick | Spring stops scheduling new invocations; in-flight `runOnce()` call runs to completion (no interruption mechanism) |
| `task-*` (executor pool) | `@Async` methods (7 total) — AI analysis, liquidity assessment, auto-approval processing | Pool threads may be interrupted; no graceful drain |
| Telegram library thread | `FundingBot` polling | Implicitly terminated |
| Exchange HTTP calls | `LiveExchangeExecutionPort` POST to exchange APIs | `HttpClient.sendAsync()` or blocking `.send()`: if thread is interrupted, `InterruptedException` is caught, flag restored, `FAILED` OrderAttempt returned. But the POST may already be received by the exchange. |

### Critical gap: In-flight order reconciliation

1. **No pre-shutdown drain**: There is no mechanism to wait for pending exchange responses before shutdown
2. **No reconciliation**: If the JVM exits after the exchange accepts an order but before the response is received, the `OrderAttempt` is never recorded. On restart:
   - The `submittedAttemptKeys` ConcurrentHashMap (in-memory dedup) is lost
   - The engine has no `OrderAttempt` record for that attempt
   - The execution plan may re-attempt the entry (if it's still within the entry window)
   - This could result in a **duplicate order** to the exchange
3. **No order-state reconciliation**: There is no mechanism to query the exchange for open orders on startup to reconcile state

### Compounding factors

- `server.shutdown=immediate` gives very little time for anything
- No `@PreDestroy` on `EngineExecutionService` or `LiveExchangeExecutionPort`
- No `SchedulingConfigurer` to await active tasks on shutdown
- Engine telemetry (`EngineTelemetryService`) uses in-memory atomic counters — lost on restart

---

## 4. All `ApplicationRunner` and `CommandLineRunner` Beans

### ApplicationRunner (3 beans, all in monitor-app)

| # | Bean | Purpose | External API Calls | DB Writes | Conditional |
|---|---|---|---|---|---|
| 1 | `OperatorAccountService` | Upserts bootstrap operator accounts from `SECURITY_OPERATOR_BOOTSTRAP_USERS` env var | No | Yes (JPA upsert) | No — always runs; no-op if env var blank |
| 2 | `CredentialStorageStartupValidator` | Validates credential master key is present if `credentials.storage.enabled=true` and `require-master-key-on-startup=true` | No | No | No — throws `IllegalStateException` if invariant violated |
| 3 | `InstrumentMetadataSyncRunner` | Syncs venue instrument metadata from configured exchanges | Yes (venue exchange APIs) | Yes (JPA save) | Yes — `trading.metadata.sync-on-startup=true` |

### CommandLineRunner

**0 beans** exist in any module.

### `@EventListener(ApplicationReadyEvent.class)` (2 methods)

| # | Bean | Purpose | Async | External API | DB Writes | Retry |
|---|---|---|---|---|---|---|
| 1 | `FundingApiCandidateSourceService.loadOnStartup()` | Fetches funding rate candidates from external API immediately on startup | No | Yes (`uainvest.com.ua`) | Yes (signal candidates) | No |
| 2 | `EngineCredentialCache.loadOnStartup()` | Asynchronously loads engine credentials from monitor | Yes (`@Async`) | Yes (monitor internal API) | No (in-memory cache) | Yes — up to 10 retries, 10s apart |

### `@PostConstruct` (1 method)

| # | Bean | Purpose | External API |
|---|---|---|---|
| 1 | `FundingBot.startPolling()` | Starts Telegram bot long-polling immediately after bean construction | Yes (Telegram API polling) |

### Order of execution on startup

```
1. Java reflection-based DI (constructor injection)
2. @PostConstruct:
   - FundingBot.startPolling() — starts Telegram polling
3. ApplicationRunner.run():
   - CredentialStorageStartupValidator.run() — validates master key
   - OperatorAccountService.run() — upserts bootstrap users
   - InstrumentMetadataSyncRunner.run() — syncs metadata (if enabled)
4. ApplicationReadyEvent listeners:
   - FundingApiCandidateSourceService.loadOnStartup() — fetches candidates
   - EngineCredentialCache.loadOnStartup() — async loads credentials (@Async)
```

---

## 5. Startup Hooks That Call External APIs

| Hook | API Called | Safe to Start With | Risk |
|---|---|---|---|
| `InstrumentMetadataSyncRunner` | Venue exchange APIs (Bybit, Gate, OKX, KuCoin, Bitget) | Yes — read-only metadata fetch | High latency if exchange is unreachable; delays startup |
| `FundingApiCandidateSourceService.loadOnStartup()` | `https://uainvest.com.ua/api/funding` | Yes — creates candidates only (requires operator review) | If external API is down, candidate refresh fails (logged as warning, does not block startup) |
| `EngineCredentialCache.loadOnStartup()` | Monitor internal API (`EnginePlanClient.fetchCredentials`) | Yes — `@Async` so non-blocking; retries up to 10 times | If monitor is unreachable, engine starts without credentials (async retry handles later) |
| `FundingBot.startPolling()` | Telegram API polling | Yes — connection errors logged, does not block startup | If Telegram is unreachable, polling starts when connection restored |

**None of these block application startup.** `CredentialStorageStartupValidator` is the only startup guard that can prevent the app from starting, and it only checks a local env var — no external calls.

---

## 6. Startup Hooks That Can Create or Modify Trading State

| Hook | What It Changes | Trading State? | Risk |
|---|---|---|---|
| `OperatorAccountService.run()` | Upserts rows in `operator_account` table | No — operator auth only | Low |
| `FundingApiCandidateSourceService.loadOnStartup()` | Inserts `SignalCandidate` records | No — requires operator review to become a `FundingEvent` → `ArmedTrade` | Low — candidates only, cannot auto-execute |
| `InstrumentMetadataSyncRunner.run()` | Updates instrument metadata in DB | No — metadata only | Low |

**None of the startup hooks can directly create `FundingEvent`, `ArmedTrade`, or execute actual trades.** The auto-approval pipeline (`AutoApprovalPipelineService`) processes NORMALIZED candidates but is event-driven or triggered from a `/api/auto-approval` endpoint — it is NOT invoked automatically on startup.

However, if `FundingApiCandidateSourceService.loadOnStartup()` ingests new candidates AND the auto-approval flow processes them asynchronously, a signal ingested on startup could trigger auto-approval shortly after startup. This is gated by `auto-approval.enabled` property.

---

## 7. All Scheduled Jobs That Start Automatically

| # | Module | Class | Method | Schedule | Begins After | Conditional |
|---|---|---|---|---|---|---|
| 1 | engine-app | `EngineExecutionScheduler` | `runLoop()` | `fixedDelay=250ms` (default) | 250ms after context refresh | Kill switch (`shouldRunScheduledLoop()`) — checks `executionLoopEnabled` + rate limit |
| 2 | engine-app | `EngineMetricsPublisher` | `publishOnSchedule()` | `initialDelay=15s`, `fixedDelay=15s` (default) | 15s after context refresh | `@ConditionalOnProperty(engine.metrics-publish.enabled=true)` |
| 3 | monitor-app | `InstrumentMetadataSyncRunner` | `scheduledSync()` | `fixedDelay=240min` (default) | 240min after context refresh | Internal `isScheduleEnabled()` check |
| 4 | monitor-app | `FundingApiCandidateSourceService` | `scheduledRefresh()` | `fixedDelay=60s` (default) | 60s after context refresh | `@ConditionalOnProperty(trading.candidate-source.enabled=true, matchIfMissing=true)` |
| 5 | telegram-bot-app | `TradeNotificationScheduler` | `pollAndNotify()` | `fixedDelay=30s` (default) | 30s after context refresh | `@ConditionalOnProperty(telegram.bot.token)` |
| 6 | telegram-bot-app | `SignalNotificationScheduler` | `pollAndNotify()` | `fixedDelay=30s` (default) | 30s after context refresh | `@ConditionalOnProperty(telegram.bot.token)` |

### All use `fixedDelay` (not `fixedRate` or `cron`)

This means each execution of a scheduled method waits for the previous to complete before the delay starts. A blocking operation stalls the entire schedule for that method (and because all share a single scheduler thread, for ALL scheduled methods across all modules).

---

## 8. How to Disable Each Scheduled Job

| # | Job | Disable Method | Property / Env Var | Default |
|---|---|---|---|---|
| 1 | Engine execution loop | Set property to `false` | `engine.execution-loop-enabled: false` | `false` (already off) |
| 2 | Engine metrics publish | Set property to `false` | `engine.metrics-publish.enabled: false` | `false` (already off) |
| 3 | Metadata sync | Set property to `false` | `trading.metadata.sync-interval-minutes: 0` (or remove) | `240` (sync runs every 240min by default) |
| 4 | Candidate source refresh | Set property to `false` | `trading.candidate-source.enabled: false` | `true` (matchIfMissing=true) |
| 5 | Telegram trade notifications | Unset token or set to blank | `telegram.bot.token: ` (blank) | `""` (blank — job disabled) |
| 6 | Telegram signal notifications | Unset token or set to blank | `telegram.bot.token: ` (blank) | `""` (blank — job disabled) |

### Equivalent environment variable overrides:

| Job | Env Var | Off Value |
|---|---|---|
| Engine execution loop | `ENGINE_EXECUTION_LOOP_ENABLED` | `false` |
| Engine metrics publish | `ENGINE_METRICS_PUBLISH_ENABLED` | `false` |
| Metadata sync | Cannot disable via env var directly (property `trading.metadata.sync-interval-minutes` mapped via YAML, not exposed as env var) | — |
| Candidate source refresh | `TRADING_CANDIDATE_SOURCE_ENABLED` | `false` |
| Telegram notifications | `TELEGRAM_BOT_TOKEN` | (blank/unset) |

### Profile-based safe defaults:

| Profile | Execution Loop | Metrics Publish | Metadata Sync | Candidate Refresh | Telegram |
|---|---|---|---|---|---|
| `local-safe` | OFF | OFF | OFF | ON (matchIfMissing) | Conditional on token |
| `staging` | OFF | ON | ON (schedule) | ON | Conditional on token |
| `prod-like` | OFF | ON | ON (schedule) | ON | N/A |
| `testnet` | ON (2s interval) | OFF | ON (schedule) | ON | Conditional on token |

---

## Key Recommendations

1. **Enable graceful shutdown** (`server.shutdown=graceful`) across all 3 modules to allow in-flight requests to complete
2. **Add `@PreDestroy` to `EngineExecutionService`** to abort pending execution ticks gracefully
3. **Add order reconciliation on engine startup** — query exchange open orders and match against `OrderAttempt` records to detect phantom orders
4. **Set `spring.task.scheduling.pool.size=4`** to prevent scheduler contention (noted in prior findings)
5. **Monitor order submission timeout vs shutdown grace period** — ensure the grace period exceeds the order submission timeout
