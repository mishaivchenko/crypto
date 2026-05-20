# Modules

## `platform-core`

Содержит общие contracts/domain pieces:

- domain models: candidate, event, trade, execution, venue.
- shared application ports.
- engine plan contracts.
- symbol and crypto utilities.

`platform-core` не является Spring Boot runtime и не владеет persistence.

## `monitor-app`

Основной operator UI и control plane.

Отвечает за:

- Dashboard.
- Candidates review.
- Funding Events.
- Armed Trades.
- Trade History.
- Venue Diagnostics + Latency Calibration.
- Journal visibility.
- Operator credential management.
- AI Signal Advisor (DeepSeek GO/WATCH/PASS).
- Internal engine plan API.
- Static UI assets (vanilla JS, no framework).

Порт по умолчанию: `8090`.

## `engine-app`

Лёгкий execution runtime.

Отвечает за:

- listing execution plans fetched from monitor.
- live order submission через `LiveExchangeExecutionPort` (Gate, Bybit, OKX, Bitget, KuCoin).
- recording `OrderAttempt` results back to monitor.
- engine execution loop (disabled by default).

Порт по умолчанию: `8091`.

Engine может размещать live orders, если `ENGINE_LIVE_ORDER_ENABLED=true`. По умолчанию отключено — safe-by-default.

Все production классы покрыты 100% mutation testing (PIT). Документация в `docs/engine-tdd/`.

## `telegram-bot-app`

Telegram бот `@funding_arbitrage_bot_bot`.

Отвечает за:

- уведомления о новых сигналах (polling monitor-app).
- команды `/signals`, `/status`, `/links`, `/faq`.

Настраивается через `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`, `MONITOR_BASE_URL`.
