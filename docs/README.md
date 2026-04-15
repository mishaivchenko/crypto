# Funding Platform Docs

Эта папка описывает только актуальную линию приложения: `2.0.0`, split на `monitor-app` и `engine-app`, Funding API candidate source, venue-aware review flow и latency-aware armed trade planning.

Старые phase-документы, TDLib-заметки, Binance-first MVP и legacy scheduler specs удалены из активной документации, потому что они больше мешали понимать текущий продукт.

## Читать в таком порядке

1. `00-current-state.md` — что приложение умеет сейчас.
2. `01-system-flow.md` — текущий бизнес-flow и границы.
3. `02-modules.md` — модули и ответственность.
4. `03-runtime-config.md` — runtime ENV и safety defaults.
5. `04-api-surface.md` — основные REST endpoints.
6. `05-venue-metadata-and-latency.md` — venues, metadata, timing и burst-entry.
7. `06-data-model.md` — актуальная persistence model.
8. `07-runbook.md` — запуск и smoke-check.
9. `08-safety-and-legacy.md` — что safe-by-default и что осталось legacy.
10. `09-next-mvp-steps.md` — что осталось до MVP.
11. `10-trade-history-ui-vision.md` — видение UI истории сделок.

