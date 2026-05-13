# Safety

## Current Runtime

Текущая линия safe-by-default не потому, что старый execution guard блокирует ордера, а потому что старого execution-кода больше нет в активной сборке.

Что удалено из активной сборки:

- old funding approval aggregate.
- old test-order endpoint.
- old scheduled order execution.
- old bot control plane.
- old exchange-client layer.

## Engine

`engine-app` доходит до полного execution boundary: читает plans из `monitor-app`, вызывает `LiveExchangeExecutionPort` и сохраняет `OrderAttempt` обратно в monitor.

Что важно:

- execution loop выключен по умолчанию (`ENGINE_EXECUTION_LOOP_ENABLED=false`);
- live order submission выключен по умолчанию (`ENGINE_LIVE_ORDER_ENABLED=false`);
- даже `prod-like` профиль не включает loop/live-order без явных ENV;
- manual `run-once` можно вызвать только явно;
- без engine credentials попытка сохраняется как `FAILED`;
- Gate testnet подтверждён (FILLED), Bybit geo-blocked для UA IPs.

Internal API защищён `X-Internal-Token`.

## Operator API

Operator endpoints защищены `X-Operator-Token`.

Токены bootstrap-операторов задаются через `SECURITY_OPERATOR_BOOTSTRAP_USERS` и хранятся в базе только как SHA-256 hash.

Safe local baseline:

- без профиля или с `local-safe` auth выключен;
- `staging` и `prod-like` включают auth обратно;
- deployment не должен полагаться на documentation-only defaults, а должен выставлять профиль явно.

## Exchange Keys

Exchange keys хранятся per operator:

- таблица `operator_exchange_credential`.
- AES-GCM encryption.
- master key только из `CREDENTIALS_MASTER_KEY_BASE64`.
- raw secrets никогда не возвращаются через API.

Если credential storage включён и master key отсутствует, startup падает fail-closed.

`local-safe` держит credential storage выключенным, а `staging` / `prod-like` включают его обратно.

## Schema Safety

`monitor-app` больше не меняет schema автоматически через Hibernate.

Теперь:

- Flyway управляет versioned migrations;
- пустая база создаётся из baseline migration;
- существующая SQLite база получает `flyway_schema_history` через baseline-on-migrate;
- JPA использует `validate`, чтобы fail-fast при schema drift.

## Trade Lifecycle Guardrails

**Cancel state validation**: `DELETE /api/v1/armed-trades/{id}` доступен только для CANCELLABLE_STATES (ARMED, ENTRY_PENDING, ENTRY_ATTEMPTED, OPEN, EXIT_PENDING). Попытка отмены сделки в CLOSED / FAILED / CANCELLED бросает 422.

**Max-concurrent limit** (default 3): при создании `ArmedTrade` подсчитываются активные сделки (те же 5 состояний); при достижении лимита — 422. Настраивается через `monitor.risk.max-concurrent-armed-trades`.

**Disabled venues**: список `monitor.risk.disabled-venues` (comma-separated) блокирует создание `ArmedTrade` для указанных venues с 422. Используется для ограничения execution до подтверждённых venues (Gate testnet confirmed; Bybit geo-blocked для UA IPs).

## Funding Direction

Funding `ArmedTrade` остаётся `SHORT-only`.

`LONG` rejected на уровне application service и API tests.
