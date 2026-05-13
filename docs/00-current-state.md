# Current State

## Версия

Текущая линия продукта: `2.0.0`.

Это новая линия без старого bot/execution контура. Сейчас проект является modular monolith с двумя runtime-приложениями:

- `monitor-app` — operator/control plane и UI.
- `engine-app` — лёгкий planning/runtime-контур для будущего execution engine.
- `platform-core` — общий домен, contracts, ports и utilities без runtime ownership.

## Текущий бизнес-flow

`Funding API -> SignalCandidate -> Review -> FundingEvent -> ArmedTrade -> Engine Plan -> OrderAttempt -> Trade Journal`

Что важно:

- Кандидаты приходят из Funding API: `https://uainvest.com.ua/api/funding?sort_by=funding&sort_dir=asc&limit=30`.
- `FundingEvent` создаётся после review/approve.
- `ArmedTrade` создаётся оператором из confirmed event.
- Funding armed trade всегда `SHORT-only`.
- `engine-app` строит план и может записывать execution attempts.
- Live exchange order submission через новый домен ещё guarded и не включён автоматически.

## Что уже работает

- Polling внешнего Funding API.
- Создание и дедупликация `SignalCandidate`.
- Нормализация символов через venue metadata registry.
- Review queue: approve, reject, delete candidate.
- Создание `FundingEvent`.
- Arm flow: `FundingEvent -> ArmedTrade`.
- Burst-entry параметры: количество входов и spacing в миллисекундах.
- Измерение request timing и расчёт effective entry latency.
- Venue diagnostics для Bybit, Gate, Bitget, OKX, KuCoin.
- Monitor UI на `8090`.
- Engine planning API на `8091`.
- Manual engine run endpoint пишет `OrderAttempt` records.
- Operator account management (`operator_account` table, SHA-256 token auth).
- AES-GCM encrypted credential storage per operator (`operator_exchange_credential`).
- Dev test run flow: entry + exit через `POST /api/v2/monitor/dev/test-runs`.
- **Live exchange order submission работает** — Gate testnet подтверждён (ACT/USDT SHORT FILLED 2026-05-09); `ENGINE_LIVE_ORDER_ENABLED=true` разблокирует это.
- Bybit geo-blocked для UA IPs даже на testnet (требует VPN).
- Pipeline navigation в UI: переход candidate → event → armed trade → order attempts.
- **Cancel trade flow**: `DELETE /api/v1/armed-trades/{id}` переводит сделку в `CANCELLED` из любого из CANCELLABLE_STATES (ARMED, ENTRY_PENDING, ENTRY_ATTEMPTED, OPEN, EXIT_PENDING); кнопка cancel доступна и в Prepared Trades, и в Trade History drawer.
- **Position + exit lifecycle**: entry FILLED → ArmedTrade переходит в OPEN + position записывается; exit FILLED → position обновляется с exitPrice, `CLOSED`; outcome (PnL, fees) записывается; ArmedTrade → `CLOSED`.
- **PnL/outcome calculation**: `recordTradeOutcome` использует upsert-паттерн — повторные вызовы корректно перезаписывают outcome.
- **Risk guardrails**: max-concurrent armed trades (default 3, `monitor.risk.max-concurrent-armed-trades`); список отключённых venues (`monitor.risk.disabled-venues`).
- **Trade History UI**: показывает реальные данные Position и Outcome (quantity, entry/exit price, net PnL, fees).
- **Engine testnet profile**: `application-testnet.yml` включает loop + live orders для Gate testnet.
- **Metadata sync**: `TRADING_METADATA_SYNC_ON_STARTUP` и `TRADING_METADATA_SCHEDULE_ENABLED` включены по умолчанию в docker-compose.

## Что принципиально не готово

- Автономный engine execution loop на проде (`ENGINE_EXECUTION_LOOP_ENABLED=false` по умолчанию).
- Деплой в Singapore как отдельная execution-runtime среда.
- Полноценное управление ролями; сейчас есть single-role operator auth через `X-Operator-Token`.
