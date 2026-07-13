# Audit Round 3 — Phase 3: Spring Boot, Config & Secrets

**Deliverables:** Runtime Process Matrix | Environment Matrix | Secret Inventory

## Section 6 — Spring Boot and Application Framework

### Version and starters
- [x] Run `./gradlew dependencyInsight --dependency spring-boot --no-daemon` — record exact Spring Boot version
  - **Spring Boot version: 3.5.14** (Spring Cloud 2025.0.2)
  - Confirmed via `build.gradle` line: `springBootVersion = '3.5.14'` and `dependencyInsight` on both modules
  - BOM: `org.springframework.boot:spring-boot-dependencies:3.5.14`
- [x] Identify all Spring Boot starters in each application module
  - **monitor-app** (6 starters):
    - `spring-boot-starter` — core auto-config, logging, embedded container support
    - `spring-boot-starter-actuator` — health, metrics, env endpoints
    - `spring-boot-starter-data-jpa` — JPA/Hibernate (transitive: `spring-boot-starter-aop`, `spring-boot-starter-jdbc`)
    - `spring-boot-starter-validation` — Bean Validation (Hibernate Validator)
    - `spring-boot-starter-web` — REST controllers, embedded Tomcat (transitive: `spring-boot-starter-json`, `spring-boot-starter-tomcat`)
    - `spring-cloud-starter-openfeign` — declarative HTTP clients
  - **engine-app** (3 starters):
    - `spring-boot-starter` — core
    - `spring-boot-starter-web` — REST, embedded Tomcat
    - `spring-boot-starter-actuator` — health, metrics
  - **telegram-bot-app** (3 starters):
    - `spring-boot-starter` — core
    - `spring-boot-starter-web` — REST, embedded Tomcat
    - `spring-cloud-starter-openfeign` — HTTP clients (to monitor)
  - All modules share `spring-boot-starter-test` (test scope) from root `subprojects` block
  - **No starters beyond these:** there are no `spring-boot-starter-cache`, `-security`, `-batch`, `-quartz`, `-amqp`, `-websocket`, `-mail`, or other optional starters
  - Version managed by BOM: `spring-boot-dependencies:3.5.14` + `spring-cloud-dependencies:2025.0.2`
- [x] Identify starters that are included but not needed
  - **monitor-app**: All 6 starters are actively used — no unneeded starters.
    - `spring-boot-starter-data-jpa` used by 18 JPA entities, 15 repositories
    - `spring-boot-starter-validation` used by 8 controllers with `@Valid` + 15 DTOs with constraint annotations
    - `spring-boot-starter-web` used by 17 REST controllers
    - `spring-boot-starter-actuator` used for `/actuator/health`, `/actuator/info`, `/actuator/prometheus` (configured in `platform-core.yml`)
    - `spring-cloud-starter-openfeign` used by 5 exchange venue adapters
  - **engine-app**: `spring-boot-starter-actuator` — **declared but NOT used**
    - Zero references to `actuator`, `management.*`, health indicators, micrometer, or Prometheus in any Java source or config file
    - No custom `HealthIndicator` or `HealthContributor` beans defined
    - No management endpoint exposure configured in any engine-app YAML profile
    - Engine pushes metrics to monitor via `RestClient` directly (not via Actuator)
    - Warmup HTTP probes use `java.net.http.HttpClient` directly — not Spring Actuator
    - For containerized deployment: `/actuator/health` would be available (auto-configured by default), but it's never referenced — removing `spring-boot-starter-actuator` would only remove the default health/info/metrics endpoints, which are not utilized
  - **telegram-bot-app**: `spring-boot-starter-web` — **arguably not needed as a full starter**
    - Explicitly disables embedded web server via `spring.main.web-application-type: none` in `application.yml`
    - The embedded Tomcat classes are bundled in the fat JAR but never started
    - Only present for Spring MVC annotations (`@RequestMapping` etc.) on Feign client interfaces — `spring-webmvc` alone would suffice
    - Additionally, `jackson-databind` is declared explicitly in `build.gradle` but is redundant — already a transitive dependency of `spring-boot-starter-web` via `spring-boot-starter-json` (though BOM-managed, so version-safe)
  - **Conclusion**: `spring-boot-starter-actuator` in engine-app is the strongest candidate for removal. `spring-boot-starter-web` in telegram-bot-app is a trade-off (convenience vs. footprint) — the fat JAR bloat is minimal, and keeping it avoids a fragile dependency chain for Feign annotation support.
- [x] Check which auto-configurations are active (`spring.autoconfigure.log` or equivalent)
  - **Engine-app**: 168 auto-configuration classes active (lightweight web app)
  - **Monitor-app**: 262 auto-configuration classes active (full JPA + Feign + Cloud infra)
  - **Telegram-bot-app**: 86 auto-configuration classes active (non-web, Feign client only)
  - Full report: `Working/auto-configuration-report.md`
  - Verified via `--debug` flag → `ConditionEvaluationReportLoggingListener` output
- [x] Check which auto-configurations are excluded explicitly
  - **No explicit exclusions in any module** — no `spring.autoconfigure.exclude` in any YAML/properties/annotation across all 3 modules
  - The only `@EnableAutoConfiguration` with options is in test support (`JpaSliceTestConfiguration`)
- [x] Check for unexpected beans from transitive starters
  - **WebSocketServletAutoConfiguration** active in engine-app and monitor-app — auto-configured because Tomcat embedded, but no WebSocket endpoints exist in either module
  - **Cache auto-config** (GenericCacheConfiguration → SimpleCacheConfiguration) active in all 3 modules — cache manager beans created but no `@Cacheable` usage anywhere
  - **GsonAutoConfiguration** active in telegram-bot-app — Gson on classpath via Feign/Spring Cloud transitive dependency, creates Gson bean and converter (no web server so unused)
  - **DiskSpaceHealthContributor + SslHealthContributor** active in engine-app — Actuator on classpath creates health indicators even though Actuator endpoints aren't used by custom code
  - None of these are harmful — beans are light, no external connections, no unexpected side effects
- [x] Review custom `@SpringBootApplication` configurations
  - **MonitorApplication**: 5 explicit scanBasePackages (excludes root package `com.crypto.funding`); `@EnableScheduling` + `@EnableAsync`; `@ConfigurationPropertiesScan`
  - **EngineApplication**: Default scan (package + subpackages); `@Import(EngineModuleConfiguration.class)` for module config; `@EnableScheduling` + `@EnableAsync`
  - **TelegramBotApplication**: Single scan package; `@EnableFeignClients` with basePackages; `@EnableScheduling` only (no `@EnableAsync`)
  - **EngineModuleConfiguration**: `@ConfigurationPropertiesScan`, `@EnableConfigurationProperties(EngineProperties.class)`, `@Import` of 3 beans
  - None use `exclude` or `excludeName` parameters on `@SpringBootApplication`
  - No custom `@AutoConfiguration` classes exist in any module

### Application structure
- [x] Check how `Clock` is created and injected (determinism for testing)
  - **Findings:**
    - **No `@Bean Clock` exists** in any configuration class across all 3 modules — `Clock.systemUTC()` is hardcoded inline in every `@Autowired` public constructor
    - **9 production services** use `Clock` (via `Instant.now(clock)`): 6 in monitor-app (FundingEventLifecycleService, MonitorEnginePlanService, EngineMetricsSnapshotView, LiquidityAssessmentService, FundingObservationMapper, FundingApiCandidateSourceService) + 3 in engine-app (EngineExecutionService, EngineMetricsPublisher, EngineRuntimeControlService)
    - **Consistent dual-constructor pattern** in all 9: `public @Autowired` constructor calls `this(..., Clock.systemUTC())`, package-private constructor accepts `Clock clock` for test injection — excellent for unit-test determinism
    - **`EngineExecutionService`** goes further: 4 overloaded constructors, also injects `LongSupplier nanoTimeSupplier` (testable via `Clock.fixed` + fixed nanos)
    - **Services using bare `Instant.now()` directly (not injectable):** SignalCandidateLifecycleService, SignalCandidateReviewService, AiSignalAdvisorService, ArmedTradeCommandService, FundingEventCommandService, MonitorOverviewService, OperatorCredentialService, EngineLifecycleRecordService, VenueLatencyProbeService, InstrumentRegistryService, VenueProfileService, VenueDiagnosticsService (12 services total)
    - **`System.currentTimeMillis()`** not injectable in: BybitCredentialChecker, KucoinCredentialChecker, GateCredentialChecker, BitgetCredentialChecker, LiveExchangeExecutionPort (acceptable — these are HTTP request timestamps, not trade decision time)
    - **`System.nanoTime()`** not injectable in: VenueLatencyProbeService, InstrumentRegistryService, VenueDiagnosticsService, FundingApiCandidateSourceService (only EngineExecutionService injects it via `LongSupplier`)
    - **Verdict:** The Clock-injected services are well-designed for deterministic testing. The main gap is the 12+ services using bare `Instant.now()`, which would need to be refactored to accept `Clock` if deterministic tests are needed. For non-deterministic integration tests, the current pattern is adequate.
  - Detailed breakdown: `Working/clock-injection-analysis.md`
- [x] Check how HTTP clients are created (Feign config, `@Bean` method)
  - **Feign (telegram-bot-app only):** Single `MonitorApiClient` interface with `@FeignClient(name = "monitor-api", url = "${monitor.base-url}")` — 3 endpoints (candidates, armed-trades, overview). Configured via `MonitorFeignConfig` providing one `RequestInterceptor` bean for `X-Operator-Token` header. Activated via `@EnableFeignClients(basePackages = "com.crypto.funding.telegram.client")`. **No customizations:** no custom `Feign.Builder`, `Decoder`, `Encoder`, `ErrorDecoder`, `Retryer`, or `Logger.Level`. No Feign timeout configuration (uses defaults: 10s connect, 60s read). No circuit breaker (`spring-cloud-starter-circuitbreaker-resilience4j` absent).
  - **Shared `HttpClient` bean (monitor-app):** `VenueHttpClientConfig` creates a `@Bean HttpClient venueHttpClient(VenueHttpProperties)` with properties from `trading.http.*`: `connectTimeoutMs=1000`, `requestTimeoutMs=5000`, `preferHttp2=true`. **Notable:** `requestTimeoutMs` is declared in `VenueHttpProperties` but **never wired to the HttpClient** — `HttpClient.Builder` only supports `connectTimeout()`, not read timeout (must be set per-request). Consumed by 20 adapter classes (5 each for metadata, credential checking, mark price, order book) plus `FundingApiPayloadFetcher`.
  - **Standalone `HttpClient` instances (not shared):**
    - `VenueLatencyProbeService` (monitor-app): `HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()` — hardcoded 5s connect timeout, does not use `VenueHttpProperties`.
    - `EngineExecutionService` (engine-app): `HttpClient.newBuilder().build()` — **no connect timeout** (infinite default). Used for latency probe at line 765.
    - `LiveExchangeExecutionPort` (engine-app): `HttpClient.newHttpClient()` in 3 default constructors, 4th overload accepts injected `HttpClient` for testing. **No connect timeout** on default instances — `REQUEST_TIMEOUT=10s` only applied per-request via `HttpRequest.Builder.timeout()`. Used for all 5 venue live order submissions (Bybit, Gate, OKX, KuCoin, Bitget).
  - **`RestClient` instances (auto-configured `RestClient.Builder`):** 3 total — `EnginePlanClient` (engine-app, to monitor), `EngineControlService` (monitor-app, to engine), `DeepSeekClient` (monitor-app, to DeepSeek AI). **None customize timeouts**, request factories, message converters, or retry. Backed by Spring Boot default `SimpleClientHttpRequestFactory` (`HttpURLConnection` — no connection pooling). `DeepSeekClient` has `.onStatus()` for 4xx/5xx error handling only.
  - **What does NOT exist:** No `RestTemplate`, `WebClient`, Apache `HttpClient`/`CloseableHttpClient`, OkHttp, or any `RestClient.Builder` customization `@Bean` anywhere. No `feign.httpclient.enabled`/`feign.okhttp.enabled` properties. Engine-app has no Feign dependency at all — uses `RestClient` + raw `HttpClient`.
  - **Key findings:**
    1. **Missing connect timeouts in engine-app:** `EngineExecutionService.probeHttpClient` and `LiveExchangeExecutionPort` default instances have no connect timeout — network partitions block execution indefinitely
    2. **`VenueHttpProperties.requestTimeoutMs` is unused:** declared (5000ms default) but never applied to the `HttpClient` bean; each adapter must set per-request timeout individually
    3. **No `RestClient` customization:** all 3 instances use auto-configured defaults backed by `HttpURLConnection` — no connection pooling, no explicit timeouts, minimal error handling
    4. **Feign has no timeout or resilience config:** uses OpenFeign defaults; no retry, no circuit breaker, no custom error decoder
    5. **No shared HTTP client across modules:** monitor-app's shared bean is inaccessible to engine-app (separate Spring context); each module creates its own
  - Full report: `Working/http-client-creation-audit.md`
- [ ] Check how thread pools are created
- [ ] Check if default Spring scheduler thread pool is used
- [ ] Check if default TaskExecutor is configured
- [ ] Check if default TaskScheduler is configured
- [ ] List all `@Scheduled` methods across monitor-app and engine-app
- [ ] List all `@Async` methods
- [ ] Check for virtual threads in Spring (`spring.threads.virtual.enabled`)

### Startup and shutdown
- [ ] Check graceful shutdown configuration (`server.shutdown=graceful`)
- [ ] Check shutdown timeout settings
- [ ] Identify what happens to an executing trade on SIGTERM
- [ ] List all `ApplicationRunner` or `CommandLineRunner` beans
- [ ] Check if startup hooks call external APIs
- [ ] Check if startup can create or modify trading state
- [ ] List all scheduled jobs that start automatically
- [ ] Document how to disable each scheduled job

### Profile-dependent beans
- [ ] Identify beans that depend on active profile
- [ ] Identify beans created only for testnet
- [ ] Identify beans created only for local-safe
- [ ] Identify beans created even when live trading is disabled

### REST API and validation
- [ ] Check for global exception handler (`@ControllerAdvice`)
- [ ] Check for request validation
- [ ] Check if Bean Validation provider is in runtime classpath
- [ ] Verify which REST endpoints lack validation
- [ ] Check for OpenAPI/Swagger documentation
- [ ] Check for API specification generation
- [ ] Check for contract between monitor and engine
- [ ] Check if internal API is versioned
- [ ] Check how monitor-engine version incompatibility is detected
- [ ] Check if build version is exposed via actuator

### Health and readiness
- [ ] Check `readiness` and `liveness` probe configuration
- [ ] Check `/actuator/info` for Git commit info
- [ ] Check health indicator implementations
- [ ] Verify: is health UP when credentials are missing?
- [ ] Verify: is health UP when exchange is unreachable?
- [ ] Verify: is health UP when DB is read-only?
- [ ] Verify: is health UP when clock drift is excessive?
- [ ] Check if a separate trading-readiness indicator exists or is needed
- [ ] Compare health status vs actual readiness to trade

### Application properties
- [ ] Identify dangerous defaults
- [ ] Identify defaults that differ between code and documentation
- [ ] Create list of properties with their safe defaults
- [ ] List all `application.yml` and `application-*.yml` files

## Section 8 — Configuration Inventory

### Profile analysis
- [ ] List all existing profiles (`local-safe`, `testnet`, `staging`, `prod-like`, `prod`)
- [ ] Identify profiles that are documented but may not exist in code
- [ ] Verify `local-safe` profile — exact configuration
- [ ] Verify `testnet` profile — exact configuration
- [ ] Verify `staging` profile — exact configuration
- [ ] Verify `prod-like` profile — exact configuration
- [ ] Document exact differences between profiles
- [ ] Check which parameters are overridden in each profile
- [ ] Check for dangerous parameter inheritance between profiles

### Profile activation
- [ ] Check how active profile is selected (ENV var, JVM arg, config default)
- [ ] Check if profile can be enabled through default environment
- [ ] Verify behavior when no profile is set
- [ ] Check which profile Docker Compose uses
- [ ] Check which profile CI staging uses
- [ ] Check which profile production should use
- [ ] Verify safety of multiple simultaneous profiles
- [ ] Check for dangerous profile combinations

### Property sources
- [ ] List all `@ConfigurationProperties` classes with `@Validated`
- [ ] List all `@Value` property injections
- [ ] List all direct `Environment.getProperty()` calls
- [ ] List all `System.getenv()` calls
- [ ] Check for duplicate property names across files
- [ ] Check for declared properties that are never read
- [ ] Check for read properties that are undocumented
- [ ] Check for property name typos
- [ ] Check for differently-named properties between monitor and engine

### Environment variables
- [ ] List all mandatory environment variables
- [ ] List all optional environment variables with defaults
- [ ] Check which defaults are safe
- [ ] Check which defaults could enable trading
- [ ] Map the relationships between: execution loop, live orders, kill switch
- [ ] Verify: is `loop ON + live orders ON + kill switch OFF + auth OFF` possible?
- [ ] Verify: can live trading run with testnet credentials?
- [ ] Verify: can testnet trading run with production URL?
- [ ] Check for centralized configuration validation at startup
- [ ] Does the app fail to start with incomplete critical config?
- [ ] Or does it start partially functional?
- [ ] Check for configuration schema documentation
- [ ] Check `.env.example` — is it present, complete, current?
- [ ] Check for old environment variables in `.env.example` (e.g., Binance without adapter)
- [ ] Check for centralized configuration reference document

### Runtime configuration
- [ ] Determine settings that should be immutable after startup
- [ ] Determine settings modifiable through API
- [ ] Determine runtime settings that persist after restart
- [ ] Determine runtime settings lost on restart
- [ ] Propose source of truth for runtime configuration

## Section 9 — Secrets and Credentials

### Secret types
- [ ] Identify all secret types used in the project
- [ ] List exchange API keys and their purposes
- [ ] List Telegram credentials
- [ ] List AI provider (DeepSeek) credentials
- [ ] List internal service tokens
- [ ] List operator tokens

### Credential storage and encryption
- [ ] Check how credential master key is stored
- [ ] Check how credential master key is generated
- [ ] Check how credential master key reaches runtime
- [ ] Identify who has access to the master key
- [ ] Check for key versioning
- [ ] Check for key rotation mechanism
- [ ] Verify old credentials can still be decrypted after rotation
- [ ] Review encrypted credential storage (database table, columns)

### Credential transmission
- [ ] Check how credentials flow from monitor to engine
- [ ] Check if credentials are transmitted over HTTP (vs HTTPS)
- [ ] Check TLS between monitor and engine
- [ ] Check if request headers are logged
- [ ] Check if decrypted credentials could appear in exceptions
- [ ] Check if decrypted credentials could appear in actuator `/env`
- [ ] Check if decrypted credentials could appear in heap dumps
- [ ] Check if decrypted credentials could appear in metrics labels
- [ ] Check if credentials are masked in API responses
- [ ] Check if credentials are masked in structured logs

### Secret exposure
- [ ] Check for `.env` files with real values in working tree
- [ ] Check if `.env` is tracked by Git
- [ ] Run `git log --all --diff-filter=A -- '*.env'` — check for committed secrets
- [ ] Search Git history for real credentials (masked review only)
- [ ] Verify no secrets in GitHub Actions logs (review CI runs)
- [ ] Verify no secrets in Docker image layers
- [ ] Check if secrets are passed as Docker build args
- [ ] Check if secrets are passed through Compose environment variables
- [ ] Check for secrets in shell history
- [ ] Check for secrets on self-hosted runner

### Key management
- [ ] Verify testnet and production API keys are separate
- [ ] Verify API keys are IP-restricted
- [ ] Verify withdrawal permission is disabled on keys
- [ ] Verify keys have only necessary trading permissions
- [ ] Check if subaccounts are used
- [ ] Check if separate keys exist for monitor and engine
- [ ] Check if separate keys exist for different environments
- [ ] Document process for revoking a compromised key
- [ ] Create inventory of active keys (masked values)
- [ ] Record last rotation date

### Production secret management
- [ ] Evaluate GitHub Environments secrets as an option
- [ ] Evaluate Docker secrets as an option
- [ ] Evaluate SOPS (`sops`) as an option
- [ ] Evaluate `age` encryption as an option
- [ ] Evaluate Vault as an option
- [ ] Evaluate cloud secret manager as an option
- [ ] Recommend minimal viable solution (not over-engineered for MVP)
- [ ] Define emergency credential revocation process
- [ ] Document who should perform revocation
- [ ] Identify secrets that cannot be confirmed without owner

## Phase output
- [ ] Compile Runtime Process Matrix (artifact, main class, port, profile, dependencies, state owned)
- [ ] Compile Environment Matrix (local-safe, testnet, staging, prod-like, intended production)
- [ ] Compile Secret Inventory (types, names, consumer, storage, rotation, exposure risk — NO actual values)
