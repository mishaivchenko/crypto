# Overview

## Реальность репозитория после Phase 0-1
Это больше не должен восприниматься как “funding arbitrage bot”.

Текущий shape:
- один Spring Boot сервис;
- Telegram/TDLib ingest продолжает работать;
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

## Telegram
- TDLib ingestion остаётся активным источником кандидатов и наблюдаемого состояния.
- Telegram signal трактуется как `candidate observation`, а не как команда на создание сделки.
- Telegram bot в этой фазе — ingest/diagnostic слой, а не торговый control plane.
- На Phase 2 ingest также создаёт persisted `SignalCandidate`, а на Phase 3 operator arm workflow остаётся только в REST.

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
