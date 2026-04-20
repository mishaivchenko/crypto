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

`engine-app` сейчас не исполняет ордера. Он только читает execution plans из `monitor-app` через protected internal REST.

Internal API защищён `X-Internal-Token`.

## Operator API

Operator endpoints защищены `X-Operator-Token`.

Токены bootstrap-операторов задаются через `SECURITY_OPERATOR_BOOTSTRAP_USERS` и хранятся в базе только как SHA-256 hash.

## Exchange Keys

Exchange keys хранятся per operator:

- таблица `operator_exchange_credential`.
- AES-GCM encryption.
- master key только из `CREDENTIALS_MASTER_KEY_BASE64`.
- raw secrets никогда не возвращаются через API.

Если credential storage включён и master key отсутствует, startup падает fail-closed.

## Funding Direction

Funding `ArmedTrade` остаётся `SHORT-only`.

`LONG` rejected на уровне application service и API tests.
