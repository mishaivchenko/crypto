# Version 2.0.0

## Идея

Версия `2.0.0` выделяет две критические runtime-роли:

- `monitor-app` — операторский контур
- `engine-app` — лёгкий execution-preparation контур

Обе опираются на общий `platform-core`.

## Текущий flow

`Funding API -> SignalCandidate -> FundingEvent -> ArmedTrade -> Trade Journal`

## Модули

### `platform-core`
- домен
- application services
- persistence
- REST controllers
- venue metadata
- safety guards

### `monitor-app`
- UI
- overview API
- operator workflows
- venue visibility

### `engine-app`
- lightweight summary/planning API
- armed trade execution windows
- minimal dependencies at runtime
- entry burst plan: несколько SHORT-входов с настраиваемым spacing в миллисекундах
- latency-aware triggers: planned entry сдвигается на измеренную + ручную latency

## Почему это важно

- monitor можно развивать как control plane
- engine можно держать максимально узким и быстрым
- дальнейший split на deploy/runtime становится проще
- задержки становятся измеряемой частью плана, а не скрытым предположением

## Что уже проверено

- модульная сборка проходит
- engine стартует отдельно
- monitor собирается отдельно
- бизнес-flow покрыт тестами
- engine planning покрыт отдельными тестами
- funding arm не принимает LONG
- test-order/venue timings участвуют в расчёте effective entry latency

## Основные команды

- `./gradlew bootRunMonitor`
- `./gradlew bootRunEngine`
- `./gradlew test`
- `./gradlew build`
