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

## Что принципиально не готово

- Реальная отправка order на биржу через новый execution domain.
- Полный risk engine.
- Market-data execution loop.
- PnL/outcome calculation.
- Production-grade trade history UI.
- Деплой в Singapore как отдельная execution-runtime среда.
- Полноценное управление ролями; сейчас есть single-role operator auth через `X-Operator-Token`.
