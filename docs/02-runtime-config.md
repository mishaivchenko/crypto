# Runtime Config (ENV)

## Обязательные ENV
# Telegram
TG_API_ID=...
TG_API_HASH=...
TG_PHONE=+...
TG_SESSION_DIR=/data/tdlib
TG_CHANNEL=@funding_watchdog

# DB
DB_URL=jdbc:sqlite:/data/fundingarb.db

# Scheduler
SCHED_DISCOVERY_SECONDS=10
SCHED_LOOKAHEAD_SECONDS=30
SCHED_MAX_LATENESS_SECONDS=60
SCHED_MIN_RECHECK_MS=250

# Exchanges (prod/testnet)
BINANCE_API_KEY=...
BINANCE_API_SECRET=...
BINANCE_BASE_URL=https://testnet.binancefuture.com

BYBIT_API_KEY=...
BYBIT_API_SECRET=...
BYBIT_BASE_URL=...

GATE_API_KEY=...
GATE_API_SECRET=...
GATE_BASE_URL=...

## Рекомендации
- все ключи только через ENV/secret manager
- логирование: не печатать key/secret ни при каких условиях
- timezone:
    - хранить next_funding_at в UTC (epoch millis)
    - показывать в UI в Europe/Kyiv
