# Backlog

## Bugs

| # | Описание | Приоритет |
|---|----------|-----------|
| B-1 | Bybit geo-blocked для UA IP даже на testnet — ошибка "regulatory restrictions". Требует VPN или не-UA аккаунт. | medium |
| B-2 | Engine credentials не подхватываются через UI monitor — нужно отдельно выставлять `ENGINE_CREDENTIALS_*` env vars в engine-контейнере. Неочевидно для оператора. | medium |
| B-3 | `ENGINE_LIVE_ORDER_ENABLED` и `ENGINE_KILL_SWITCH_ENABLED` не видны в UI — оператор не может понять почему LAB Run Once падает без чтения логов. | low |

---

## Features

| # | Описание | Статус | Приоритет |
|---|----------|--------|-----------|
| F-1 | **Latency UI** — p50/p95/p99 секция в venue-detail, probe button, форма default latency. Бэкенд готов, только UI. | pending | high |
| F-2 | **Production Deployment** — monitor на VPS, engine в Singapore (low-latency к биржам), secrets из ENV/secret manager. | pending | high |
| F-3 | **Autonomous Loop Testing** — несколько одновременных сделок на testnet, latency SLA guard, kill switch через runtime API. | pending | medium |
| F-4 | **AI Advisor Quality Loop** — сбор реальных результатов сделок обратно в историю советника, дообучение порогов GO/WATCH/PASS. | pending | low |
| F-5 | **Engine status в UI** — показывать состояние флагов `LIVE_ORDER_ENABLED`, `KILL_SWITCH`, `EXECUTION_LOOP` прямо в интерфейсе, чтобы оператор видел что включено. | pending | medium |
