# Funding Arbitrage Bot (Funding-only MVP)

## Что это
Один сервис, который:
- читает сигналы (пары) из Telegram канала (через TDLib),
- позволяет в Telegram UI выбрать пары, биржи и сумму USDT,
- сохраняет подтверждённые фандинги в SQLite,
- планирует выполнение по времени next_funding_at,
- за секунды до funding-time отправляет команды на биржи (тестовые/прод).

## Компоненты
- Telegram ingestion (TDLib)
- Telegram UI bot (команды/кнопки)
- Persistence (SQLite + JPA)
- Scheduler (poll + due-window)
- Execution (Exchange clients: Binance, Bybit, Gate)
