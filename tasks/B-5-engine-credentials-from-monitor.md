# B-5 — Engine получает credentials из monitor (единое хранилище ключей)

**Тип:** Bug / Architecture  
**Приоритет:** high  
**Статус:** open

## Проблема

Engine читает credentials из своих ENV vars (`engine.credentials.<venue>.api-key` и т.д.).
Monitor хранит те же ключи в своей SQLite БД (AES-GCM зашифровано).
Оператор вынужден вводить ключи в двух местах — в UI monitor и в ENV vars engine.
Для Gate это сделали вручную, для OKX/KuCoin забыли → все попытки падают с "Missing engine credentials".

## Решение

Monitor — единое хранилище ключей. Engine подтягивает credentials из monitor по internal API при старте, кэширует в памяти.

### Шаги

1. **Monitor** — добавить endpoint `GET /internal/engine/credentials/{venue}?mode={testnet|production}`
   - Возвращает расшифрованные `{ apiKey, secretKey, passphrase }` для venue
   - Защищён `X-Internal-Token` (уже есть)
   - Если ключей нет — 404

2. **Engine** — добавить `EngineCredentialClient` (Feign или RestClient) вызывающий этот endpoint
   - При старте (или ленивая загрузка при первом использовании venue) подтягивает credentials для всех venue из `live-enabled-venues`
   - Кэширует в `Map<String, VenueCredentials>` в памяти
   - `LiveExchangeExecutionPort.credential()` читает из кэша вместо `environment.getProperty()`

3. **Удалить** `engine.credentials.*` из `application.yml` и `application-testnet.yml`

4. **Опционально** — endpoint `POST /internal/engine/credentials/reload` чтобы не перезапускать engine при смене ключей

## Детали

- Когда engine переедет в Singapore — endpoint доступен через Cloudflare Tunnel (уже настроен)
- При смене ключей в monitor UI — нужно перезапустить engine (или вызвать reload endpoint)
- Не добавлять новую инфраструктуру — monitor уже является control plane

## Затронутые файлы

- `monitor-app/` — новый internal API endpoint + сервис
- `engine-app/` — новый Feign client + рефактор `LiveExchangeExecutionPort.credential()`
- `platform-core/` — возможно новый контракт `EngineCredentialsResponse`
- `engine-app/src/main/resources/application.yml` — удалить `engine.credentials.*`
- `engine-app/src/main/resources/application-testnet.yml` — удалить `engine.credentials.*`
