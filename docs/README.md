# Funding Platform Docs

Эта папка описывает актуальную линию приложения: split на `monitor-app` и `engine-app`, Funding API candidate source, venue-aware review flow, encrypted operator credentials, latency-aware armed trade planning, quality gates и Engine TDD foundation.

GitHub Issues and milestones are the authoritative tracker. Project boards are optional views.

## Current Index

1. `PROJECT_STATUS.md` — текущая phase map, source of truth, closure gates.
2. `00-current-state.md` — что приложение умеет сейчас.
3. `01-system-flow.md` — текущий business flow и границы.
4. `02-modules.md` — модули и ответственность.
5. `03-runtime-config.md` — runtime ENV и safety defaults.
6. `04-api-surface.md` — основные REST endpoints.
7. `05-venue-metadata-and-latency.md` — venues, metadata, timing и burst-entry.
8. `06-data-model.md` — актуальная persistence model.
9. `07-runbook.md` — запуск и smoke-check.
10. `08-safety.md` — почему текущий runtime safe-by-default.
11. `09-next-mvp-steps.md` — post-MVP hardening/go-live backlog overview.
12. `10-trade-history-ui-vision.md` — видение UI истории сделок.
13. `11-observability-isolated.md` — отдельный observability-контур без влияния на default runtime.
14. `12-engine-tdd-migration-prompt.md` — historical prompt for the Engine TDD migration.
15. `13-engine-tdd-migration-plan.md` — Engine TDD migration plan and baseline rationale.
16. `14-code-quality-modernization-plan.md` — quality foundation plan and cleanup waves.
17. `engine-tdd/` — active Engine TDD program, requirements, and gap matrix.
