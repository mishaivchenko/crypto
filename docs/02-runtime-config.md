# Runtime Config (ENV)

## Candidate Source
TRADING_CANDIDATE_SOURCE_ENABLED=true  
TRADING_CANDIDATE_SOURCE_URL=https://uainvest.com.ua/api/funding?sort_by=funding&sort_dir=asc&limit=30  
TRADING_CANDIDATE_SOURCE_REFRESH_INTERVAL_SECONDS=60  
TRADING_CANDIDATE_SOURCE_TYPE=FUNDING_API  

Правила:
- funding API стал единственным runtime-источником кандидатов;
- polling source обновляет watchlist и создаёт `SignalCandidate`;
- если источник нужно временно отключить для отладки или тестов, используйте `TRADING_CANDIDATE_SOURCE_ENABLED=false`.

## Telegram Bot (optional UI)
TELEGRAM_BOT_ENABLED=false  
TG_BOT_USERNAME=...  
TG_BOT_TOKEN=...  

## Modes
- `TRADING_VENUE_ACCESS_MODE`: глобальный режим доступа `testnet` или `production`.
- Venue-specific режимы (`BYBIT_MODE`, `GATE_MODE`, etc.) остаются fallback-значениями, но основной runtime switch должен быть глобальным.

## Metadata / Venue Registry
TRADING_METADATA_ENABLED_VENUES=bybit,gate,bitget,okx,kucoin
TRADING_METADATA_SYNC_ON_STARTUP=true  
TRADING_METADATA_SCHEDULE_ENABLED=false  
TRADING_METADATA_SYNC_INTERVAL_MINUTES=240  
TRADING_METADATA_REQUIRE_CREDENTIALS_ON_STARTUP=true  
TRADING_METADATA_BOOTSTRAP_FALLBACK_ENABLED=false  

Правила:
- registry sync-ится по enabled venues;
- при `REQUIRE_CREDENTIALS_ON_STARTUP=true` приложение падает, если для активного mode у биржи нет ключей/секретов;
- это сделано специально, чтобы multi-venue конфигурация была честной уже на старте.

## HTTP / Request Timing
TRADING_HTTP_CONNECT_TIMEOUT_MS=1000  
TRADING_HTTP_REQUEST_TIMEOUT_MS=5000  
TRADING_HTTP_PREFER_HTTP2=true  

Практика:
- для Singapore deployment request timeouts лучше держать низкими и измеримыми;
- metadata и в будущем execution path должны использовать заранее прогретые HTTP clients;
- request timing можно смотреть через `/api/v1/venues/timings`.

## Execution Safety
TRADING_EXECUTION_MODE=DISABLED  
TRADING_LEGACY_EXECUTION_ENABLED=false  
TRADING_LIVE_VENUES=bybit  
TRADING_BLOCKED_VENUES=gate  
TRADING_DEFAULT_ENTRY_ATTEMPT_COUNT=1
TRADING_DEFAULT_ENTRY_SPACING_MS=0
TRADING_DEFAULT_MANUAL_LATENCY_ADJUSTMENT_MS=0

Правила:
- `DISABLED`: никаких legacy ордеров
- `SHADOW`: только passive/shadow поведение без order placement
- `LIVE`: legacy execution только при явном opt-in и только для venue из allowlist
- `gate` должен оставаться заблокированным до отдельной реализации venue-specific execution path
- funding `ArmedTrade` всегда `SHORT`; `LONG` на funding-arm API отклоняется;
- `ENTRY_ATTEMPT_COUNT` задаёт burst-попытки входа; `ENTRY_SPACING_MS=0` означает одновременные попытки, `>0` — последовательный burst;
- измеренная entry latency берётся из request timing: сначала `test-order`, затем `credential-check`, затем `metadata-sync`;
- effective latency = `measured latency + manual adjustment`, но не меньше 0;
- engine сдвигает trigger входа раньше `plannedEntryAt` на effective latency.

## Exchanges (testnet)
BYBIT_TESTNET_API_KEY=...  
BYBIT_TESTNET_SECRET_KEY=...  
BYBIT_TESTNET_BASE_URL=https://api-testnet.bybit.com  

GATE_TESTNET_API_KEY=...  
GATE_TESTNET_SECRET_KEY=...  
GATE_TESTNET_BASE_URL=https://fx-api-testnet.gateio.ws/api/v4  

## Exchanges (prod)
BYBIT_PROD_API_KEY=...  
BYBIT_PROD_SECRET_KEY=...  
BYBIT_PROD_BASE_URL=https://api.bybit.com  

GATE_PROD_API_KEY=...  
GATE_PROD_SECRET_KEY=...  
GATE_PROD_BASE_URL=https://api.gateio.ws/api/v4  

BITGET_PROD_API_KEY=...
BITGET_PROD_SECRET_KEY=...
BITGET_PROD_PASSPHRASE=...

OKX_PROD_API_KEY=...
OKX_PROD_SECRET_KEY=...
OKX_PROD_PASSPHRASE=...

KUCOIN_PROD_API_KEY=...
KUCOIN_PROD_SECRET_KEY=...
KUCOIN_PROD_PASSPHRASE=...

## DB
SPRING_DATASOURCE_URL=jdbc:sqlite:/data/fundingarb.db  

## Scheduler (defaults см. application.yml)
SCHED_DISCOVERY_SECONDS=15  
SCHED_LOOKAHEAD_SECONDS=10  
SCHED_MAX_LATENESS_SECONDS=120  
SCHED_MIN_RECHECK_MS=1000  

## Практики
- Все ключи только через ENV/secret manager (GitHub Secrets, Docker secrets).
- Логи не должны содержать токены/ключи.
- Timezone: next_funding_at хранить в UTC, UI — Europe/Kyiv.
- Если нужен реальный запуск legacy execution для controlled testing, это должно быть отдельным осознанным включением, а не “случайным наличием ключей”.
- Для изолированных тестов и локальных интеграционных прогонов отключайте funding API polling через `TRADING_CANDIDATE_SOURCE_ENABLED=false`.
- Для проверки venue metadata и сетевого пути используйте:
  - `POST /api/v1/venues/{venue}/sync`
  - `GET /api/v1/venues`
  - `GET /api/v1/venues/timings`
