# Deploy Notes

## Shape

Production deployment is two independent services:

- `monitor-app` on the control-plane VPS.
- `engine-app` close to exchanges.

`engine-app` does not read the monitor database. It fetches execution plans from monitor internal REST using `X-Internal-Token`.

## Local Docker Smoke

```bash
cp deploy/.env.example .env
# Fill SECURITY_OPERATOR_BOOTSTRAP_USERS, INTERNAL_ENGINE_TOKEN and CREDENTIALS_MASTER_KEY_BASE64.
docker compose up --build
```

Monitor:

```text
http://localhost:8090
```

Engine:

```text
http://localhost:8091/internal/engine/summary
```

## Images

The same `Dockerfile` can build either module:

```bash
docker build \
  --build-arg APP_MODULE=monitor-app \
  --build-arg APP_CLASSIFIER=monitor \
  --build-arg APP_PORT=8090 \
  -t funding-monitor:2.0.0 .

docker build \
  --build-arg APP_MODULE=engine-app \
  --build-arg APP_CLASSIFIER=engine \
  --build-arg APP_PORT=8091 \
  -t funding-engine:2.0.0 .
```

## Secrets

Operator tokens are bootstrapped from `SECURITY_OPERATOR_BOOTSTRAP_USERS` and stored only as hashes.

Exchange credentials are written through the operator credential API and stored encrypted with AES-GCM. Raw keys must not be committed to the repository or image.
