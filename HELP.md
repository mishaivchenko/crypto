# Quick Help

## Build

```bash
./gradlew test
./gradlew build
```

## Run Locally

Monitor/control plane:

```bash
SECURITY_OPERATOR_AUTH_ENABLED=false \
TRADING_METADATA_REQUIRE_CREDENTIALS_ON_STARTUP=false \
./gradlew bootRunMonitor
```

Engine/read-side runtime:

```bash
INTERNAL_ENGINE_TOKEN=dev-internal-token ./gradlew bootRunEngine
```

## Main URLs

- Monitor UI: `http://localhost:8090`
- Monitor health: `http://localhost:8090/actuator/health`
- Engine health: `http://localhost:8091/actuator/health`
- Engine summary: `http://localhost:8091/internal/engine/summary`

## Auth

Operator APIs use `X-Operator-Token` when `SECURITY_OPERATOR_AUTH_ENABLED=true`.

Bootstrap operators:

```env
SECURITY_OPERATOR_BOOTSTRAP_USERS=alice:token,bob:token2
```

Exchange credentials are stored through `/api/v1/operators/me/credentials/**`, encrypted with `CREDENTIALS_MASTER_KEY_BASE64`.
