# MVP Scope (актуализировано 2026-01-31)
- Один бинарник (Spring Boot jar) + Telegram bot UI.
- Биржи: Binance, Bybit, Gate (testnet/prod переключаются через ENV).
- Хранение: SQLite в volume `/data`.
- Планировщик исполняет сделки в due-window перед funding time.
- CI: проверки на push/PR, docker push из `main`.
