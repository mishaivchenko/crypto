# Scheduler Design

## Цель
- всегда находить ближайшие fundings
- не жрать CPU
- не пропускать события
- не запускать tick параллельно

## Механика
- discovery polling каждые N секунд (например 10-15s)
- due-window обработка:
    - from = now - maxLateness
    - to = now + lookahead
    - select active=1 executed=0 next_funding_at BETWEEN from..to
- mark executed после попытки исполнения (или сохранить статус)
- Значения по умолчанию конфигурируются через ENV (см. `application.yml`), в Docker override через `SCHED_*`.

## Проблемы, которые закрываем
- несколько монет в один момент: выбираем список due и обрабатываем все
- двойной tick: AtomicBoolean guard
- добавление новых записей руками: discovery polling должен увидеть
