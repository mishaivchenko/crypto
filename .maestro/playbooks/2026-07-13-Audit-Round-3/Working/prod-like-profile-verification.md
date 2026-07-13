# prod-like Profile — Exact Configuration Verification

**Date:** 2026-07-13

## Summary

✅ **Safety verdict: safe for production-adjacent use** — no automatic execution risk. Execution loop and live orders default to OFF; must be explicitly enabled via environment variables.

## Per-Module Configuration

### monitor-app (`application-prod-like.yml`)

| Property | Value | Effect |
|----------|-------|--------|
| `security.operators.auth-enabled` | `true` | All operator endpoints require authentication |
| `credentials.storage.enabled` | `true` | AES-GCM encrypted credential database active |
| `credentials.storage.require-master-key-on-startup` | `true` | App fails startup if master key env var is missing |
| `monitor.engine-metrics.enabled` | `true` | Accepts metrics snapshots from engine |
| `trading.metadata.require-credentials-on-startup` | `true` | Metadata sync requires valid credentials to start |

**Total overrides:** 5 (all safety-enabling)

**Properties inherited from defaults (via `platform-core.yml` + `application.yml`):**
- `trading.candidate-source.enabled: true` (matchIfMissing) — external API polled every 60s
- `trading.venue-access.mode: production` (via ENV default) — monitor uses production URLs
- `ai.deepseek.enabled: false` (default) — AI analysis disabled unless explicitly enabled
- DeepSeek (ai) + dev test tool + auto-approval — all default to disabled

### engine-app (`application-prod-like.yml`)

| Property | Value | Effect |
|----------|-------|--------|
| `engine.execution-loop-enabled` | `false` | Execution loop OFF — engine reads plans but never submits orders |
| `engine.metrics-publish.enabled` | `true` | Metrics snapshots pushed to monitor every 15s |

**Total overrides:** 2

**Key defaults inherited from `application.yml` (not overridden):**
- `engine.live-order-enabled: false` — live orders OFF
- `engine.kill-switch-enabled: true` — kill switch ON
- `engine.trading-venue-access-mode: testnet` — engine defaults to testnet URLs
- `engine.live-enabled-venues: bybit,gate` — but meaningless since loop + live orders are OFF
- `engine.max-notional-usd: 25` — $25 cap if ever activated

### telegram-bot-app

**No `application-prod-like.yml` exists.** The deploy Docker Compose uses `SPRING_PROFILES_ACTIVE: staging` for telegram-bot. Telegram-bot behavior in production:

- **Token-driven activation:** Bot is only active when `TELEGRAM_BOT_TOKEN` env var is non-empty (via `@ConditionalOnProperty`)
- No web server (`spring.main.web-application-type: none`)
- Uses monitor service at `http://monitor:8090` (Docker Compose internal network)
- `SPRING_PROFILES_ACTIVE: staging` sets `INFO` logging, requires `TELEGRAM_BOT_TOKEN`, and points to internal monitor URL

## Key Differences from Staging

Comparing `application-prod-like.yml` vs `application-staging.yml`:

### monitor-app: **1 difference**

| Property | staging | prod-like |
|----------|---------|-----------|
| `trading.metadata.require-credentials-on-startup` | `false` | `true` |

All other properties are identical. The single difference means staging can start with missing metadata credentials and gracefully degrade, while prod-like will refuse to start.

### engine-app: **No differences**

engine-app staging and prod-like YAML files are **byte-for-byte identical** — both set `execution-loop-enabled: false` and `metrics-publish.enabled: true`. The distinction between staging and prod-like for engine relies entirely on environment variables, not YAML overrides.

## Docker Compose Usage

From `deploy/docker-compose.yml`:

| Service | Profile | Notes |
|---------|---------|-------|
| `monitor` | `prod-like` | With explicit ENV overrides for metrics, DeepSeek, venue URLs |
| `engine` | `prod-like` | Execution loop/live orders OFF by default, with ENV overrides allowed (`ENGINE_EXECUTION_LOOP_ENABLED`, `ENGINE_LIVE_ORDER_ENABLED`, `ENGINE_KILL_SWITCH_ENABLED`) |
| `telegram-bot` | `staging` | No prod-like profile exists for this module |

## Findings

### Finding 1: engine-app staging and prod-like are byte-for-byte identical
Both files set the same 2 properties identically. The behavioral difference between staging and prod-like for engine-app depends entirely on the environment variables the operator sets at container startup, not on any YAML distinction. This is **not inherently dangerous** since both profiles default to safe (loop OFF, live orders OFF), but it may confuse operators who expect different YAML-based behavior between profiles.

### Finding 2: telegram-bot-app has no prod-like profile
Confirmed from earlier audit findings (Section 8 profile inventory). The telegram-bot module works correctly in production via `staging` profile + token-driven bean activation. However, this means any future prod-like-specific configuration for telegram-bot (e.g., logging level, retry behavior) would need a new profile file.

### Finding 3: `trading.candidate-source.enabled` inherits default `true` (matchIfMissing)
The prod-like profile does NOT explicitly disable or enable the candidate source. It uses the default from `platform-core.yml` which is `${TRADING_CANDIDATE_SOURCE_ENABLED:true}` with matchIfMissing. This means external API (`uainvest.com.ua`) is polled every 60s in prod-like unless explicitly disabled via environment variable. This is consistent behavior but worth documenting since it creates SignalCandidate records automatically.

### Finding 4: monitor-app and engine-app use inconsistent venue access modes by default
- monitor-app uses `${TRADING_VENUE_ACCESS_MODE:production}` from platform-core.yml
- engine-app uses `${TRADING_VENUE_ACCESS_MODE:testnet}` from application.yml base defaults
- In prod-like, monitor checks credentials for production, engine checks credentials for testnet
- Since engine loop and live orders are OFF by default, this inconsistency is harmless but confusing

### Finding 5: Venue defaults are inconsistent (from platform-core.yml)
- bybit: `mode = testnet` default
- gate: `mode = testnet` default
- bitget: `mode = production` default
- okx: `mode = production` default
- kucoin: `mode = production` default
This is irrelevant for the engine (which has its own venue access mode), but monitor checks credentials based on its `trading.venue-access.mode` setting, which defaults to `production`.

### Finding 6: The 3 runtime guard defaults from engine-app application.yml
Even without the prod-like profile, engine-app defaults are safe:
1. `engine.execution-loop-enabled: false` — loop OFF
2. `engine.live-order-enabled: false` — live orders OFF
3. `engine.kill-switch-enabled: true` — kill switch ON

The engine would need explicit ENV overrides to ALL THREE of these to become operational. This is a correct defense-in-depth approach — the profile only ensures one dimension (loop), but the app-level defaults handle the other two.

### Finding 7: `monitor.engine-metrics.enabled: true` in prod-like enables metrics ingestion
When running with prod-like, monitor-app accepts engine metrics snapshots. In staging, this is also enabled. The differentiation between environments for metrics depends on the engine's `metrics-publish.enabled` setting, which is `true` in both staging and prod-like engine profiles.

## Safety Assessment

| Critical Dimension | Status | Details |
|-------------------|--------|---------|
| Auth enabled | ✅ ON | `security.operators.auth-enabled: true` |
| Credential storage | ✅ ON | `credentials.storage.enabled: true`, master key required |
| Engine execution loop | ✅ OFF | `engine.execution-loop-enabled: false` (requires explicit ENV) |
| Live orders | ✅ OFF | `engine.live-order-enabled: false` (app default, not overridden) |
| Kill switch | ✅ ON | `engine.kill-switch-enabled: true` (app default) |
| Metadata credentials required | ✅ ON | `trading.metadata.require-credentials-on-startup: true` |
| Metrics publishing | ✅ ON (monitor accepts, engine pushes) | Both modules enabled |

**Conclusion:** The prod-like profile is safe for pre-production testing and production-adjacent environments. No automatic trading activity can occur without explicit, multi-variable environment configuration changes.
