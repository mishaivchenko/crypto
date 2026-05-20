# MVP Steps — Status

## Выполненные шаги

### Step 1: Execution Ports ✓
Live order submission работает. Gate testnet подтверждён (ACT/USDT SHORT FILLED 2026-05-09). Все 5 venues (Gate, Bybit, OKX, Bitget, KuCoin) имеют venue-specific адаптеры в `LiveExchangeExecutionPort`. Bybit geo-blocked для UA IPs без VPN.

### Step 2: Engine Runtime Loop ✓
Engine execution loop operational. `EngineExecutionScheduler` тикает каждые 250 ms (по умолчанию). Burst entry — несколько попыток со spacing — поддерживается для live venues. Включается через `ENGINE_EXECUTION_LOOP_ENABLED=true`.

### Step 3: Latency Calibration ✓
Реализовано полностью:
- Warm-up probes перед исполнением.
- p50/p95/p99 per venue + operation.
- Venue default latency как fallback.
- Manual override через `manualLatencyAdjustmentMs`.
- Отображение в UI.
- Manual probe endpoint: `POST /api/v2/monitor/venues/{venue}/latency-probe`.

### Step 4: Position And Exit ✓
Полный lifecycle реализован:
`OrderAttempt (FILLED entry)` → `ArmedTrade → OPEN` + position записывается →
engine обнаруживает `EXIT_WINDOW` → `submitOrder(reduceOnly=true)` →
`OrderAttempt (FILLED exit)` → `recordPosition(CLOSED, exitPrice)` → `ArmedTrade → CLOSED`

### Step 5: Trade Outcome ✓
`recordTradeOutcome` рассчитывает gross/net PnL, fees, slippage. Upsert-паттерн — повторные вызовы корректно перезаписывают outcome. Trade History UI показывает реальные данные.

### Step 6: Risk Guardrails ✓
- Max-concurrent armed trades (default 3, `monitor.risk.max-concurrent-armed-trades`).
- Per-venue disable list (`monitor.risk.disabled-venues`).
- `SHORT`-only funding direction enforced на service layer.
- Cancel trade flow для CANCELLABLE_STATES.

---

## Что сделано сверх плана

### AI Signal Advisor
DeepSeek анализирует каждый сигнал асинхронно. Возвращает `GO` / `WATCH` / `PASS` с confidence и reasoning на русском. Советник понимает стратегию: ловим ценовое движение после фандинга, а не сам фандинговый платёж.

### Telegram Bot
`@funding_arbitrage_bot_bot` — уведомления о новых сигналах, статус системы, FAQ.

### Trade History UI
Полноценная история сделок: фильтры, health badges, detail drawer `Source → Event → Plan → Attempts → Position → Outcome`, latency strip, attempt ladder.

---

## Что остаётся

### Production Deployment Shape (Step 7)
- Деплой `monitor-app` на control-plane VPS.
- Деплой `engine-app` в Singapore (low-latency path к биржам).
- Secrets из ENV / secret manager.
- Observability: Prometheus + Grafana (конфигурация в `deploy/observability/`, не часть основного flow).

### Autonomous Loop Hardening
- Тестирование нескольких одновременных сделок на testnet.
- Latency SLA — отказ исполнения если latency profile устарел.
- Kill switch через runtime API.

### AI Advisor Quality Loop
- Сбор фактических результатов сделок обратно в историю советника.
- Возможность дообучить пороги GO/WATCH/PASS на реальных данных выходов.
