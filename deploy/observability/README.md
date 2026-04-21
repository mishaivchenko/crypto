# Isolated Observability Stack

Этот каталог добавляет отдельный opt-in observability runtime по схеме:

`engine -> monitor -> Prometheus -> Grafana`

Важно:
- основной `main` flow не меняется;
- основной `docker-compose.yml` не меняется;
- основной локальный запуск `./gradlew bootRunMonitor` и `./gradlew bootRunEngine` не меняется;
- этот stack использует отдельные контейнеры, отдельную сеть, отдельные volumes и отдельную SQLite базу.

## Что поднимается

- `funding-observability-monitor`
- `funding-observability-engine`
- `funding-observability-prometheus`
- `funding-observability-grafana`

Prometheus scrapes только `monitor`:
- `/actuator/prometheus`

Grafana не обращается к `engine` напрямую.

## Быстрый старт

```bash
cd deploy/observability
cp .env.observability.example .env.observability
docker compose --env-file .env.observability up --build
```

Порты по умолчанию:
- monitor: `18090`
- engine: `18091`
- Prometheus: `19090`
- Grafana: `13000`

## Почему runtime изолирован

В observability compose включаются только отдельные override-переменные для демо/наблюдения:
- `MONITOR_ENGINE_METRICS_ENABLED=true`
- `ENGINE_METRICS_PUBLISH_ENABLED=true`
- `TRADING_CANDIDATE_SOURCE_ENABLED=true`
- `TRADING_METADATA_SYNC_ON_STARTUP=true`
- `TRADING_METADATA_REQUIRE_CREDENTIALS_ON_STARTUP=false`
- `MANAGEMENT_METRICS_DISTRIBUTION_SLO_HTTP_SERVER_REQUESTS=50ms,100ms,250ms,500ms,1s,2s,5s`

Это сделано специально, чтобы не менять default main behaviour.

SQLite в этом stack отдельная:
- `jdbc:sqlite:/data/observability.db`

То есть observability compose не подменяет основной runtime молча.

## Проверка

1. Открыть monitor metrics:
   `http://localhost:18090/actuator/prometheus`
2. Убедиться, что есть метрика:
   `funding_engine_up`
3. Открыть Prometheus:
   `http://localhost:19090`
4. Открыть Grafana:
   `http://localhost:13000`

## Dashboards

По умолчанию provisioning поднимает два operator-facing dashboard:

- `Funding Platform Observability`
  - health engine/snapshot
  - plans by status / venue
  - execution run durations
  - failed attempts by venue
  - monitor API latency
- `Funding Platform Venues & Latency`
  - metadata sync avg duration
  - credential-check avg duration
  - request volume / success ratio
  - telemetry freshness
  - p95 latency monitor endpoints

## Rollback

Откат не требует правки основного business config.

Достаточно:

```bash
cd deploy/observability
docker compose --env-file .env.observability down
```

При необходимости удалить только observability-данные:

```bash
docker compose --env-file .env.observability down -v
```
