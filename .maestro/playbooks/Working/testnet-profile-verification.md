---
type: audit
title: `testnet` Profile — Exact Configuration Verification
created: 2026-07-13
tags:
  - audit
  - configuration
  - spring-boot
  - profiles
  - testnet
related:
  - '[[AUDIT-ROUND-3-03]]'
  - '[[profile-inventory]]'
  - '[[local-safe-profile-verification]]'
  - '[[application-properties-audit]]'
---

# `testnet` Profile — Exact Configuration Verification

## Scope

Verifies the exact configuration of the `testnet` profile. This profile exists **only** in `engine-app` (`engine-app/src/main/resources/application-testnet.yml`). Neither `monitor-app` nor `telegram-bot-app` have a testnet profile — when the engine runs in testnet mode, the monitor and telegram-bot remain in their default/active profile.

## Profile Activation

| Property | Value |
|----------|-------|
| Activation mechanism | `SPRING_PROFILES_ACTIVE=testnet` env var (must be explicitly set) |
| Default value | **NOT the default** — `local-safe` is the default via `build.gradle` |
| Scope | engine-app ONLY |
| Applied by | `./gradlew bootRunEngine -DSPRING_PROFILES_ACTIVE=testnet` or `SPRING_PROFILES_ACTIVE=testnet ./gradlew bootRunEngine` |
| Bootstrap token | Defaults to `funding-local-internal-token` (same as local-safe, from `build.gradle:14`) |
| Documentation | `docs/03-runtime-config.md` lines 193-209 (Engine Testnet Profile section); `docs/07-runbook.md` line 199 |

### Activation documentation (runbook)

```bash
ENGINE_GATE_TESTNET_API_KEY=xxx ENGINE_GATE_TESTNET_SECRET_KEY=yyy \
  SPRING_PROFILES_ACTIVE=testnet ./gradlew bootRunEngine
```

---

## engine-app — Exact Configuration

### File: `application-testnet.yml`

Eleven explicit property overrides:

```yaml
spring:
    config:
        activate:
            on-profile: testnet

engine:
    execution-loop-enabled: true
    execution-loop-interval-ms: 2000
    live-order-enabled: true
    kill-switch-enabled: false
    trading-venue-access-mode: testnet
    live-enabled-venues: gate
    max-notional-usd: 25
    metadata-max-age-minutes: 240
    latency-max-age-minutes: 1440
    monitor-base-url: ${MONITOR_INTERNAL_BASE_URL:http://localhost:8090}
    internal-token: ${INTERNAL_ENGINE_TOKEN:funding-local-internal-token}
    metrics-publish:
        enabled: false
```

### Override vs Default Comparison

| Property | Base Default (`application.yml`) | `testnet` Override | Difference |
|----------|--------------------------------|--------------------|------------|
| `engine.execution-loop-enabled` | `${ENGINE_EXECUTION_LOOP_ENABLED:false}` | `true` | ⚠️ **CHANGED**: OFF → ON |
| `engine.execution-loop-interval-ms` | `${ENGINE_EXECUTION_LOOP_INTERVAL_MS:1000}` | `2000` | Changed: 1000 → 2000 (slower tick) |
| `engine.live-order-enabled` | `${ENGINE_LIVE_ORDER_ENABLED:false}` | `true` | ⚠️ **CHANGED**: OFF → ON |
| `engine.kill-switch-enabled` | `${ENGINE_KILL_SWITCH_ENABLED:true}` | `false` | ⚠️ **CHANGED**: ON → OFF |
| `engine.trading-venue-access-mode` | `${TRADING_VENUE_ACCESS_MODE:testnet}` | `testnet` | Same value (base default is already testnet) |
| `engine.live-enabled-venues` | `${ENGINE_LIVE_ENABLED_VENUES:bybit,gate}` | `gate` | Changed: bybit+gate → gate only |
| `engine.max-notional-usd` | `${ENGINE_MAX_NOTIONAL_USD:25}` | `25` | Same value |
| `engine.metadata-max-age-minutes` | `${ENGINE_METADATA_MAX_AGE_MINUTES:240}` | `240` | Same value (explicit for clarity) |
| `engine.latency-max-age-minutes` | `${ENGINE_LATENCY_MAX_AGE_MINUTES:1440}` | `1440` | Same value (explicit for clarity) |
| `engine.monitor-base-url` | `${MONITOR_INTERNAL_BASE_URL:http://localhost:8090}` | `${MONITOR_INTERNAL_BASE_URL:http://localhost:8090}` | Same expression |
| `engine.internal-token` | `${INTERNAL_ENGINE_TOKEN:}` | `${INTERNAL_ENGINE_TOKEN:funding-local-internal-token}` | ⚠️ **Default token set** (base default is empty string) |
| `engine.metrics-publish.enabled` | `${ENGINE_METRICS_PUBLISH_ENABLED:false}` | `false` | Same value (explicit for clarity) |

### Properties NOT overridden (inherited from `application.yml`)

| Property | Default Value | Assessment |
|----------|---------------|------------|
| `server.port` | `${ENGINE_SERVER_PORT:8091}` | ✅ Standard |
| `engine.execution-scheduler-tick-ms` | `${ENGINE_EXECUTION_SCHEDULER_TICK_MS:250}` | ✅ 250ms tick — same as local-safe |
| `engine.metrics-publish.interval-ms` | `${ENGINE_METRICS_PUBLISH_INTERVAL_MS:15000}` | ✅ Not used (metrics disabled) |
| `engine.bybit.testnet-base-url` | `${BYBIT_TESTNET_BASE_URL:https://api-testnet.bybit.com}` | ✅ Testnet URL |
| `engine.bybit.production-base-url` | `${BYBIT_PRODUCTION_BASE_URL:https://api.bybit.com}` | ✅ Not used (gate only) |
| `engine.gate.testnet-base-url` | `${GATE_TESTNET_BASE_URL:https://api-testnet.gateapi.io/api/v4}` | ✅ Testnet URL |
| `engine.gate.production-base-url` | `${GATE_PRODUCTION_BASE_URL:https://fx-api.gateio.ws/api/v4}` | ✅ Not used |
| `engine.okx.testnet-base-url` | `${OKX_TESTNET_BASE_URL:https://www.okx.com}` | ⚠️ Same URL for testnet/production |
| `engine.okx.production-base-url` | `${OKX_PRODUCTION_BASE_URL:https://www.okx.com}` | ⚠️ Same URL for testnet/production |
| `engine.kucoin.testnet-base-url` | `${KUCOIN_TESTNET_BASE_URL:https://api-sandbox.kucoin.com}` | ✅ Testnet URL (not in enabled venues) |
| `engine.kucoin.production-base-url` | `${KUCOIN_PRODUCTION_BASE_URL:https://api-futures.kucoin.com}` | ✅ Not used |
| `engine.bitget.testnet-base-url` | `${BITGET_TESTNET_BASE_URL:https://api.bitget.com}` | ⚠️ Same URL for testnet/production |
| `engine.bitget.production-base-url` | `${BITGET_PRODUCTION_BASE_URL:https://api.bitget.com}` | ⚠️ Same URL for testnet/production |

### All 13 engine-app beans load

All engine-app beans are unconditional (except `EngineMetricsPublisher` which is gated by `@ConditionalOnProperty`). In testnet profile:
- `EngineMetricsPublisher` — **NOT created** (`metrics-publish.enabled: false` → `@ConditionalOnProperty` does not match)
- All other 12 beans — **created** (unconditional)

### Network connections made by engine-app in testnet

| Connection | Direction | Status | Notes |
|-----------|-----------|--------|-------|
| Monitor (port 8090, plan fetch) | Local | ✅ Active | Default `monitor-base-url: http://localhost:8090` |
| Exchange APIs (Gate testnet, live orders) | Outbound | ⚠️ **Active** (if credentials present) | Gate testnet URL: `https://api-testnet.gateapi.io/api/v4` |
| Exchange APIs (latency probe) | Outbound | ✅ Active (for Gate if connected) | Part of execution loop flow |

### Credential check flow in testnet

Even with `live-order-enabled: true`, the `CredentialAwareExecutionPort` (extends `LiveExchangeExecutionPort`) still checks for credentials via `missingCredentialsReason()`:

1. **Missing API key or secret** → `OrderAttemptStatus.FAILED` with "Missing engine credentials"
2. **Missing passphrase (Bitget/OKX/Kucoin)** → `OrderAttemptStatus.FAILED` with "Missing engine passphrase"
3. **Credentials present** → proceeds to `liveGateFailure()` checks
4. **Kill switch enabled** → `OrderAttemptStatus.FAILED` with "kill switch is enabled" (kill switch is OFF in testnet, so this guard is removed)
5. **Venue not in enabled set** → `OrderAttemptStatus.FAILED` ("Venue X is not enabled")
6. **Notional > max** → `OrderAttemptStatus.FAILED`
7. **Non-SHORT entry** → `OrderAttemptStatus.FAILED` ("entry side must be SHORT")
8. **Stale metadata** → `OrderAttemptStatus.FAILED`

### Testnet-specific HTTP headers for testnet-mode venues

When `engine.trading-venue-access-mode` = `testnet`, the following venues add testnet-specific headers:

| Venue | Testnet Indicator | Header Used | LiveExchangeExecutionPort.java Reference |
|-------|-------------------|-------------|------------------------------------------|
| OKX | `x-simulated-trading: 1` | Header on all private API calls | Lines 319, 329-332, 377-380 |
| Bitget | `paptrading: 1` | Header on all private API calls | Lines 529, 538-541, 581-584 |
| Bybit | Different base URL | Uses `api-testnet.bybit.com` URL | Lines 916, 925 (`baseUrl` method) |
| Gate | Different base URL | Uses `api-testnet.gateapi.io` URL | Lines 918 (`baseUrl` method) |
| KuCoin | Different base URL | Uses `api-sandbox.kucoin.com` URL | Lines 921 (`baseUrl` method) |

**Important**: OKX and Bitget use the **same base URL** for both testnet and production modes. The only distinction is the testnet-specific header. If `trading-venue-access-mode` were somehow set to `production` while running against testnet credentials, OKX and Bitget would hit production endpoints without the testnet header — a potential risk.

---

## Monitor Behavior During Engine Testnet Mode

Since monitor-app has **no `testnet` profile**, when the engine runs with `SPRING_PROFILES_ACTIVE=testnet`, the monitor continues with whichever profile is active for it:

| Scenario | Monitor Profile | Monitor Behavior |
|----------|----------------|------------------|
| Local dev testnet run | `local-safe` (default via `bootRunMonitor` or ENV) | Auth OFF, credentials OFF, metadata sync OFF |
| Dedicated testnet deployment | `staging` or `prod-like` | Auth ON, credentials ON, metadata sync ON |
| Docker Compose testnet | User sets `SPRING_PROFILES_ACTIVE=testnet` for engine service | Engine gets testnet; monitor gets `prod-like` (from `docker-compose.yml`) |

### Monitor property interaction

| Monitor Property | Default | Impact on Testnet |
|-----------------|---------|-------------------|
| `TRADING_VENUE_ACCESS_MODE` | `production` (platform-core.yml) | Monitor's own access mode — does NOT affect engine. Used for venue metadata URLs and diagnostics. |
| `trading.candidate-source.enabled` | `true` (matchIfMissing) | Candidate source polls external API regardless of engine profile |
| `MONITOR_INTERNAL_BASE_URL` | `http://localhost:8090` | Engine needs this to reach monitor for plans |

**Key point**: Monitor's `TRADING_VENUE_ACCESS_MODE` defaulting to `production` is a separate concern from engine's `trading-venue-access-mode: testnet`. The monitor does not submit orders — it manages credentials and displays data. The production access mode in monitor determines which venue metadata URLs are used (e.g., Bybit production metadata URL vs testnet), which affects instrument lookup but not order execution.

---

## Safety Analysis

### Critical safety dimensions

| Safety Dimension | Status in `testnet` | Detail |
|-----------------|---------------------|--------|
| **Execution loop** | 🔴 ON | `execution-loop-enabled: true` — ticks at 2000ms interval |
| **Live orders** | 🔴 ON | `live-order-enabled: true` — can submit real (testnet) orders |
| **Kill switch** | 🔴 OFF | `kill-switch-enabled: false` — no emergency stop |
| **Credentials required** | 🟢 YES | `missingCredentialsReason()` returns FAILED if credentials absent |
| **Notional capped** | 🟢 $25 USD | `max-notional-usd: 25` — minimal financial risk |
| **Venue restricted** | 🟢 Gate only | `live-enabled-venues: gate` — only Gate testnet |
| **Access mode** | 🟢 Testnet | `trading-venue-access-mode: testnet` — testnet URLs and headers |
| **Metrics published** | 🟢 OFF | `metrics-publish.enabled: false` — no monitoring data sent |
| **Monitor auth** | ⚠️ Varies | Depends on monitor's profile (no testnet profile for monitor) |
| **Monitor credentials** | ⚠️ Varies | Depends on monitor's profile (no testnet profile for monitor) |

### Data flow safety

```
Engine loop ticks (2000ms)
  → Fetch plans from monitor (/internal/v1/engine/plans)
    → Monitor returns plans for armed trades (if monitor credentials present)
  → Check runtime control (loop ON, live orders ON, kill switch OFF)
  → Check credentials (FAILED if missing)
  → Submit order to Gate testnet (max $25 notional)
    → Record OrderAttempt
    → Update trade state via monitor API
```

### Risk: OKX and Bitget same URL for testnet/production

Both OKX and Bitget use the same base URL for testnet and production modes. The only distinction is the `x-simulated-trading: 1` (OKX) or `paptrading: 1` (Bitget) header. These headers are added when `engine.trading-venue-access-mode` is `testnet`. **Gate** and **Bybit** use different URLs for testnet vs production, so they are inherently safer.

**Mitigation**: Currently only Gate is in `live-enabled-venues: gate` for testnet, so OKX/Bitget are not a concern. If OKX or Bitget are added to `live-enabled-venues` in a future update, this risk would need review.

### Risk: Empty credentials still cause FAILED

If no `ENGINE_CREDENTIALS_GATE_*` env vars are set, the execution loop will run, fetch plans, attempt to submit orders, and immediately fail with `"Missing engine credentials for gate."`. This produces `OrderAttempt` records with `FAILED` status but does not cause any exchange interaction. The loop continues to tick at 2000ms, generating `FAILED` attempts on each tick.

---

## Configuration Merge Summary

The `testnet` profile is the **most permissive** profile — it explicitly enables both the execution loop and live order submission while disabling the kill switch. Key mitigations: only Gate testnet is enabled, notional capped at $25, credentials still checked, and access mode set to testnet.

| Domain | Status | Detail |
|--------|--------|--------|
| Engine execution loop | 🔴 Explicitly ON | `execution-loop-enabled: true` |
| Engine live orders | 🔴 Explicitly ON | `live-order-enabled: true` |
| Engine kill switch | 🔴 Explicitly OFF | `kill-switch-enabled: false` |
| Engine metrics publishing | 🟢 Explicitly OFF | `metrics-publish.enabled: false` |
| Trading access mode | 🟢 Testnet | `trading-venue-access-mode: testnet` |
| Live-enabled venues | 🟢 Gate only | `live-enabled-venues: gate` (default is bybit,gate) |
| Max notional | 🟢 $25 | `max-notional-usd: 25` |
| Loop interval | 🟢 2000ms | Slower default to prevent rapid-fire attempts |
| Internal token default | ⚠️ Set to dev token | Same as local-safe: `funding-local-internal-token` |
| Monitor profile | ⚠️ Unspecified | Must be set independently (no monitor testnet profile) |

---

## Findings

### Finding 1: Missing from CLAUDE.md and README.md profile tables

**Severity:** Medium (documentation gap)

The `testnet` profile is entirely absent from the profile tables in both `CLAUDE.md` (lines 112-119) and `README.md` (lines 119-125). Both tables only list 3 profiles (`local-safe`, `staging`, `prod-like`). A developer or operator relying on these documents would not know that a testnet execution profile exists.

**Impact:** Operators could be unaware that running the engine with `SPRING_PROFILES_ACTIVE=testnet` enables loop + live orders + removes kill switch. If they're following a "safe" docker-compose or env example, they would use `prod-like` which has none of these enabled.

**Recommendation:** Add a `testnet` row to both CLAUDE.md and README.md profile tables:
```
| `testnet` | OFF¹ | OFF¹ | ON | ON | OFF² |
```
With footnotes: ¹ Depends on monitor's profile (no testnet profile for monitor); ² Metrics publishing disabled.

### Finding 2: `testnet` enables loop + live orders + disables kill switch — NO additional safety net

**Severity:** Medium (design concern)

The testnet profile removes **all three** runtime guards:
1. Execution loop: OFF → ON
2. Live orders: OFF → ON  
3. Kill switch: ON → OFF

There is no additional safety mechanism added to compensate. The only remaining protections are:
- Credential check (credentials must be present — otherwise FAILED)
- Notional cap ($25)
- Single enabled venue (Gate)

If a user accidentally activates testnet with production credentials (possible if `ENGINE_CREDENTIALS_GATE_API_KEY` points to a production key), there is no additional guard to prevent real trading — though the Gate production URL is different from testnet, and the access mode would route to testnet URL.

**Recommendation:** Consider adding a double-confirmation mechanism such as requiring a separate env var `ENGINE_TESTNET_CONFIRMED=true` to be set alongside the profile, to prevent accidental activation.

### Finding 3: No `@Profile` annotation guards — profile behavior is entirely property-driven

**Severity:** Informational

Like all other profiles in this codebase, `testnet` does not use `@Profile` annotations. Profile behavior is entirely driven by property values in `application-testnet.yml`. This is the established pattern and works correctly, but it means:
- All 13 engine-app beans still load (12 unconditional + 1 `@ConditionalOnProperty`)
- No bean is prevented from loading by the testnet profile
- The `EngineMetricsPublisher` is excluded by `@ConditionalOnProperty` (not `@Profile`)

### Finding 4: Loop interval 2000ms vs scheduler tick 250ms

**Severity:** Low (configuration clarity)

The testnet profile sets `execution-loop-interval-ms: 2000` (the business-level loop interval) but does **not** override `execution-scheduler-tick-ms: 250` (the Spring scheduling tick rate). The scheduler ticks at 250ms but the business loop only processes every 2000ms. This is intentional and correct — the scheduler checks the loop guard every 250ms, and the business logic respects the 2000ms interval. However, the tick overhead (250ms check with fast no-op) is minor.

### Finding 5: Internal token has a default in testnet

**Severity:** Low (same as local-safe)

The testnet profile sets `engine.internal-token: ${INTERNAL_ENGINE_TOKEN:funding-local-internal-token}`, which provides a default dev token. The base `application.yml` has no default (`${INTERNAL_ENGINE_TOKEN:}` — empty). This means testnet runs with a known dev token by default, which is acceptable for local testnet testing but means the token must be explicitly changed in any multi-operator deployment.

### Finding 6: Monitor credential endpoints available based on monitor profile

**Severity:** Low (operational awareness)

Since monitor has no testnet profile, the engine credentials API endpoint (`/internal/v1/engine/credentials`) availability depends on the monitor's active profile:
- Monitor in `local-safe` → `credentials.storage.enabled: false` → **no credentials available** → engine gets FAILED for all orders
- Monitor in `staging`/`prod-like` → `credentials.storage.enabled: true` → credentials available if operator has stored them

This is intentional — it means testnet execution requires a monitor with credential storage enabled.

### Finding 7: All three runtime guards removed simultaneously

**Severity:** Medium (architectural risk)

When `testnet` is active, `execution-loop-enabled=true`, `live-order-enabled=true`, and `kill-switch-enabled=false`. These three flags are the **only** runtime guards before live exchange API calls. Removing all three simultaneously means the developer has no emergency backstop if unexpected behavior occurs — they would need to stop the JVM process to halt order submission.

**Comparison with other profiles:**
- `local-safe`: loop OFF, live OFF, kill-switch ON → 3 guarantees
- `staging`: loop OFF, live OFF, kill-switch ON → 3 guarantees (not running anyway)
- `prod-like`: loop OFF (requires ENV), live OFF (requires ENV), kill-switch ON → ENV gates
- `testnet`: loop ON, live ON, kill-switch OFF → 0 guarantees

---

## Conclusion

**The `testnet` profile enables full execution capability against Gate testnet.** It is designed for active testing of the order submission pipeline with real exchange API calls (against testnet endpoints). 

### Safety verdict

| Dimension | Status | Notes |
|-----------|--------|-------|
| Loop runs | 🔴 Yes | 2000ms interval |
| Live orders can be submitted | 🔴 Yes | If Gate testnet credentials are provided |
| Real money at risk | 🟢 No | Testnet funds only, $25 max notional |
| Production URL can be hit | 🟢 Unlikely | Gate testnet URL is different from production; OKX/Bitget use headers but not enabled |
| Credentials can be accidentally production keys | ⚠️ Possible | Operator must ensure credentials are testnet keys |
| Monitor auth/credentials | ⚠️ Depends | Monitor profile must be set independently |
| CLI/Profile activation | 🟢 Explicit | Must be explicitly set — never the default |

**Overall: Safe for its intended purpose (Gate testnet execution testing) if the operator is aware of what they're enabling.** The main risk is an operator activating this profile without understanding that it removes all runtime guards. Documentation must clearly state: "This profile removes all three execution guards — use only for deliberate testnet testing."

### Recommended action items

1. **Add `testnet` to CLAUDE.md and README.md profile tables** — operators need to see this profile exists
2. **Consider a double-confirmation env var** (`ENGINE_TESTNET_CONFIRMED=true`) as an additional safety measure
3. **Document the monitor profile dependency** — testnet testing requires monitoring in staging/prod-like mode for credentials
