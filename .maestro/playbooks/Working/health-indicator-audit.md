# Health Indicator Audit

**Audit Date:** 2026-07-13
**Scope:** monitor-app, engine-app, telegram-bot-app

---

## 1. `/actuator/info` — Git Commit Info

**Status: ❌ No Git or build info configured**

| Aspect | Finding |
|--------|---------|
| `build-info.properties` | **Not present** anywhere in project source or build output |
| `git.properties` | **Not present** — no `gradle-git-properties` plugin configured |
| `springBoot { buildInfo() }` in build.gradle | **Not present** in any module's build.gradle |
| `management.info.*` or `management.endpoint.info.*` config | **Not present** in any YAML across all modules |
| `/actuator/info` output | Returns empty `{}` on monitor-app (where endpoint is exposed) |
| engine-app info exposure | `/actuator/info` is **not exposed** — only default `/actuator/health` available |

**Impact:** Low. `/actuator/info` provides no useful information. Version `"2.0.0"` exists only as Java string literals in `MonitorOverviewService.java` and `EngineRuntimeControlService.java`, not sourced from build metadata. No way to determine which Git commit a running instance was built from without external CI tracking.

---

## 2. Health Indicator Implementations

**Status: ❌ Zero custom HealthIndicator beans exist**

| Indicator Type | monitor-app | engine-app | telegram-bot-app |
|---------------|-------------|------------|------------------|
| Custom `HealthIndicator` | 0 | 0 | N/A (no web server) |
| Custom `HealthContributor` | 0 | 0 | N/A |
| Custom `HealthAggregator` | 0 | 0 | N/A |
| `@Endpoint` for health | 0 | 0 | N/A |

**Auto-configured indicators (Spring Boot defaults):**

| Auto-configured Indicator | monitor-app | engine-app | Notes |
|--------------------------|-------------|------------|-------|
| `DiskSpaceHealthContributor` | ✅ Active | ✅ Active | Checks free disk space only |
| `SslHealthContributor` | ✅ Active | ✅ Active | Checks SSL/TLS certificates |
| `PingHealthIndicator` | ✅ Active | ✅ Active | Always present — just returns `{"status":"UP"}` |
| `DataSourceHealthIndicator` | ✅ Active | ❌ N/A | DataSource on classpath via JPA starter; checks SQLite connection |
| `WebSocketHealthIndicator` | ❌ No WebSocket | ❌ No WebSocket | Not auto-configured |

**Detailed behavior of auto-configured indicators:**

- **`DataSourceHealthIndicator`** (monitor-app only): Runs `SELECT 1` validation query against the SQLite database. If the DB connection fails (file missing, permissions wrong, WAL locked), this indicator returns `DOWN` and the overall health status degrades.
- **`DiskSpaceHealthContributor`** (both): Returns `DOWN` only if free space drops below 10MB (Spring Boot default threshold).
- **`SslHealthContributor`** (both): Returns `DOWN` if SSL certificates are expired or validation fails.
- **`PingHealthIndicator`** (both): Always returns `UP` — no actual health check.

---

## 3. Is Health UP When Credentials Are Missing?

**Status: ✅ YES — health is UP when credentials are missing**

- No health indicator checks credential presence
- Exchange credential verification is performed on-demand via `VenueDiagnosticsService.checkCredentials()` and `OperatorCredentialService`, triggered by the diagnostics UI or API call — not by the health endpoint
- `CredentialStorageStartupValidator` checks master key presence on startup (in prod-like profile) but this is a startup-time check, not a runtime health check
- In `CredentialAwareExecutionPort` (engine-app), missing credentials cause `submitOrder()` to return FAILED — but this is not reflected in health status

**Consequence:** A running instance with no API keys configured will report `UP` at `/actuator/health`, despite being incapable of executing any trades.

---

## 4. Is Health UP When Exchange Is Unreachable?

**Status: ✅ YES — health is UP when exchanges are unreachable**

- No health indicator checks exchange API connectivity
- Exchange reachability is checked on-demand by the diagnostics UI or by specific service calls — not reflected in health endpoint status
- The monitoring app can report `UP` while all 5 venue exchanges are unreachable

**Consequence:** Health status provides no signal about the system's ability to interact with external markets.

---

## 5. Is Health UP When DB Is Read-Only?

**Status: ⚠️ Depends — monitor-app MAY report DOWN if SQLite connection fails**

- **monitor-app**: The `DataSourceHealthIndicator` (auto-configured via `spring-boot-starter-actuator`) validates the SQLite JDBC connection. If the database file has read-only permissions or is otherwise inaccessible, the validation query (`SELECT 1`) fails and the health status returns `DOWN`.
  - However: SQLite read-only mode behavior is driver-dependent. If the SQLite JDBC driver opens the file successfully in read-only mode, the validation query may succeed even though writes fail — health would be UP despite the DB being effectively read-only.
- **engine-app**: No data source — not applicable.

**Consequence:** Existing health coverage for DB read-only is partial and SQLite-driver-dependent. No health indicator validates write capability.

---

## 6. Is Health UP When Clock Drift Is Excessive?

**Status: ✅ YES — health is UP regardless of clock drift**

- No health indicator, `Clock` monitoring, NTP sync check, or clock drift detection exists anywhere in the codebase
- The trading application depends on precise system time for:
  - Exchange order timestamps (API signing)
  - Funding rate entry timing (per-second precision for futures positions)
  - Trade journal timestamps (sequence ordering)
  - Execution interval timing (250ms scheduler tick)
- A clock drift of even a few seconds could cause:
  - API request signature rejection (HMAC timestamps)
  - Missed or premature entry windows
  - Confused trade journal ordering

**Consequence:** The system is completely blind to clock drift — a critical risk for a trading application where sub-second timing accuracy is essential for exchange API interactions.

---

## 7. Separate Trading-Readiness Indicator

**Status: ❌ No trading-readiness indicator exists; one is needed**

**Current gap:** `/actuator/health` aggregates only: disk space, SSL status, ping, and (on monitor-app) DB connectivity. None of these reflect the system's ability to execute trades.

**A `TradingReadinessHealthIndicator` should check:**

| Check | What It Validates | Priority |
|-------|------------------|----------|
| **Engine loop status** | Is `EngineRuntimeControlService.isExecutionLoopEnabled()` true? | **High** |
| **Live order mode** | Is `EngineRuntimeControlService.isLiveOrderEnabled()` true? | **High** |
| **Kill switch** | Is kill switch disengaged? | **High** |
| **Credentials present** | Are credentials loaded for configured venues? | **High** |
| **Monitor connectivity** (engine-app) | Can engine reach `/actuator/health` on monitor? | **Medium** |
| **Exchange reachability** | Are configured venue API endpoints responsive? | **Medium** |
| **Clock sync** | Is system clock within tolerance (e.g., ±5s of NTP)? | **Medium** |
| **DB writable** (monitor-app) | Can the DB accept writes? | **Medium** |
| **Scheduler health** | Is the 250ms loop running on time? | **Low** |

**Health status mapping:**

| Trading Readiness | Health Status | Notes |
|------------------|---------------|-------|
| All checks pass | `UP` | System fully capable of trading |
| Credentials missing | `DOWN` with detail | Cannot submit orders |
| Exchange unreachable | `DOWN` with detail | Cannot reach markets |
| Engine loop disabled intentionally | `OUT_OF_SERVICE` | Degraded — monitoring only |
| Clock drift excessive | `DOWN` with detail | Critical reliability risk |
| DB read-only | `DOWN` with detail (monitor-app) | Cannot record state |

**Recommendation:** Implement in `monitor-app` as it has visibility into both the domain state (DB, credentials) and can query engine for its status. The health aggregation already has status codes (`UP`, `DOWN`, `OUT_OF_SERVICE`, `UNKNOWN`) that map well to trading readiness semantics.

---

## 8. Health Status vs Actual Readiness to Trade — Comparison

| Readiness Factor | `/actuator/health` Reports | Actually Ready? | Gap |
|-----------------|---------------------------|----------------|-----|
| Execution loop enabled | **No check** — always UP | No, if loop=false | **Full gap — health always UP** |
| Live orders enabled | **No check** — always UP | No, if live-order-enabled=false | **Full gap** |
| Credentials present | **No check** — always UP | No, if keys missing | **Full gap** |
| Exchanges reachable | **No check** — always UP | No, if API down | **Full gap** |
| Clock synchronized | **No check** — always UP | No, if clock drifts | **Full gap** |
| DB writable | Partial — SQLite connection only | Depends on write perms | **Partial gap** |
| Disk space OK | Checked → DOWN if <10MB free | Probably not ready | Coverage adequate |
| SSL valid | Checked → DOWN if expired | Probably not ready | Coverage adequate |

**Verdict:** The application health endpoint provides effectively **no meaningful signal** about trading readiness. In all critical dimensions (loop status, live orders, credentials, exchange connectivity, clock sync), the health endpoint reports `UP` regardless of actual readiness.

---

## Recommendations

1. **Add `springBoot { buildInfo() }`** to all 3 app modules in `build.gradle` to generate `META-INF/build-info.properties`, giving `/actuator/info` build version, timestamp, and artifact coordinates
2. **Add `gradle-git-properties` plugin** (`com.gorylenko.gradle-git-properties`) to generate `git.properties` for commit SHA in `/actuator/info`
3. **Implement `TradingReadinessHealthIndicator`** in monitor-app that checks engine loop status, live order mode, credentials, exchange connectivity, and clock sync
4. **Add `management.endpoint.health.probes.enabled=true`** to shared config if K8s deployment is planned
5. **Consider a lightweight `ClockDriftHealthIndicator`** using NTP comparison (e.g., `java.time.Instant.now()` vs HTTP Date header from exchange responses)
