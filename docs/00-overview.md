# Overview

## Реальность репозитория после Phase 0-1
Это больше не должен восприниматься как “funding arbitrage bot”.

Текущий shape:
- один Spring Boot сервис;
- внешний funding API является источником candidate ingestion;
- Telegram bot остаётся только опциональным operator/diagnostic интерфейсом;
- legacy funding approve/scheduler/execution flow сохранён только как transitional code;
- новый домен funding-event trading уже введён параллельно legacy-коду;
- новый control plane начинается с internal REST API, а не с Telegram bot UI;
- поверх ingest добавлен review-driven candidate flow `SignalCandidate -> FundingEvent`.
- поверх `FundingEvent` добавлен operator arm flow `FundingEvent -> ArmedTrade`.
- появился multi-venue metadata registry и diagnostics layer для venue sync/request timing.

## Legacy vs New
### Legacy
- `ApprovedFundingEntity`
- `FundingApprovalService`
- `FundingSchedulerService`
- `OrderExecutorService`
- `TestOrderEngine`
- `/api/test-orders`

Legacy-контур больше не является целевой архитектурой и должен рассматриваться как временный совместимый слой.

### New Domain
- `domain.event`: `FundingEvent`
- `domain.trade`: `ArmedTrade`, `Position`, `TradeOutcome`
- `domain.execution`: `OrderIntent`, `OrderAttempt`
- `domain.profile`: `VenueTimingProfile`
- `application.*`: command/query services и порты
- `infrastructure.persistence.*`: новая persistence-модель

## Candidate Source and Telegram
- Кандидаты теперь формируются из внешнего funding API, а не из TDLib.
- Funding source обновляет `FundingWatchlistService` и создаёт persisted `SignalCandidate`.
- Telegram bot в этой фазе не участвует в candidate creation и остаётся только UI-слоем для оператора.
- Operator arm workflow и review по-прежнему остаются только в REST.

## Safety model
- Default runtime: `DISABLED`
- Legacy execution default: `false`
- Live execution возможен только по явному opt-in
- `gate` намеренно заблокирован в legacy execution path

## Direction
- Сейчас: strong modular monolith
- Позже возможно выделение:
  - `signal-ingest`
  - `control-api`
  - `execution-engine`
- Control plane уже состоит из:
  - candidate review
  - funding event management
  - armed trade preparation
  - venue diagnostics
