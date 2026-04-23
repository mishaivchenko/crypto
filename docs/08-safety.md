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

`engine-app` сейчас доходит до execution boundary: читает execution plans из `monitor-app`, строит entry attempts, вызывает `ExecutionPort` и сохраняет `OrderAttempt` обратно в monitor.

Что важно:

- execution loop выключен по умолчанию;
- даже `prod-like` профиль не включает loop без явного `ENGINE_EXECUTION_LOOP_ENABLED=true`;
- manual `run-once` можно вызвать только явно;
- без engine credentials попытка сохраняется как `FAILED`;
- если credentials есть, live order HTTP submission всё равно остаётся guarded в этой фазе;
- реальные exchange order adapters будут включаться отдельной фазой.

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

## Funding Direction

Funding `ArmedTrade` остаётся `SHORT-only`.

`LONG` rejected на уровне application service и API tests.
