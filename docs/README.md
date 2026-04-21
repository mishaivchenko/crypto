# Funding Platform Docs

Эта папка описывает актуальную линию приложения: split на `monitor-app` и `engine-app`, Funding API candidate source, venue-aware review flow, encrypted operator credentials и latency-aware armed trade planning.

## Читать в таком порядке

1. `00-current-state.md` — что приложение умеет сейчас.
2. `01-system-flow.md` — текущий бизнес-flow и границы.
3. `02-modules.md` — модули и ответственность.
4. `03-runtime-config.md` — runtime ENV и safety defaults.
5. `04-api-surface.md` — основные REST endpoints.
6. `05-venue-metadata-and-latency.md` — venues, metadata, timing и burst-entry.
7. `06-data-model.md` — актуальная persistence model.
8. `07-runbook.md` — запуск и smoke-check.
9. `08-safety.md` — почему текущий runtime safe-by-default.
10. `09-next-mvp-steps.md` — что осталось до MVP.
11. `10-trade-history-ui-vision.md` — видение UI истории сделок.
12. `11-observability-isolated.md` — отдельный observability-контур без влияния на default runtime.
