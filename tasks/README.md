# Tasks

Беклог проекта. Каждая задача — отдельный файл.

## Формат имени файла

`<тип>-<номер>-<slug>.md` — например `B-4-armed-trade-timing-defaults.md`

- `B-*` — Bug
- `F-*` — Feature

## Статусы

- `open` — баг, требует фикса
- `pending` — фича, ещё не начата
- `in-progress` — в работе
- `done` — завершена (файл можно архивировать или удалить)

## Список задач

| ID | Тип | Приоритет | Статус | Описание |
|----|-----|-----------|--------|----------|
| [B-1](B-1-bybit-geo-block.md) | Bug | medium | open | Bybit geo-blocked для UA IP |
| [B-2](B-2-engine-credentials-ui.md) | Bug | medium | open | Engine credentials не подхватываются через UI |
| [B-3](B-3-engine-flags-not-visible.md) | Bug | low | open | ENGINE flags не видны в UI |
| [B-4](B-4-armed-trade-timing-defaults.md) | Bug | high | open | ArmedTrade: дефолт времени, секунды в picker, интервал 150→30мс |
| [F-1](F-1-latency-ui.md) | Feature | high | pending | Latency UI — p50/p95/p99 в venue-detail |
| [F-2](F-2-production-deployment.md) | Feature | high | pending | Production Deployment |
| [F-3](F-3-autonomous-loop-testing.md) | Feature | medium | pending | Autonomous Loop Testing |
| [F-4](F-4-ai-advisor-quality-loop.md) | Feature | low | pending | AI Advisor Quality Loop |
| [B-5](B-5-engine-credentials-from-monitor.md) | Bug/Arch | high | open | Engine получает credentials из monitor — единое хранилище ключей |
| [F-5](F-5-engine-status-ui.md) | Feature | medium | pending | Engine status флаги в UI |
