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
SECURITY_OPERATOR_AUTH_ENABLED=false \
TRADING_METADATA_REQUIRE_CREDENTIALS_ON_STARTUP=false \
./gradlew bootRunMonitor
```

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
INTERNAL_ENGINE_TOKEN=<same-token-as-monitor> ./gradlew bootRunEngine
```

URL:

```text
http://localhost:8091
```

Smoke:

```bash
curl http://localhost:8091/actuator/health
curl http://localhost:8091/internal/engine/summary
curl http://localhost:8091/internal/engine/plans
```

Manual execution-attempt smoke:

```bash
curl -X POST 'http://localhost:8091/internal/engine/execution/run-once?force=true'
curl http://localhost:8090/api/v1/order-attempts
```

Expected safe result without engine credentials: attempts are recorded as `FAILED` with a missing credentials reason.

## Local Safe Defaults

Для локального запуска без ключей:

```env
TRADING_METADATA_REQUIRE_CREDENTIALS_ON_STARTUP=false
SECURITY_OPERATOR_AUTH_ENABLED=false
CREDENTIALS_STORAGE_ENABLED=false
```

## What Good Looks Like

- Monitor root returns `200`.
- Monitor health is `UP`.
- Engine health is `UP`.
- Candidate source creates normalized candidates.
- Venue metadata sync reports active instruments.
- Engine endpoints return plans or an empty list through monitor REST.
- Manual engine run records `OrderAttempt` rows when armed trades exist.
- No process listens after shutdown on `8090` or `8091`.
