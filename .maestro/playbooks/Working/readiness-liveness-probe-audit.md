# Readiness / Liveness Probe Audit

**Audit Date:** 2026-07-13
**Scope:** monitor-app (port 8090), engine-app (port 8091), telegram-bot-app (no web server)

---

## 1. `management.endpoint.health.probes` Configuration

**Status: ❌ Not configured**

No `management.endpoint.health.probes.*` property exists in any YAML across all 3 modules.

With this unset, Spring Boot 3.5 **does not expose** the Kubernetes-ready probe endpoints:
- `/actuator/health/readiness` — not available
- `/actuator/health/liveness` — not available

To enable: `management.endpoint.health.probes.enabled=true` in application YAML. This would expose separate readiness/liveness probe HTTP endpoints. For the trading domain, a separate `trading-readiness` health group would also be relevant.

## 2. Kubernetes Probe Configuration

**Status: ❌ Not applicable — no Kubernetes manifests exist**

The codebase has zero Kubernetes deployment YAMLs, Service definitions, Helm charts, or Kustomize configs. Therefore:
- No `livenessProbe` definition
- No `readinessProbe` definition
- No `startupProbe` definition

**If Kubernetes deployment is planned**, probes would need to be configured at both the application level (Spring Boot probe endpoints) and the K8s level (deployment spec).

## 3. Docker HEALTHCHECK

**Status: ❌ Not configured**

**Root `/Dockerfile`** has no `HEALTHCHECK` instruction. The Dockerfile is used for all 3 apps via `APP_MODULE`/`APP_CLASSIFIER` build args.

**Docker Compose files** (`docker-compose.yml`, `deploy/docker-compose.yml`, `deploy/observability/docker-compose.yml`):
- Zero services have a `healthcheck:` block
- All services use `restart: unless-stopped` — containers restart if they crash but have no health-based recovery

Container orchestration (Docker Compose) cannot detect when an application has started successfully or is unhealthy — `depends_on` only checks container startup, not application readiness.

## 4. Custom HealthIndicator Implementations

**Status: ❌ Zero implementations exist**

| Artifact | Count |
|---|---|
| Custom `HealthIndicator` beans | 0 |
| Custom `HealthContributor` beans | 0 |
| Custom `HealthAggregator` beans | 0 |
| `@Endpoint` for health purposes | 0 |
| `@ConditionalOnHealth` usage | 0 |

All 3 modules rely entirely on Spring Boot's auto-configured health indicators:

- **monitor-app**: Spring Boot auto-configures `DataSourceHealthIndicator` (via `DataSourceScriptDatabaseInitializer`), `DiskSpaceHealthContributor`, and `SslHealthContributor`. None of these are aware of trading-specific state.
- **engine-app**: Spring Boot auto-configures `DiskSpaceHealthContributor` and `SslHealthContributor`. `DataSourceHealthIndicator` is absent (no data source).
- **telegram-bot-app**: No health endpoint (no web server, no actuator).

## 5. Actuator Health Endpoint Exposure by Module

| Module | Actuator Starter | `management.endpoints.web.exposure.include` | Available at |
|---|---|---|---|
| **monitor-app** | ✅ Yes | `health,info,prometheus` (in `platform-core.yml` line 5) | `/actuator/health` |
| **engine-app** | ✅ Yes (but unreferenced — see prior finding about removal candidate) | **None configured** — uses Spring Boot defaults (only `health`) | `/actuator/health` |
| **telegram-bot-app** | ❌ No (no web server) | N/A | N/A |

**Important engine-app gap**: Has `spring-boot-starter-actuator` on classpath but NO explicit `management.endpoints.web.exposure.include` config. By Spring Boot default, only `/actuator/health` is exposed. No `/actuator/info` or `/actuator/prometheus` available on engine-app without explicit config.

## 6. CI/CD Smoke Test Probes

**File:** `.github/workflows/ci-cd.yml` lines 253–260

```yaml
- name: Smoke test
  run: |
    for svc in 8090 8091; do
      echo "Waiting for :$svc..."
      for i in $(seq 1 24); do
        curl -sf http://localhost:$svc/actuator/health && echo ":$svc OK" && break
        [ $i -eq 24 ] && echo ":$svc not ready after 120s" && exit 1
        sleep 5
      done
    done
```

This is a basic smoke test: checks that the Actuator health endpoint returns HTTP 200. It does NOT:
- Distinguish between readiness and liveness
- Validate any business-specific state (credentials present, exchanges reachable, DB writable)
- Use any probe-specific endpoints

## 7. Path Mapping

**Status: ❌ Not configured**

No `management.endpoints.web.path-mapping` properties exist. Custom probe paths like `/healthz` or `/readyz` are not configured. The default `/actuator/health` path is used exclusively.

## 8. Base Path

**Status: ❌ Not customized**

No `management.endpoints.web.base-path` property exists. The default `/actuator` base path is used.

## 9. `server.shutdown` Configuration

**Status: ❌ Not configured (immediate shutdown)**

Already documented in the startup/shutdown audit (`Working/startup-shutdown-audit.md`). No graceful shutdown, no shutdown plugins for K8s `preStop` hooks.

## Findings Summary

| # | Finding | Severity | Impact |
|---|---|---|---|
| 1 | No K8s probe endpoints enabled (`management.endpoint.health.probes.enabled=true` missing) | **Medium** | If deployed to K8s, no readiness/liveness endpoints available for orchestrator |
| 2 | No Docker HEALTHCHECK in Dockerfile or compose | **Low** | Compose cannot detect unhealthy containers; `restart: unless-stopped` only catches crashes, not application-level failure |
| 3 | No custom HealthIndicator implementations | **Low** | `/actuator/health` reports UP based on disk space and SSL only — completely blind to trading-readiness state |
| 4 | CI/CD smoke test uses basic `/actuator/health` with no business-logic validation | **Low** | Tests only that the app started, not that it's ready to trade |
| 5 | No path-mapping for `/healthz`/`/readyz` | **Low** | Non-K8s deployment, but convention would be useful |
| 6 | Engine-app lacks explicit actuator exposure config | **Low** | Only `/actuator/health` exposed on engine by default; no `/info` or `/prometheus` |

## Recommendations

1. **If K8s deployment is planned:** Add `management.endpoint.health.probes.enabled=true` to shared config and configure liveness/readiness probes on the deployment spec
2. **Add a `TradingReadinessHealthIndicator`** that checks: engine loop status, credential presence, exchange connectivity, clock sync (reporting `DOWN` with details when any invariant is violated)
3. **Add Dockerfile `HEALTHCHECK`** pointing at `/actuator/health` for container orchestration
4. **If engine-app remains containerized:** Add explicit `management.endpoints.web.exposure.include: health` or remove actuator starter entirely (per prior finding that spring-boot-starter-actuator is unused in engine-app)
