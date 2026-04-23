# Runtime Config

## Runtime Profiles

Поддерживаются три явных профиля:

```env
SPRING_PROFILES_ACTIVE=local-safe
SPRING_PROFILES_ACTIVE=staging
SPRING_PROFILES_ACTIVE=prod-like
```

Смысл профилей:

- `local-safe`: engine loop off, operator auth off, credential storage off, metadata credentials optional, metrics off.
- `staging`: engine loop off, operator auth on, credential storage on, metadata credentials optional, metrics on.
- `prod-like`: engine loop off by default, operator auth on, credential storage on, metadata credentials required, metrics on.

Если профиль не задан, base config остаётся safe-by-default.

## Candidate Source

```env
TRADING_CANDIDATE_SOURCE_ENABLED=true
TRADING_CANDIDATE_SOURCE_URL=https://uainvest.com.ua/api/funding?sort_by=funding&sort_dir=asc&limit=30
TRADING_CANDIDATE_SOURCE_REFRESH_INTERVAL_SECONDS=60
TRADING_CANDIDATE_SOURCE_TYPE=FUNDING_API
```

## Venue Access Mode

```env
TRADING_VENUE_ACCESS_MODE=production
```

Глобальный режим: `testnet` или `production`.

Venue-specific modes могут существовать как fallback, но продуктово основной switch должен быть глобальным.

## Enabled Venues

```env
TRADING_METADATA_ENABLED_VENUES=bybit,gate,bitget,okx,kucoin
TRADING_METADATA_SYNC_ON_STARTUP=true
TRADING_METADATA_SCHEDULE_ENABLED=false
TRADING_METADATA_SYNC_INTERVAL_MINUTES=240
TRADING_METADATA_REQUIRE_CREDENTIALS_ON_STARTUP=false
TRADING_METADATA_BOOTSTRAP_FALLBACK_ENABLED=false
```

Для `prod-like` включите stricter startup-требование явно:

```env
TRADING_METADATA_REQUIRE_CREDENTIALS_ON_STARTUP=true
```

## HTTP Timing

```env
TRADING_HTTP_CONNECT_TIMEOUT_MS=1000
TRADING_HTTP_REQUEST_TIMEOUT_MS=5000
TRADING_HTTP_PREFER_HTTP2=true
```

Timing доступен через:

```text
GET /api/v1/venues/timings
GET /api/v1/venues/timings?venue=gate
```

## Operator Auth

```env
SECURITY_OPERATOR_AUTH_ENABLED=false
SECURITY_OPERATOR_BOOTSTRAP_USERS=alice:raw-token,bob:raw-token-2
```

Все `/api/**` endpoints требуют `X-Operator-Token`, если auth включён.

Токены bootstrap-операторов сохраняются в базе только как SHA-256 hash.

## Internal Monitor/Engine Token

```env
INTERNAL_ENGINE_TOKEN=change-me
MONITOR_INTERNAL_BASE_URL=http://localhost:8090
```

Monitor internal endpoints `/internal/v1/engine/**` требуют `X-Internal-Token`.

Engine использует `MONITOR_INTERNAL_BASE_URL` и тот же `INTERNAL_ENGINE_TOKEN`.

## Engine Execution Attempts

```env
ENGINE_EXECUTION_LOOP_ENABLED=false
ENGINE_EXECUTION_LOOP_INTERVAL_MS=1000
```

Manual smoke endpoint:

```text
POST /internal/engine/execution/run-once?force=true
```

Правила текущей фазы:

- loop выключен по умолчанию;
- `prod-like` тоже не включает loop автоматически, deployment должен выставить `ENGINE_EXECUTION_LOOP_ENABLED=true` явно;
- `force=true` выполняет все entry attempts из monitor plans, включая future `WAITING_ENTRY`;
- без engine credentials попытки сохраняются как `FAILED`;
- live exchange order submission пока не включён автоматически.

Engine credentials сейчас читаются из runtime ENV/config engine-side:

```env
ENGINE_CREDENTIALS_BYBIT_API_KEY=
ENGINE_CREDENTIALS_BYBIT_SECRET_KEY=
ENGINE_CREDENTIALS_GATE_API_KEY=
ENGINE_CREDENTIALS_GATE_SECRET_KEY=
ENGINE_CREDENTIALS_BITGET_API_KEY=
ENGINE_CREDENTIALS_BITGET_SECRET_KEY=
ENGINE_CREDENTIALS_BITGET_PASSPHRASE=
```

Если значения отсутствуют, это нормальный smoke-сценарий: `OrderAttempt` будет записан с причиной missing credentials.

## Funding Burst Defaults

```env
TRADING_DEFAULT_ENTRY_ATTEMPT_COUNT=1
TRADING_DEFAULT_ENTRY_SPACING_MS=0
TRADING_DEFAULT_MANUAL_LATENCY_ADJUSTMENT_MS=0
```

Правила:

- `entryAttemptCount >= 1`.
- `entrySpacingMs = 0` означает одновременный burst.
- `entrySpacingMs > 0` означает последовательные попытки.
- effective latency = measured latency + manual adjustment, но не меньше 0.

## Exchange Credentials

Актуальные product venues:

- Bybit.
- Gate.
- Bitget.
- OKX.
- KuCoin.

Credentials больше не задаются через venue-specific API key ENV. Они записываются через operator API:

```text
PUT /api/v1/operators/me/credentials/{venue}/{mode}
POST /api/v1/operators/me/credentials/{venue}/{mode}/check
```

Encryption config:

```env
CREDENTIALS_STORAGE_ENABLED=true
CREDENTIALS_REQUIRE_MASTER_KEY_ON_STARTUP=true
CREDENTIALS_MASTER_KEY_BASE64=<openssl rand -base64 32>
```

Если credential storage включён и master key отсутствует, startup падает fail-closed.

Credentials не должны храниться в репозитории или deployment image.

## Schema Management

`monitor-app` использует Flyway и больше не опирается на `ddl-auto=update`:

```env
SPRING_FLYWAY_ENABLED=true
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
```

Поведение:

- пустая SQLite база получает schema из `db/migration/V1__baseline.sql`;
- существующая pre-Flyway SQLite база берётся под управление через baseline history table;
- JPA проверяет schema через `validate`, а не изменяет её автоматически.
