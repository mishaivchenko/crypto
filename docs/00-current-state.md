# Current State

## Версия

Текущая линия продукта: `2.0.0`.

Это уже не Telegram/TDLib bot и не старый funding scheduler. Сейчас проект является modular monolith с двумя runtime-приложениями:

- `monitor-app` — operator/control plane и UI.
- `engine-app` — лёгкий planning/runtime-контур для будущего execution engine.
- shared core — домен, persistence, API, venue metadata, safety и journal.

## Текущий бизнес-flow

`Funding API -> SignalCandidate -> Review -> FundingEvent -> ArmedTrade -> Engine Plan -> Trade Journal`

Что важно:

- Кандидаты приходят из Funding API: `https://uainvest.com.ua/api/funding?sort_by=funding&sort_dir=asc&limit=30`.
- Telegram/TDLib больше не является source of truth для candidate ingestion.
- `FundingEvent` создаётся после review/approve.
- `ArmedTrade` создаётся оператором из confirmed event.
- Funding armed trade всегда `SHORT-only`.
- `engine-app` строит план, но не отправляет live orders.
- Live execution через новый домен ещё не реализован.

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

## Что принципиально не готово

- Реальная отправка order через новый execution domain.
- Полный risk engine.
- Market-data execution loop.
- PnL/outcome calculation.
- Production-grade trade history UI.
- Multi-user auth/roles.
- Деплой в Singapore как отдельная execution-runtime среда.

