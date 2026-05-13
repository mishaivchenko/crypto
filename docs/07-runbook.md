# Runbook

## Requirements

- JDK 25.
- Gradle wrapper из репозитория.
- `data/` directory для локальной SQLite базы.

## Build And Test

```bash
./gradlew test
./gradlew build
```

Если локальный Gradle не видит JDK 25 на macOS:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@25 ./gradlew --no-daemon -Dorg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25 test
```

## Run Monitor

```bash
./gradlew bootRunMonitor
```

По умолчанию этот root task сам стартует monitor с `SPRING_PROFILES_ACTIVE=local-safe` и `INTERNAL_ENGINE_TOKEN=funding-local-internal-token`, если вы не передали свои ENV.

URL:

```text
http://localhost:8090
```

Smoke:

```bash
curl http://localhost:8090/actuator/health
curl http://localhost:8090/api/v2/monitor/overview
curl 'http://localhost:8090/api/v1/candidates?size=3'
```

Если auth включён:

```bash
curl -H 'X-Operator-Token: <token>' http://localhost:8090/api/v2/monitor/overview
```

## Run Engine

```bash
./gradlew bootRunEngine
```

По умолчанию этот root task сам стартует engine с `SPRING_PROFILES_ACTIVE=local-safe` и тем же `INTERNAL_ENGINE_TOKEN=funding-local-internal-token`, если вы не передали свои ENV.

URL:

```text
http://localhost:8091
```

Smoke:

```bash
curl http://localhost:8091/actuator/health
curl -H "X-Internal-Token: funding-local-internal-token" http://localhost:8091/internal/engine/summary
curl -H "X-Internal-Token: funding-local-internal-token" http://localhost:8091/internal/engine/plans
```

Manual execution-attempt smoke:

```bash
curl -X POST 'http://localhost:8091/internal/engine/execution/run-once?force=true'
curl http://localhost:8090/api/v1/order-attempts
```

Expected safe result without engine credentials: attempts are recorded as `FAILED` with a missing credentials reason.

## Local Safe Defaults

Для локального запуска без ключей (`local-safe` профиль выставляется автоматически):

```env
TRADING_METADATA_REQUIRE_CREDENTIALS_ON_STARTUP=false
TRADING_METADATA_SYNC_ON_STARTUP=false
SECURITY_OPERATOR_AUTH_ENABLED=false
CREDENTIALS_STORAGE_ENABLED=false
ENGINE_EXECUTION_LOOP_ENABLED=false
ENGINE_LIVE_ORDER_ENABLED=false
```

## Run Engine In Testnet Mode

```bash
ENGINE_GATE_TESTNET_API_KEY=xxx ENGINE_GATE_TESTNET_SECRET_KEY=yyy \
  SPRING_PROFILES_ACTIVE=testnet ./gradlew bootRunEngine
```

Включает execution loop (2000 ms), live orders для Gate testnet, max notional $25.

## Latency Probe

Обновляет latency profile venue вручную:

```bash
curl -X POST http://localhost:8090/api/v2/monitor/venues/gate/latency-probe
```

Необходимо, если latency profile устарел и engine отказывает с "Latency profile is stale".

## Cancel Stuck Trade

```bash
curl -X DELETE http://localhost:8090/api/v1/armed-trades/{id} \
  -H "X-Operator-Token: funding-local-operator-token"
```

Переводит сделку в `CANCELLED`. Работает для CANCELLABLE_STATES (ARMED, ENTRY_PENDING, ENTRY_ATTEMPTED, OPEN, EXIT_PENDING). Позиции на бирже не закрываются — только запись в базе.

## Autonomous Loop on Testnet

Полный цикл без ручных dev-run кнопок. Требуются Gate testnet API ключи.

### Prerequisites

- Monitor запущен (`./gradlew bootRunMonitor` или docker-compose).
- Venue metadata synced (по умолчанию включено в docker-compose).
- Gate testnet API key + secret.

### Steps

```bash
# 1. Запустить latency probe для заполнения timing profile
curl -X POST http://localhost:8090/api/v2/monitor/venues/gate/latency-probe

# 2. Создать синтетическую сделку через Dev Test Run
#    (entry ~10s в будущем, exit ~70s в будущем)
curl -X POST http://localhost:8090/api/v2/monitor/dev/test-runs \
  -H "Content-Type: application/json" \
  -d '{"venue":"gate","symbol":"ACT/USDT","notionalUsd":5}'
# Сохрани armedTradeId из ответа

# 3. Запустить engine с включённым loop и live orders
ENGINE_GATE_TESTNET_API_KEY=xxx ENGINE_GATE_TESTNET_SECRET_KEY=yyy \
  ENGINE_EXECUTION_LOOP_ENABLED=true ENGINE_LIVE_ORDER_ENABLED=true \
  SPRING_PROFILES_ACTIVE=testnet ./gradlew bootRunEngine
```

Ожидаемые события в логах engine:

| Время | Событие |
|-------|---------|
| ~10s | `FILLED` entry attempt, trade → `OPEN`, position записан |
| ~70s | `FILLED` exit attempt, position → `CLOSED` с exitPrice, outcome записан, trade → `CLOSED` |

### Verification queries

```bash
# Состояние сделки
curl http://localhost:8090/api/v1/armed-trades/{id}

# Позиция
curl http://localhost:8090/api/v2/trades/{id}/position

# Результат
curl http://localhost:8090/api/v2/trades/{id}/outcome
```

### Expected end state

| Поле | Ожидаемое значение |
|------|-------------------|
| `trade.state` | `CLOSED` |
| `position.state` | `CLOSED` |
| `position.exitPrice` | non-null |
| `outcome.outcomeCode` | `CLOSED` |
| `outcome.netPnlUsd` | non-null (positive or negative) |

Если trade зависает в `ARMED` дольше нескольких минут — проверь latency probe (шаг 1). Если trade завис в `OPEN` — проверь engine логи на "EXIT_WINDOW" и убедись, что `plannedExitAt` в прошлом.

## What Good Looks Like

- Monitor root returns `200`.
- Monitor health is `UP`.
- Engine health is `UP`.
- Candidate source creates normalized candidates.
- Venue metadata sync reports active instruments.
- Engine endpoints return plans or an empty list through monitor REST.
- Manual engine run records `OrderAttempt` rows when armed trades exist.
- No process listens after shutdown on `8090` or `8091`.
