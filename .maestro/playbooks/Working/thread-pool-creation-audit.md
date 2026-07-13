# Thread Pool Creation — Codebase Audit

**Date:** 2026-07-13
**Scope:** monitor-app, engine-app, telegram-bot-app, platform-core

---

## Executive Summary

**The codebase defines no custom thread pool executors whatsoever.** All async (`@Async`) and scheduled (`@Scheduled`) execution relies entirely on Spring Boot's default auto-configured thread pools. The only explicitly sized pool in the entire codebase is the HikariCP database connection pool (`maximum-pool-size: 2`).

---

## 1. Key Annotations

| Module | `@EnableScheduling` | `@EnableAsync` |
|--------|---------------------|----------------|
| **monitor-app** | ✅ `MonitorApplication.java:17` | ✅ `MonitorApplication.java:18` |
| **engine-app** | ✅ `EngineApplication.java:10` | ✅ `EngineApplication.java:11` |
| **telegram-bot-app** | ✅ `TelegramBotApplication.java:12` | ❌ not present |

---

## 2. Spring Boot Default Thread Pool Configuration

Since no custom `ThreadPoolTaskExecutor`, `ThreadPoolTaskScheduler`, `AsyncConfigurer`, or `SchedulingConfigurer` beans exist, Spring Boot applies its auto-configured defaults:

### `@Async` — `TaskExecutionAutoConfiguration`
| Property | Default | Source |
|----------|---------|--------|
| `spring.task.execution.pool.core-size` | **8** | `SimpleAsyncTaskExecutor` / `TaskExecutorBuilder` |
| `spring.task.execution.pool.max-size` | **Integer.MAX_VALUE** (effectively unbounded) | `TaskExecutorBuilder` |
| `spring.task.execution.pool.queue-capacity` | **Integer.MAX_VALUE** (effectively unbounded) | `TaskExecutorBuilder` |
| `spring.task.execution.thread-name-prefix` | `task-` | `TaskExecutorBuilder` |
| `spring.task.execution.pool.keep-alive` | `60s` | `TaskExecutorBuilder` |

### `@Scheduled` — `TaskSchedulingAutoConfiguration`
| Property | Default | Source |
|----------|---------|--------|
| `spring.task.scheduling.pool.size` | **1** (single thread!) | `TaskSchedulingAutoConfiguration` |
| `spring.task.scheduling.thread-name-prefix` | `scheduling-` | `TaskSchedulingAutoConfiguration` |

**⚠️ Critical:** The scheduled task pool defaults to **size = 1**. Every `@Scheduled` method across all modules runs on the *same single thread*. A long-running or blocking scheduled method would delay all others.

---

## 3. `@Async` Methods (7 total — executed via default thread pool)

### engine-app (1 method)
| Method | Location | Notes |
|--------|----------|-------|
| `EngineCredentialCache.loadOnStartup()` | `EngineCredentialCache.java:33` | Loads venue credentials after startup |

### monitor-app (6 methods)
| Method | Location | Notes |
|--------|----------|-------|
| `SignalLiquidityService.assessAsync(SignalCandidate)` | `SignalLiquidityService.java:37` | Liquidity assessment after candidate ingest |
| `AiSignalAdvisorService.analyzeAsync(Long)` | `AiSignalAdvisorService.java:61` | AI analysis on signal candidate |
| `AutoApprovalPipelineService.onCandidateReady(CandidateReadyForAutoApprovalEvent)` | `AutoApprovalPipelineService.java:68` | Event-driven auto-approval entry |
| `AutoApprovalPipelineService.sweepNormalized()` | `AutoApprovalPipelineService.java:75` | Batch sweep for auto-approval |
| `AutoApprovalPipelineService.tryAutoProcess(Long)` | `AutoApprovalPipelineService.java:90` | Pipeline entry point |
| `LiquidityAutoAssessService.assessAfterArm(Long, String, String)` | `LiquidityAutoAssessService.java:26` | Auto-assessment after arm |

### telegram-bot-app
No `@Async` methods — `@EnableAsync` is not declared.

---

## 4. `@Scheduled` Methods (6 total — executed via single-threaded default scheduler)

### engine-app (2 methods)
| Method | Schedule | Location | Notes |
|--------|----------|----------|-------|
| `EngineExecutionScheduler.runLoop()` | `fixedDelay=250ms` | `EngineExecutionScheduler.java:22` | Main execution loop |
| `EngineMetricsPublisher.publishOnSchedule()` | `initialDelay=15s, fixedDelay=15s` | `EngineMetricsPublisher.java:53-56` | Metrics snapshot push |

### monitor-app (2 methods)
| Method | Schedule | Location | Notes |
|--------|----------|----------|-------|
| `FundingApiCandidateSourceService.scheduledRefresh()` | `fixedDelay=60s` | `FundingApiCandidateSourceService.java:97` | Pollls external funding API |
| `InstrumentMetadataSyncRunner.scheduledSync()` | `fixedDelay=240min` | `InstrumentMetadataSyncRunner.java:33` | Venue metadata sync |

### telegram-bot-app (2 methods)
| Method | Schedule | Location | Notes |
|--------|----------|----------|-------|
| `SignalNotificationScheduler.pollAndNotify()` | `fixedDelay=30s` | `SignalNotificationScheduler.java:45` | Telegram signal alerts |
| `TradeNotificationScheduler.pollAndNotify()` | `fixedDelay=30s` | `TradeNotificationScheduler.java:44` | Telegram trade alerts |

---

## 5. What Does NOT Exist

| Pattern | Found? |
|---------|--------|
| `ThreadPoolExecutor` (JDK) | ❌ |
| `Executors.newFixedThreadPool()` | ❌ |
| `Executors.newCachedThreadPool()` | ❌ |
| `Executors.newScheduledThreadPool()` | ❌ |
| `Executors.newSingleThreadExecutor()` | ❌ |
| `ThreadPoolTaskExecutor` `@Bean` | ❌ |
| `ThreadPoolTaskScheduler` `@Bean` | ❌ |
| `SimpleAsyncTaskExecutor` reference | ❌ |
| `AsyncConfigurer` / `AsyncConfigurerSupport` | ❌ |
| `SchedulingConfigurer` | ❌ |
| `@Bean` returning `Executor`/`ExecutorService` | ❌ |
| `@Bean` returning `ScheduledExecutorService` | ❌ |
| `spring.task.execution.*` properties in YAML | ❌ |
| `spring.task.scheduling.*` properties in YAML | ❌ |
| `spring.threads.virtual.enabled` | ❌ |
| `VirtualThreadTaskExecutor` / virtual threads | ❌ |
| Custom executor `@Configuration` classes | ❌ |

---

## 6. Only Explicit Pool Configuration: HikariCP

**File:** `monitor-app/src/main/resources/platform-core.yml` (lines 17-26)
```yaml
spring:
    datasource:
        hikari:
            maximum-pool-size: 2
```

This is the only pool with an explicitly configured size in the entire codebase.

---

## 7. Risk Assessment

### Risk 1: Single-threaded scheduler (`pool.size=1`)
All 6 `@Scheduled` methods compete for the same thread. If any method blocks or takes significantly longer than its interval:
- The 250ms engine execution tick could be delayed
- The 60s polling loop could be delayed
- Scheduled tasks could back up and never catch up

**Severity:** Medium — engine tick delay could cause missed trading windows.

### Risk 2: Unbounded async queue
The default `@Async` pool has `max-size=Integer.MAX_VALUE` and `queue-capacity=Integer.MAX_VALUE`. Under sustained load, threads could grow without bound. With 7 `@Async` methods under normal conditions this is unlikely to be an issue, but a burst of events could create many concurrent threads.

**Severity:** Low — 7 methods at typical operational volume.

### Risk 3: No virtual threads
No `spring.threads.virtual.enabled=true` despite Spring Boot 3.5 and JDK 25 support. Virtual threads would particularly benefit the `@Async` methods that perform I/O-bound work (HTTP calls, database queries).

**Severity:** Low — performance optimization opportunity, not a correctness issue.

---

## 8. Recommendations

1. **Configure `spring.task.scheduling.pool.size`** to at least **2–4** to prevent the single-threaded scheduler from becoming a bottleneck — especially critical for the 250ms engine execution tick.
2. **Consider `spring.task.execution.pool.max-size`** cap to prevent unbounded thread growth under unusual load.
3. **Evaluate virtual threads** (`spring.threads.virtual.enabled=true`) for JDK 25 + Spring Boot 3.5 to reduce I/O-bound thread overhead.
