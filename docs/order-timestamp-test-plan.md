# План тестирования фиксации времени открытия ордера

Цель: проверить, что после успешного открытия тестового ордера мы сохраняем серверное время ответа и время открытия на бирже (если недоступно — делаем доп. GET), логируем и пишем в БД в новую таблицу `order_execution_time`.

## Уровни и сценарии
- **Unit — Rest-клиенты**
  - Binance: `updateTime` попадает в `exchangeTsMillis`, status OK.
  - Gate: `create_time` (sec) конвертируется в мс и сохраняется.
  - Bybit: `createdTime` из v5 ответа парсится и сохраняется.
  - Ошибка/отсутствие поля: `exchangeTsMillis` остаётся `null`, источник `UNKNOWN`.

- **Unit — OrderExecutorService**
  - Happy-path: после открытия ордера вызывается `OrderExecutionTimeStore.save(...)` с серверным временем, биржевым временем (если было) и источником `RESPONSE_BODY` или `FOLLOW_UP_QUERY`.
  - exchanges пустые → ордер не ставится, запись в store не создаётся, funding помечается executed.
  - Недостаточный баланс/qty → бросается IllegalArgumentException, ничего не сохраняется.
  - follow-up: если `exchangeTsMillis == null`, вызывается `fetchOrderTimestamp` у клиента; при исключении продолжаем без падения.

- **E2E (ограниченно, без реальных HTTP)**
  - Поток арбитража использует fake client: ордер ставится, логика не падает, интеграция с сервисом не требует реального БД подключения.

## Набор тестов (автоматизированы)
- `BinanceRestClientTest.placesLimitOrder` — покрывает получение `updateTime`.
- `GateRestClientTest.placesMarketOrder` — проверяет `create_time` → мс.
- `BybitRestClientTest.placesMarketOrder` — проверяет `createdTime`.
- `OrderExecutorServiceTest` (моки) — happy path, empty exchanges, ошибки qty; проверка вызова `OrderExecutionTimeStore`.
- `ArbitrageFlowE2ETest` — сквозной сценарий с fake client, mock store.

## Регрессия/ручное (при необходимости)
- Smoke: `./gradlew test` — все тесты зелёные.
- При интеграции с реальными биржами: проверить наличие записи в таблице `order_execution_time` после размещения тестового ордера (серверное время и биржевое время не `null`).
