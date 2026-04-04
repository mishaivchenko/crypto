# Migration to New Funding Signal Source

## Что изменено в последнем коммите
- Полностью убран runtime ingest через TDLib.
- Новый источник кандидатов теперь:
  - `https://uainvest.com.ua/api/funding?sort_by=funding&sort_dir=asc&limit=30`
- Добавлен polling service, который:
  - получает funding entries,
  - нормализует символы,
  - обновляет watchlist,
  - создаёт `SignalCandidate` через существующий application flow.
- Telegram больше не участвует в формировании кандидатов и остаётся только optional bot UI.
- Обновлены тесты, Docker/runtime config и документация под новый source.

## Текущий flow проекта
```text
Funding API -> SignalCandidate -> Review -> FundingEvent -> ArmedTrade -> Trade Journal
```

Дополнительно:
- venue metadata sync работает отдельно;
- legacy execution path всё ещё существует, но по умолчанию заблокирован;
- Telegram bot не является control plane.

## Состояние готовности
- Candidate ingestion: готово
- Candidate review: готово
- FundingEvent creation: готово
- ArmedTrade preparation: готово
- Trade journaling: готово
- Multi-venue metadata/diagnostics: готово
- Live execution нового домена: не готово
- Timing/profiling engine: не готово
- Outcome/PnL layer: не готово

Итог:
проект сейчас находится в состоянии `control plane ready, execution plane not ready`.

## Основные модули
- `infrastructure.source`
  - новый funding API source
- `application.candidate`
  - ingest, normalization, review
- `domain.candidate / domain.event / domain.trade`
  - новый основной домен
- `watchlist`
  - наблюдаемое funding state для review и downstream flow
- `application.venue / infrastructure.exchange`
  - metadata sync и venue diagnostics
- `legacy.* / scheduler / execution`
  - старый контур, сохранён как transitional и guarded
- `api`
  - internal REST API и optional Telegram bot UI

## Следующий логичный шаг
Следующая правильная фаза:
- построить execution-plane нового домена без возврата к legacy flow.

Минимальный план:
1. Ввести новый execution workflow от `ArmedTrade`.
2. Добавить `OrderIntent -> OrderAttempt -> Position -> TradeOutcome`.
3. Сделать `SHADOW` execution mode для 3+ бирж.
4. Начать собирать реальные latency / request timing / ack timing / fill timing метрики.
5. Подготовить отдельный Singapore-oriented execution deployment.

## Основной риск сейчас
- `nextFundingAt` пока вычисляется эвристически из `updated_at` и `funding_interval`.
- Если upstream source даст более точное funding time, это место нужно будет заменить в первую очередь.
