# 11. Isolated Observability

## Цель

Добавлен отдельный opt-in контур:

`engine -> monitor -> Prometheus -> Grafana`

Он не меняет основной runtime по умолчанию.

## Что именно добавлено

- `EngineMetricsSnapshot` в `platform-core`
- engine-side publisher snapshot'ов
- monitor-side internal ingest endpoint
- monitor-side Micrometer gauges
- отдельный deploy bundle в `deploy/observability`
- Grafana provisioning и dashboard

## Feature Flags

По умолчанию в `main`:

- `ENGINE_METRICS_PUBLISH_ENABLED=false`
- `MONITOR_ENGINE_METRICS_ENABLED=false`

Следствия:

- engine не делает HTTP push в monitor;
- monitor не ждёт snapshot'ы;
- отсутствие Prometheus/Grafana не влияет на runtime;
- business/UI flow остаётся прежним.

## Runtime модель

Когда observability включён:

1. `engine-app` периодически строит low-cardinality snapshot.
2. Snapshot отправляется в `monitor-app` по internal endpoint.
3. `monitor-app` публикует агрегированные gauge metrics.
4. Prometheus scrapes только `monitor-app`.
5. Grafana читает только Prometheus.

## Низкая кардинальность

Разрешены только безопасные измерения:

- `funding_engine_up`
- `funding_engine_plans`
- `funding_engine_actionable_plans`
- `funding_engine_plan_status{status=...}`
- `funding_engine_snapshot_age_seconds`

Нет labels по:

- `armedTradeId`
- `symbol`
- `orderId`
- другим high-cardinality полям

## Безопасность

Ingest endpoint живёт под internal-контуром:

- `POST /internal/v1/engine/metrics-snapshot`

Он защищён тем же `X-Internal-Token`, что и monitor/engine plan sync.

Если feature выключен, endpoint не поднимается.

## Отдельный deploy flow

Observability runtime вынесен отдельно:

- [deploy/observability/docker-compose.yml](/Users/mishaivchenko/.codex/worktrees/da09/crypto/deploy/observability/docker-compose.yml)
- [deploy/observability/README.md](/Users/mishaivchenko/.codex/worktrees/da09/crypto/deploy/observability/README.md)

Это отдельный compose со своими:

- container names
- network
- volumes
- SQLite database
- host ports

## Rollback

Rollback простой:

1. Остановить `deploy/observability` compose.
2. Не менять основной `.env` и основной business config.
3. Основной runtime продолжит работать как раньше.
