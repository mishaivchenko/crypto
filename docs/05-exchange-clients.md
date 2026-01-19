# Exchange Clients

## Требования MVP
- единый интерфейс:
    - placeTestOrder(symbol, usdtAmount, exchange)
    - позже: placeMarketOrder(symbol, side, qty, leverage, marginMode)
- маппинг символов под конкретную биржу
- обработка ошибок:
    - invalid symbol
    - min notional
    - precision

## Стратегия теста latency
- тестовый запрос/ордер на testnet
- измеряем:
    - t0 перед запросом
    - t1 после ответа
    - store p50/p95 в памяти или таблице
- используем p95 + safetyMargin для offset перед funding-time
