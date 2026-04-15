# Runtime Config

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
TRADING_METADATA_REQUIRE_CREDENTIALS_ON_STARTUP=true
TRADING_METADATA_BOOTSTRAP_FALLBACK_ENABLED=false
```

Для локального запуска без ключей можно временно использовать:

```env
TRADING_METADATA_REQUIRE_CREDENTIALS_ON_STARTUP=false
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

## Execution Safety

```env
TRADING_EXECUTION_MODE=DISABLED
TRADING_LEGACY_EXECUTION_ENABLED=false
TRADING_LIVE_VENUES=
TRADING_BLOCKED_VENUES=gate
```

По умолчанию приложение не должно размещать live orders.

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

Примеры ENV:

```env
BYBIT_PROD_API_KEY=
BYBIT_PROD_SECRET_KEY=

GATE_PROD_API_KEY=
GATE_PROD_SECRET_KEY=

BITGET_PROD_API_KEY=
BITGET_PROD_SECRET_KEY=
BITGET_PROD_PASSPHRASE=

OKX_PROD_API_KEY=
OKX_PROD_SECRET_KEY=
OKX_PROD_PASSPHRASE=

KUCOIN_PROD_API_KEY=
KUCOIN_PROD_SECRET_KEY=
KUCOIN_PROD_PASSPHRASE=
```

Credentials не должны храниться в репозитории.

