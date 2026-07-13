# HTTP Client Creation Audit

**Date:** 2026-07-13
**Scope:** Section 6 — Application Structure: HTTP client creation across all 3 modules

---

## 1. Feign Client (telegram-bot-app only)

**Interface:** `MonitorApiClient` — one interface, 3 endpoints:
- `GET /api/v1/candidates` — page of `CandidateSummary`
- `GET /api/v1/armed-trades` — list of `ArmedTradeSummary`
- `GET /api/v2/monitor/overview` — `MonitorOverview`

```java
@FeignClient(name = "monitor-api", url = "${monitor.base-url}")
```

**Configuration class:** `MonitorFeignConfig` — single `@Bean`:
- `RequestInterceptor` — adds `X-Operator-Token` header from `${monitor.operator-token}`

**Activation:** `@EnableFeignClients(basePackages = "com.crypto.funding.telegram.client")` on `TelegramBotApplication`

### Missing Feign customizations (none exist across codebase):
- ❌ No custom `Feign.Builder` bean
- ❌ No custom `Decoder` / `Encoder` / `ErrorDecoder` / `Retryer` / `Logger.Level`
- ❌ No `feign.httpclient.enabled` or `feign.okhttp.enabled` properties
- ❌ No timeout configuration on Feign (uses defaults: connectTimeout=10s, readTimeout=60s)
- ❌ No circuit breaker (no `spring-cloud-starter-circuitbreaker-resilience4j` on classpath)

---

## 2. Shared `java.net.http.HttpClient` Bean (monitor-app)

**Config class:** `VenueHttpClientConfig`
```java
@Bean
HttpClient venueHttpClient( VenueHttpProperties properties ) {
    return HttpClient.newBuilder()
        .connectTimeout( Duration.ofMillis( properties.getConnectTimeoutMs() ) )
        .followRedirects( HttpClient.Redirect.NEVER )
        .version( properties.isPreferHttp2() ? HttpClient.Version.HTTP_2 : HttpClient.Version.HTTP_1_1 )
        .build();
}
```

**Properties:** `VenueHttpProperties` (`@ConfigurationProperties(prefix = "trading.http")`)

| Property | Default | ENV override | Description |
|---|---|---|---|
| `trading.http.connect-timeout-ms` | 1000 | `TRADING_HTTP_CONNECT_TIMEOUT_MS` | Connect timeout |
| `trading.http.request-timeout-ms` | 5000 | `TRADING_HTTP_REQUEST_TIMEOUT_MS` | Request/read timeout (NOT set on HttpClient builder — only connectTimeout is!) |
| `trading.http.prefer-http2` | true | `TRADING_HTTP_PREFER_HTTP2` | Prefer HTTP/2 |

**`requestTimeoutMs` is declared in properties but NEVER applied to the HttpClient bean** — `HttpClient.Builder` only supports `connectTimeout()`, not a read/request timeout. Per-request timeout must be set on each `HttpRequest.Builder.timeout()`.

**Consumers (20 classes in monitor-app):** All inject via constructor injection:
- 5x `MetadataAdapter` (Bybit, Gate, OKX, KuCoin, Bitget)
- 5x `CredentialChecker` (Bybit, Gate, OKX, KuCoin, Bitget)
- 5x `MarkPriceAdapter` (Bybit, Gate, OKX, KuCoin, Bitget)
- 5x `OrderBookAdapter` (Bybit, Gate, OKX, KuCoin, Bitget)
- 1x `FundingApiPayloadFetcher`

These 20 classes set the per-request timeout individually — verify that `VenueHttpProperties.requestTimeoutMs` is actually read by each adapter (codebase finding needed per adapter).

---

## 3. Standalone `HttpClient` Instances (not using shared bean)

### `VenueLatencyProbeService` (monitor-app)
```java
this.httpClient = HttpClient.newBuilder()
    .connectTimeout( Duration.ofSeconds( 5 ) )
    .build();
```
- Hardcoded 5s connect timeout — does NOT use `VenueHttpProperties`
- Per-request timeout hardcoded to 8 seconds
- **Issue:** Duplicates HttpClient creation; config not centralized

### `EngineExecutionService` (engine-app)
```java
this.probeHttpClient = HttpClient.newBuilder().build();
```
- **No connect timeout** — default is infinite (block forever!)
- Used for latency probe at line 765
- **Issue: Missing connect timeout means a network stall blocks the execution loop indefinitely**

### `LiveExchangeExecutionPort` (engine-app)
```java
// 3 constructors use bare defaults:
HttpClient.newHttpClient()

// 4th overload accepts injected HttpClient for testing:
protected LiveExchangeExecutionPort(..., HttpClient httpClient, ...)
```
- No connect timeout on the default instances
- `REQUEST_TIMEOUT = Duration.ofSeconds(10)` is applied per-request on every `HttpRequest.Builder.timeout()` call
- **Issue: No connect timeout means TCP connection hangs forever if exchange is unreachable**
- `LiveExchangeExecutionPort` handles ALL 5 venues' live order submission (Bybit, Gate, OKX, KuCoin, Bitget)

---

## 4. `RestClient` Instances (Spring Boot auto-configured)

All 3 use Spring Boot's auto-configured `RestClient.Builder` injected via constructor:

| Class | Module | Base URL | Headers | Purpose |
|---|---|---|---|---|
| `EnginePlanClient` | engine-app | `engine.monitor-base-url` (default: `http://localhost:8090`) | `X-Internal-Token` | Plan fetch, order recording, metrics, credentials |
| `EngineControlService` | monitor-app | `monitor.engine-control.base-url` (default: `http://localhost:8091`) | `X-Internal-Token` | Engine runtime control |
| `DeepSeekClient` | monitor-app | `ai.deepseek.base-url` (default: `https://api.deepseek.com`) | `Authorization: Bearer {key}` | AI signal analysis |

**None of these:**
- Customize timeouts (use auto-configured defaults)
- Set a custom `ClientHttpRequestFactory`
- Add request interceptors beyond the auth header
- Configure retry or error handling (except DeepSeekClient's `.onStatus()` for 4xx/5xx)

Spring Boot's default `RestClient.Builder` uses `SimpleClientHttpRequestFactory` (backed by `HttpURLConnection`) — no connection pooling, no keep-alive, no request timeouts unless explicitly configured via `spring.restclient.*` properties or a custom `ClientHttpRequestFactory` bean. **No such configuration exists anywhere in the codebase.**

---

## 5. Summary of HTTP Client Types

| Type | Count | Configurability | Concerns |
|---|---|---|---|
| Feign (OpenFeign) | 1 interface, 3 endpoints | Defaults only | No timeouts configured; no error decoder; no retry; no circuit breaker |
| Shared `HttpClient` bean | 1 bean, 20 consumers | Centralized connect timeout only | requestTimeoutMs property defined but never wired to HttpClient; not used by engine-app at all |
| Standalone `HttpClient` | 3 instances | Hardcoded | VenueLatencyProbeService (5s), EngineExecutionService (NO timeout), LiveExchangeExecutionPort (NO connect timeout) |
| `RestClient` | 3 instances | Auto-configured defaults | No timeouts set; no connection pooling; no error handling beyond basic status check |

---

## 6. Key Findings

### Issue 1: Missing connect timeouts in engine-app
`EngineExecutionService.probeHttpClient` and `LiveExchangeExecutionPort` default constructors create `HttpClient` with **no connect timeout** (infinite). A network partition would block the execution loop indefinitely.

### Issue 2: `VenueHttpProperties.requestTimeoutMs` is unused
The property exists and defaults to 5000ms but is never applied. Each adapter class must set its own per-request timeout — verify whether each of the 20 adapters uses `properties.getRequestTimeoutMs()`.

### Issue 3: No `RestClient` customization
3 `RestClient` instances use Spring Boot defaults: no connection pooling, no explicit timeouts, no custom error handling (except DeepSeekClient), no retry. Backed by `HttpURLConnection` (no keep-alive by default).

### Issue 4: Feign has no timeout configuration
The sole Feign client has no `connectTimeout` or `readTimeout` configured — uses Feign defaults (10s connect, 60s read). No circuit breaker or retry.

### Issue 5: No HTTP client abstraction layer
Each application module creates its own HTTP clients independently. No shared `HttpClient` bean exists for engine-app. The shared bean in monitor-app is not accessible to engine-app (different Spring context).
