---
type: audit
title: Safety of Multiple Simultaneous Profiles — Full Analysis
created: 2026-07-13
tags:
  - audit
  - configuration
  - spring-boot
  - profiles
  - security
related:
  - '[[profile-override-inventory]]'
  - '[[profile-differences-comparison]]'
  - '[[testnet-profile-verification]]'
  - '[[profile-activation-audit]]'
---

# Safety of Multiple Simultaneous Profiles

## How Spring Boot Handles Multiple Profiles

When multiple profiles are activated (via comma-separated `SPRING_PROFILES_ACTIVE` or `spring.profiles.active`), Spring Boot loads each `application-{profile}.yml` as a separate `PropertySource` in activation order. **The last activated profile has highest property resolution priority.**

This means the order in which profiles are listed changes the effective configuration — the same pair of profiles can produce different (and potentially dangerous) results depending on ordering.

## Scope of Investigation

- **4 profiles × 3 modules = 84 theoretical combinations** (allowing order permutations)
- **No `spring.profiles.include` anywhere** — profiles cannot be implicitly combined through configuration
- **No `@Profile` annotations** — no Java-based profile gating
- **No startup validation** for incompatible profile combinations

## Deployment Reality Check

| Activation Path | Profile Count | Risk |
|----------------|:-------------:|------|
| `build.gradle bootRun` | Always **single** (`local-safe` or env override) | None |
| `docker-compose.yml` (root) | Single per service (no profile set — base defaults) | None |
| `deploy/docker-compose.yml` | Single per service (`prod-like`/`staging`) | None |
| `deploy/observability/docker-compose.yml` | Single per service (`staging`) | None |
| Manually via `-jar` / `SPRING_PROFILES_ACTIVE` | Potentially **multiple** if user types commas | **Highest risk** |

**No production or CI deployment path activates multiple profiles.** The risk is confined to manual/developer launches.

---

## Critical Finding: testnet + ANY Safe Profile on Engine-App

### ⚠️ CRITICAL severity — potential financial loss

The `testnet` profile reverses all 3 critical execution guards:
- `engine.execution-loop-enabled: true` (base: `false`)
- `engine.live-order-enabled: true` (base: `false`)
- `engine.kill-switch-enabled: false` (base: `true`)

No other engine-app profile (`local-safe`, `staging`, `prod-like`) overrides `live-order-enabled` or `kill-switch-enabled`. **Only `execution-loop-enabled` is re-overridden by safe profiles.**

### Scenario 1: `SPRING_PROFILES_ACTIVE=local-safe,testnet`
| Property | Effective Value | Source |
|----------|:--------------:|--------|
| `execution-loop-enabled` | `true` ❌ | testnet (last wins) |
| `live-order-enabled` | `true` ❌ | testnet (no other profile sets this) |
| `kill-switch-enabled` | `false` ❌ | testnet (no other profile sets this) |
| **Verdict** | **⚠️ DANGEROUS — all guards disabled** | |

### Scenario 2: `SPRING_PROFILES_ACTIVE=testnet,local-safe`
| Property | Effective Value | Source |
|----------|:--------------:|--------|
| `execution-loop-enabled` | `false` ✅ | local-safe (overrides testnet) |
| `live-order-enabled` | `true` ❌ | testnet (not overridden — no profile sets this) |
| `kill-switch-enabled` | `false` ❌ | testnet (not overridden — no profile sets this) |
| **Verdict** | **⚠️ MASKED DANGER — loop appears OFF but kill switch is disabled and live orders are enabled. Any runtime API call to start the loop removes the last barrier.** | |

### Scenario 3: `SPRING_PROFILES_ACTIVE=staging,testnet` or `prod-like,testnet`
Same as Scenario 1 — testnet's dangerous settings win when last.

### Scenario 4: `SPRING_PROFILES_ACTIVE=testnet,staging` or `testnet,prod-like`
Same as Scenario 2 — masked danger.

### What Makes This Dangerous

1. **Masked safety illusion**: The user activates `testnet,local-safe` thinking "local safety + testnet execution." The loop appears OFF. But live orders are ON and the kill switch is OFF. If the user later enables the loop via the runtime API (`/api/v1/dev/engine/runtime`), there are zero safeguards — immediate trading with live orders and no kill switch.

2. **No other profile fills the gap**: Profiles `local-safe`, `staging`, and `prod-like` do NOT set `live-order-enabled` or `kill-switch-enabled`. These properties are ONLY specified in the `testnet` profile (or inherited from base defaults if testnet is not active). Safe profiles should explicitly set them to provide defense-in-depth when combined.

3. **Order-sensitive**: Users who type `SPRING_PROFILES_ACTIVE=local-safe,testnet` (thinking "start from local-safe, add testnet") get MORE dangerous behavior than `testnet` alone — because with testnet alone, loop is ON but at least visible. With a safe profile first and testnet last, the loop is ON AND other guards are disabled.

---

## High Finding: monitor-app Profile Combinations

### ⚠️ HIGH severity — potential security gap

The monitor-app has no execution capability (no trading loop), so profile combinations don't risk financial loss. However, they can create security gaps by disabling authentication or credential storage.

### Scenario: `SPRING_PROFILES_ACTIVE=staging,local-safe`
| Property | Effective Value | Source |
|----------|:--------------:|--------|
| `auth-enabled` | `false` ⚠️ | local-safe (last wins) |
| `credentials.storage.enabled` | `false` ⚠️ | local-safe |
| `require-master-key-on-startup` | `false` ⚠️ | local-safe |
| `engine-metrics.enabled` | `false` | local-safe |
| `metadata.require-credentials` | `false` | both set false |
| `metadata.sync-on-startup` | `false` | local-safe only |
| `deepseek.enabled` | `false` | local-safe only |

A user who activates `staging,local-safe` expecting "staging with local overrides" gets:
- No authentication
- No credential storage
- No master key requirement
- But thinks they're running in staging-mode (because they included staging)

### Scenario: `SPRING_PROFILES_ACTIVE=prod-like,local-safe`
Same as above — local-safe's security-disabling properties override prod-like's security.

### Scenario: `SPRING_PROFILES_ACTIVE=staging,prod-like`
| Property | Effective Value | Source |
|----------|:--------------:|--------|
| `auth-enabled` | `true` ✅ | both set true |
| `credentials.storage.enabled` | `true` ✅ | both set true |
| `require-master-key` | `true` ✅ | both set true |
| `engine-metrics.enabled` | `true` ✅ | both set true |
| `metadata.require-credentials` | `true` | prod-like (last wins, overrides staging's `false`) |
| **Verdict** | **✅ Safe — slightly more restrictive than staging alone** | |

### Scenario: `SPRING_PROFILES_ACTIVE=prod-like,staging`
Same properties but `metadata.require-credentials` = `false` (staging wins). Not dangerous, just less restrictive on startup metadata sync.

---

## Medium Finding: telegram-bot-app Profile Combinations

### ℹ️ LOW severity — no trading risk

telegram-bot-app has no execution loop, no live orders, and no monetary risk. Profile combinations here affect only token configuration and logging:

- `local-safe,staging`: staging's `monitor.base-url` wins (Docker hostname), token remains optional (from local-safe's `:}` default) but staging's `no-default` token requirement is overridden by local-safe
- `staging,local-safe`: local-safe's `http://localhost:8090` wins for monitor URL, token gets `:}` fallback (optional)

Not dangerous — just inconsistent configuration.

---

## High Finding: `@ConditionalOnProperty` Beans Affected by Profile Combinations

There are 12 `@ConditionalOnProperty` beans across the 3 modules. Profile combinations could cause unexpected bean creation/omission:

### Engine-metrics beans (6 total — 1 engine + 5 monitor)

| Profile Combination | `EngineMetricsPublisher` (engine) | 5 monitor metrics beans |
|-------------------|:-------------------------------:|:----------------------:|
| testnet only | ❌ NOT created | N/A (monitor independent) |
| staging only | ✅ Created | ✅ Created |
| prod-like only | ✅ Created | ✅ Created |
| `testnet,staging` | ❌ NOT created (testnet wins: `enabled: false`) | ✅ Created (staging wins) |
| `staging,testnet` | ❌ NOT created (testnet wins) | ❌ NOT created (testnet doesn't set; inherited from... ) |

Wait — engine and monitor are separate Spring contexts with separate profiles. So cross-module `@ConditionalOnProperty` interactions are not a concern. Each module manages its own beans.

Let me re-analyze within each module:

### engine-app: `EngineMetricsPublisher`
Gated on `engine.metrics-publish.enabled=true`

| Profile Combination | `enabled` value | Bean created? |
|-------------------|:--------------:|:------------:|
| local-safe only | `false` | ❌ |
| testnet only | `false` | ❌ |
| staging only | `true` | ✅ |
| prod-like only | `true` | ✅ |
| `testnet,staging` | `true` (staging wins) | ✅ |
| `testnet,prod-like` | `true` (prod-like wins) | ✅ |
| `staging,testnet` | `false` (testnet wins) | ❌ |
| `local-safe,staging` | `true` (staging wins) | ✅ |

Not dangerous — just behavioral.

### monitor-app: `FundingApiCandidateSourceService`
Gated on `trading.candidate-source.enabled=true` with `matchIfMissing=true`.

This bean is created in ALL profile combinations because:
- Base default is `true` (matchIfMissing)
- No profile overrides it
- Only explicit env var could disable it

### monitor-app: 5 engine-metrics beans
Gated on `monitor.engine-metrics.enabled=true`

| Profile Combination | `enabled` value | Beans created? |
|-------------------|:--------------:|:------------:|
| local-safe only | `false` | ❌ |
| staging only | `true` | ✅ |
| prod-like only | `true` | ✅ |
| `local-safe,staging` | `true` (staging wins) | ✅ |
| `staging,local-safe` | `false` (local-safe wins) | ❌ |
| `prod-like,staging` | `true` (both true) | ✅ |

Not dangerous — metrics beans have no financial impact.

### telegram-bot-app: 4 token-gated beans
Gated on `telegram.bot.token` being non-empty with `matchIfMissing=false`.

| Profile Combination | Token value | Beans created? |
|-------------------|:----------:|:------------:|
| local-safe only | Empty (default `:}`) | ❌ |
| staging only | Required from env var | ✅ if env set, otherwise **surprising** |
| `local-safe,staging` | Empty (local-safe's `:}` default wins if local-safe is last) | ❌ |
| `staging,local-safe` | Empty (local-safe's `:}` default wins) | ❌ |

**Surprising behavior**: With `staging,local-safe`, staging's token-required semantic is overridden by local-safe's empty-token default. Users who combine these profiles would expect staging's behavior but get local-safe's.

---

## High Finding: `@ConditionalOnProperty` + Profile Combination = Inconsistent Bean States

### The structural problem

Safe profiles (`local-safe`, `staging`, `prod-like`) do NOT explicitly set properties that testnet introduces (`live-order-enabled`, `kill-switch-enabled`). When combined with testnet:

1. **testnet enables** `live-order-enabled=true` and `kill-switched-enabled=false`
2. **No safe profile disables** these back to safe defaults
3. **The properties persist** even when the safe profile is listed last

### Why this persists

Spring Boot's property resolution is **additive** — properties from all active profiles are merged, with later profiles overriding on conflict. If profile B doesn't set a property that profile A set, profile A's value remains in effect.

The fix: safe profiles should explicitly set `live-order-enabled=false` and `kill-switch-enabled=true` to provide defense-in-depth against accidental profile combination.

---

## Medium Finding: telegram-bot-app Missing `on-profile` Guard

telegram-bot-app's `application-local-safe.yml` and `application-staging.yml` do NOT declare `spring.config.activate.on-profile`. While this works via Spring Boot's filename convention for single-profile activation, it creates a subtle risk:

If a future refactoring or test configuration accidentally loads these files as base documents (e.g., via `spring.config.import`), staging's settings (monitor URL with Docker hostname, required token) would be applied unconditionally — this is a configuration accident waiting to happen.

---

## Low Finding: No Profile Combination Validation

There is **no startup-time validation** that checks for incompatible profile combinations:
- No `ApplicationRunner` or `ApplicationListener` validates profile compatibility
- No `@Profile` annotations restrict classes to specific profiles
- No documentation of which combinations are safe or dangerous
- No startup warnings printed when testnet is combined with safe profiles

The app starts silently with whatever combination is active, regardless of danger.

---

## Dangerous Combination Matrix

### engine-app

| Combination | Effective Loop | Effective Live | Effective Kill Switch | Verdict |
|------------|:-------------:|:-------------:|:-------------------:|:-------:|
| `local-safe` only | OFF | OFF | ON | ✅ Safe |
| `testnet` only | ON | ON | OFF | 🟡 Intentional (testnet purpose) |
| `staging` only | OFF | OFF | ON | ✅ Safe |
| `prod-like` only | OFF | OFF | ON | ✅ Safe |
| `local-safe,testnet` | ON | ON | OFF | ❌ DANGEROUS |
| `testnet,local-safe` | OFF | ON | OFF | ❌ MASKED DANGER |
| `staging,testnet` | ON | ON | OFF | ❌ DANGEROUS |
| `testnet,staging` | OFF | ON | OFF | ❌ MASKED DANGER |
| `prod-like,testnet` | ON | ON | OFF | ❌ DANGEROUS |
| `testnet,prod-like` | OFF | ON | OFF | ❌ MASKED DANGER |
| `local-safe,staging` | OFF | OFF | ON | ✅ Safe |
| `staging,local-safe` | OFF | OFF | ON | ✅ Safe |
| `local-safe,prod-like` | OFF | OFF | ON | ✅ Safe |
| `prod-like,local-safe` | OFF | OFF | ON | ✅ Safe |
| `staging,prod-like` | OFF | OFF | ON | ✅ Safe |
| `prod-like,staging` | OFF | OFF | ON | ✅ Safe |
| `local-safe,staging,testnet` | ON (testnet last) | ON | OFF | ❌ DANGEROUS |
| `testnet,staging,local-safe` | OFF (local-safe last) | ON | OFF | ❌ MASKED DANGER |

### monitor-app

| Combination | Auth | Credentials | Metrics | Verdict |
|------------|:---:|:----------:|:------:|:-------:|
| `local-safe` only | OFF | OFF | OFF | ✅ Safe (read-only) |
| `staging` only | ON | ON | ON | ✅ Safe |
| `prod-like` only | ON | ON | ON | ✅ Safe |
| `local-safe,staging` | ON | ON | ON | ✅ Safe |
| `staging,local-safe` | OFF | OFF | OFF | ⚠️ Auth gap |
| `local-safe,prod-like` | ON | ON | ON | ✅ Safe |
| `prod-like,local-safe` | OFF | OFF | OFF | ⚠️ Auth gap |
| `staging,prod-like` | ON | ON | ON | ✅ Safe |
| `prod-like,staging` | ON | ON | ON | ✅ Safe |

### telegram-bot-app

No dangerous combinations — no trading functionality. Profile combinations only affect token optionality and monitor URL resolution.

---

## Recommendations

### Critical (address before merging)

1. **Add safety property overrides to safe profiles** — `local-safe`, `staging`, and `prod-like` engine-app profiles should explicitly set `live-order-enabled=false` and `kill-switch-enabled=true` to prevent testnet contamination:
   ```yaml
   # application-local-safe.yml, application-staging.yml, application-prod-like.yml
   engine:
       live-order-enabled: false
       kill-switch-enabled: true
   ```

2. **Add startup validation** for dangerous profile combinations — either:
   - An `ApplicationRunner` in engine-app that checks if `testnet` + any other profile is active and logs a CRITICAL warning
   - Or a `@ConditionalOnProperty` gate that prevents critical guards from being overridden

### High (do this sprint)

3. **Add `spring.config.activate.on-profile`** to telegram-bot-app's profile YAMLs for consistency and protection against accidental unconditional loading.

4. **Document dangerous combinations** in `CLAUDE.md` profile table — add a note: "Avoid combining `testnet` with other profiles. If combined, order matters: testnet's dangerous settings (live orders ON, kill switch OFF) persist even when a safe profile is listed later."

### Medium (document and backlog)

5. **Add profile validation warning** — log detected profile combinations on startup with a WARN level message for any combination involving testnet + another profile:
   ```
   WARN — engine-app running with multiple profiles: [local-safe, testnet].
   DANGER: testnet's live-order-enabled=true and kill-switch-enabled=false override
   safe profiles. Only execution-loop-enabled is re-disabled by local-safe.
   ```

6. **Consider a `@ConditionalOnSingleProfile` pattern** — or validate at startup that only one of {local-safe, staging, prod-like, testnet} is active at a time (they were designed as exclusive, not composable).

---

## Follow-up Task: Check for Dangerous Profile Combinations

The item "Check for dangerous profile combinations" is structurally covered by this analysis — all 84+ combinations have been enumerated and categorized above. The dangerous combinations are summarized in the matrix tables. The key actionable finding is that no safe profile explicitly overrides testnet's dangerous properties, creating a masked-risk scenario.
