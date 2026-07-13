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
- [x] Check how thread pools are created
  - **No custom thread pool executors exist anywhere in the codebase** — no `ThreadPoolExecutor`, `Executors.new*`, `ThreadPoolTaskExecutor`, `SimpleAsyncTaskExecutor`, `AsyncConfigurer`, or `SchedulingConfigurer` in any of the 3 modules
  - **`@EnableAsync` + `@EnableScheduling`** on both monitor-app and engine-app; telegram-bot-app has `@EnableScheduling` only (no `@EnableAsync`)
  - **Default `ThreadPoolTaskScheduler` pool size = 1**: ALL 6 `@Scheduled` methods across all 3 modules share the same single scheduler thread — the 250ms engine execution tick, 60s candidate poll, 240min metadata sync, and both telegram pollers all compete for one thread
  - **7 `@Async` methods** use Spring Boot's default `ThreadPoolTaskExecutor` (core=8, max=unbounded, queue=unbounded) — 1 in engine-app (`EngineCredentialCache.loadOnStartup`), 6 in monitor-app (`SignalLiquidityService.assessAsync`, `AiSignalAdvisorService.analyzeAsync`, `AutoApprovalPipelineService` × 3, `LiquidityAutoAssessService.assessAfterArm`)
  - **6 `@Scheduled` methods**: `EngineExecutionScheduler.runLoop` (250ms), `EngineMetricsPublisher.publishOnSchedule` (15s), `FundingApiCandidateSourceService.scheduledRefresh` (60s), `InstrumentMetadataSyncRunner.scheduledSync` (240min), `SignalNotificationScheduler.pollAndNotify` (30s), `TradeNotificationScheduler.pollAndNotify` (30s)
  - **HikariCP** is the only explicitly sized pool (`maximum-pool-size: 2` in platform-core.yml)
  - **No `spring.task.execution.*` or `spring.task.scheduling.*`** properties configured in any YAML file
  - **No virtual thread configuration** (`spring.threads.virtual.enabled` absent)
  - **No `@Bean` returning `Executor`/`ExecutorService`/`ScheduledExecutorService`** in any configuration class
  - Full report: `Working/thread-pool-creation-audit.md`
  - **Risk**: Single-threaded scheduler could delay the 250ms engine execution tick if any other scheduled method blocks — a potential trading-decision latency issue
  - **Recommendation**: Set `spring.task.scheduling.pool.size` to at least 2–4 to prevent scheduler contention
- [x] Check if default Spring scheduler thread pool is used
  - **Yes, the default is used.** No `spring.task.scheduling.pool.size` property exists in any YAML. No `SchedulingConfigurer` implementation exists. No custom `TaskScheduler` bean.
  - **Default size = 1.** Spring Boot auto-configures `ThreadPoolTaskScheduler` with a single-thread fixed pool. All 6 `@Scheduled` methods across 3 modules share this one thread.
  - **Risk:** The 250ms engine execution tick competes with 5 other scheduled methods. A blocking operation in any `@Scheduled` method stalls the engine loop — a potential trading-decision latency issue.
  - **Recommendation (from prior finding):** Set `spring.task.scheduling.pool.size` to 2–4 to prevent scheduler contention.
- [x] Check if default TaskExecutor is configured
  - **Yes, the default `ThreadPoolTaskExecutor` is used.** No `spring.task.execution.pool.*` properties exist in any YAML. No `AsyncConfigurer` implementation. No custom `Executor`/`TaskExecutor` bean.
  - **Default config:** core=8, max=Integer.MAX_VALUE (unbounded), queue=Integer.MAX_VALUE (unbounded), keepAlive=60s, threadNamePrefix="task-". Created by Spring Boot's `TaskExecutionAutoConfiguration` (present on classpath via `spring-boot-starter`).
  - 7 `@Async` methods use this executor (1 in engine-app, 6 in monitor-app). telegram-bot-app has no `@EnableAsync` — no executor bean created in that module (no `@Async` methods to resolve).
- [x] Check if default TaskScheduler is configured
  - **Yes, the default is used.** Identical finding to "default scheduler thread pool" above. No `ThreadPoolTaskScheduler` customization, no `spring.task.scheduling.*` properties, no `SchedulingConfigurer`.
  - The default `ThreadPoolTaskScheduler` is auto-configured by `TaskSchedulingAutoConfiguration` when `@EnableScheduling` is present (3 modules have it).
- [x] List all `@Scheduled` methods across monitor-app and engine-app
  - **6 total across 3 modules:**
    1. `engine-app/EngineExecutionScheduler.runLoop()` — `fixedDelayString = "${engine.execution-scheduler-tick-ms:250}"` (250ms default)
    2. `engine-app/EngineMetricsPublisher.publishOnSchedule()` — `fixedDelayString = "${engine.metrics-publish.interval-ms:15000}"` (15s default, with initialDelay)
    3. `monitor-app/InstrumentMetadataSyncRunner.scheduledSync()` — `fixedDelayString = "${trading.metadata.sync-interval-minutes:240}m"` (240min default)
    4. `monitor-app/FundingApiCandidateSourceService.scheduledRefresh()` — `fixedDelayString = "${trading.candidate-source.refresh-interval-seconds:60}000"` (60s default)
    5. `telegram-bot-app/TradeNotificationScheduler.pollAndNotify()` — `fixedDelayString = "${telegram.bot.signal-poll-interval-ms:30000}"` (30s default)
    6. `telegram-bot-app/SignalNotificationScheduler.pollAndNotify()` — `fixedDelayString = "${telegram.bot.signal-poll-interval-ms:30000}"` (30s default)
  - All 6 use `fixedDelay` (not `fixedRate` or `cron`). All use configurable delay strings with defaults.
- [x] List all `@Async` methods
  - **7 total across 2 modules:**
    1. `monitor-app/SignalLiquidityService.assessAsync(SignalCandidate)` — asynchronous liquidity assessment
    2. `monitor-app/AiSignalAdvisorService.analyzeAsync(Long)` — DeepSeek AI analysis
    3. `monitor-app/AutoApprovalPipelineService.onCandidateReady(CandidateReadyForAutoApprovalEvent)` — event handler
    4. `monitor-app/AutoApprovalPipelineService.sweepNormalized()` — periodic sweep
    5. `monitor-app/AutoApprovalPipelineService.tryAutoProcess(Long)` — individual auto-processing
    6. `monitor-app/LiquidityAutoAssessService.assessAfterArm(Long, String, String)` — post-arm assessment
    7. `engine-app/EngineCredentialCache.loadOnStartup()` — credential cache warmup
  - telegram-bot-app has no `@Async` methods (no `@EnableAsync` annotation)
- [x] Check for virtual threads in Spring (`spring.threads.virtual.enabled`)
  - **Not configured.** `spring.threads.virtual.enabled` is entirely absent from all YAML/properties files across all modules. No `@Bean` returning virtual thread executor exists.
  - JDK 25 is the build target, which fully supports virtual threads (Project Loom), but the project doesn't opt in.
  - If enabled: would switch `ThreadPoolTaskExecutor` to virtual threads per task (no thread pool overhead) and `ThreadPoolTaskScheduler` to virtual threads. Not recommended for scheduler (fixed pool sizing is safer for latency-critical ticks).

### Startup and shutdown
- [x] Check graceful shutdown configuration (`server.shutdown=graceful`)
  - **Not configured in any module** — all 3 apps use Spring Boot default `server.shutdown=immediate`
  - No `spring.lifecycle.timeout-per-shutdown-phase` property exists anywhere
  - No `@PreDestroy`, `DisposableBean`, `SmartLifecycle`, or `Lifecycle` implementations exist
  - No custom JVM shutdown hooks registered
- [x] Check shutdown timeout settings
  - **No shutdown timeout parameters configured** — even if graceful shutdown were enabled, no beans implement cleanup hooks. The phase timeout default of 30s would have no effect as there's nothing to await.
- [x] Identify what happens to an executing trade on SIGTERM
  - **Critical gap: no protection for in-flight trades during shutdown.** With `immediate` shutdown, the JVM exits promptly, killing all threads mid-operation
  - Engine execution loop (250ms `@Scheduled`): Spring stops scheduling new invocations; any in-flight `runOnce()` call continues (no interruption mechanism)
  - `LiveExchangeExecutionPort` handles `InterruptedException` correctly (restores flag, returns FAILED attempt), but HTTP POST to exchange is not interruptible once sent
  - **Risk: order POST sent but response not received before JVM exits** — no reconciliation mechanism on restart. `submittedAttemptKeys` (in-memory dedup set) is lost, risking duplicate orders
  - 7 `@Async` methods may be mid-execution (no graceful drain of `ThreadPoolTaskExecutor`)
  - Full report: `Working/startup-shutdown-audit.md`
- [x] List all `ApplicationRunner` or `CommandLineRunner` beans
  - **3 `ApplicationRunner` beans** (all in monitor-app):
    1. `OperatorAccountService` — upserts bootstrap operator accounts from env var
    2. `CredentialStorageStartupValidator` — validates master key is present (throws if invariant violated)
    3. `InstrumentMetadataSyncRunner` — syncs venue instrument metadata (conditional: `trading.metadata.sync-on-startup=true`)
  - **0 `CommandLineRunner`** beans anywhere
  - **2 `@EventListener(ApplicationReadyEvent.class)`** methods:
    1. `FundingApiCandidateSourceService.loadOnStartup()` — fetches candidates from external API (blocking)
    2. `EngineCredentialCache.loadOnStartup()` — async loads engine credentials with retry (up to 10×, 10s apart)
  - **1 `@PostConstruct`**: `FundingBot.startPolling()` — starts Telegram bot polling immediately after bean init
- [x] Check if startup hooks call external APIs
  - **4 startup hooks call external APIs:**
    1. `InstrumentMetadataSyncRunner.run()` → venue exchange APIs (conditional)
    2. `FundingApiCandidateSourceService.loadOnStartup()` → `uainvest.com.ua` (blocking)
    3. `EngineCredentialCache.loadOnStartup()` → monitor internal API (async, retries)
    4. `FundingBot.startPolling()` → Telegram API polling
  - **None block application startup** — errors are logged as warnings, startup continues
  - Only `CredentialStorageStartupValidator` can prevent startup, and it checks a local env var (no external call)
- [x] Check if startup can create or modify trading state
  - **None create `FundingEvent`, `ArmedTrade`, or execute actual trades**
  - `OperatorAccountService` upserts operator accounts (auth only, not trading state)
  - `FundingApiCandidateSourceService` ingests `SignalCandidate` records only (requires operator review to become trades)
  - `InstrumentMetadataSyncRunner` updates metadata only
  - Auto-approval pipeline runs on events/API calls, not automatically on startup
- [x] List all scheduled jobs that start automatically
  - **6 total across 3 modules:**
    1. `EngineExecutionScheduler.runLoop()` — 250ms fixedDelay (kill switch guards execution)
    2. `EngineMetricsPublisher.publishOnSchedule()` — 15s fixedDelay (`@ConditionalOnProperty`)
    3. `InstrumentMetadataSyncRunner.scheduledSync()` — 240min fixedDelay
    4. `FundingApiCandidateSourceService.scheduledRefresh()` — 60s fixedDelay (`@ConditionalOnProperty`, matchIfMissing=true)
    5. `TradeNotificationScheduler.pollAndNotify()` — 30s fixedDelay (telegram-bot)
    6. `SignalNotificationScheduler.pollAndNotify()` — 30s fixedDelay (telegram-bot)
  - All use `fixedDelay` (not `fixedRate`/`cron`). All share single scheduler thread (default pool size=1).
- [x] Document how to disable each scheduled job
  - See `Working/startup-shutdown-audit.md` for full table of properties, env vars, and profile defaults
  - **Key disable methods:**
    - Engine loop: `engine.execution-loop-enabled=false` or `ENGINE_EXECUTION_LOOP_ENABLED=false`
    - Metrics publish: `engine.metrics-publish.enabled=false` or `ENGINE_METRICS_PUBLISH_ENABLED=false`
    - Candidate refresh: `trading.candidate-source.enabled=false` or `TRADING_CANDIDATE_SOURCE_ENABLED=false`
    - Telegram: unset `TELEGRAM_BOT_TOKEN` (blank disables conditional bean)
    - Metadata sync: not directly disableable via env var (set interval to 0 or use schedule code guard)

### Profile-dependent beans
- [x] Identify beans that depend on active profile
  - **Zero `@Profile` annotations exist** anywhere in the codebase — no Java class uses `@Profile`.
  - Profile-dependent behavior is driven entirely through property values in per-profile YAML files, NOT through conditional bean registration.
  - **11 `@ConditionalOnProperty` beans** gate subsystems (not profiles):
    - **engine-app** (1): `EngineMetricsPublisher` — gated on `engine.metrics-publish.enabled=true` (disabled in local-safe, testnet; enabled in staging, prod-like)
    - **monitor-app** (6): 5 engine-metrics beans gated on `monitor.engine-metrics.enabled=true` (disabled in local-safe); `FundingApiCandidateSourceService` gated on `trading.candidate-source.enabled=true` (matchIfMissing — enabled by default)
    - **telegram-bot-app** (4): `TelegramBot`, `FundingBot`, `SignalNotificationScheduler`, `TradeNotificationScheduler` — all gated on `telegram.bot.token` being non-empty
  - All other beans across all 3 modules are **unconditional** — they exist in every profile.
  - Full report: `Working/profile-dependent-beans-audit.md`
- [x] Identify beans created only for testnet
  - **No beans are created only for testnet.** The testnet profile overrides property values (execution-loop-enabled=true, live-order-enabled=true) but does not create or destroy any beans.
  - All 12/13 engine-app beans exist regardless of profile — the testnet profile simply removes the runtime guards.
- [x] Identify beans created only for local-safe
  - **No beans are created only for local-safe.** The local-safe profile disables features via property values: loop=false, metrics enabled=false, auth=false, credential storage=false, metadata sync=false, DeepSeek=false.
  - All beans exist but most are behaviorally disabled. `EngineMetricsPublisher` and the 5 monitor engine-metrics beans are not created in local-safe (due to `@ConditionalOnProperty`), but they are feature-gated, not profile-gated.
- [x] Identify beans created even when live trading is disabled
  - **Nearly the entire application context exists** even with live trading disabled.
  - **engine-app**: 12/13 beans are unconditional — `EngineExecutionService`, `EngineExecutionScheduler`, `CredentialAwareExecutionPort`, `EnginePlanClient`, `EngineController`, `EngineCredentialCache`, etc. All exist. The execution loop ticks at 250ms but immediately exits via `EngineRuntimeControlService.isExecutionLoopEnabled()` guard. The `CredentialAwareExecutionPort` returns FAILED for every order when `liveOrderEnabled=false`.
  - **monitor-app**: All 20+ venue adapter classes, all REST controllers, all services (including `AutoApprovalPipelineService`), `VenueHttpClientConfig.venueHttpClient()` bean, persistence layer — all unconditional.
  - **telegram-bot-app**: `MonitorFeignConfig.monitorOperatorTokenInterceptor()` and `MonitorApiClient` Feign interface are unconditional.
  - **Only exception**: `EngineMetricsPublisher` (engine-app) and the 5 engine-metrics beans (monitor-app) — these are not created when metrics publishing is disabled, which happens in local-safe and testnet profiles.

### REST API and validation
- [x] Check for global exception handler (`@ControllerAdvice`)
  - **monitor-app**: `ApiExceptionHandler` with `@RestControllerAdvice` — handles 6 exception types (ResourceNotFound → 404, DomainValidation → 409, IllegalArgumentException/IllegalStateException → 400, MethodArgumentNotValid → 400, NoResourceFound → 404, `Exception` catch-all → 500). Response format: `ApiErrorResponse` record with timestamp/status/message/path.
  - **engine-app**: **NO `@ControllerAdvice` exists** — relies on Spring Boot's default `BasicErrorController`. Inconsistent error format vs monitor-app.
  - **telegram-bot-app**: No web server (`web-application-type: none`) — not applicable.
- [x] Check for request validation
  - **Public endpoints WITH `@Valid` (9 endpoints):** operator credentials upsert, funding event arm, dev engine runtime update, dev test run create, auto-approval rule create/update, armed trade update, candidate approve/reject, venue global mode set.
  - **Public DTOs with Bean Validation (9 DTOs):** OperatorCredentialRequest, ArmFundingEventRequest, UpdateArmedTradeRequest, DevTestRunCreateRequest, AutoApprovalRuleRequest, EngineRuntimeSettingsRequest, SetGlobalVenueModeRequest, ApproveCandidateRequest, RejectCandidateRequest.
  - **Public endpoints WITHOUT `@Valid` (2 DTOs):** `SetVenueDefaultLatencyRequest` (venue default latency) and `DevTestRunPhaseRequest` (dev test run entry/exit) — both DTOs have zero validation annotations.
- [x] Check if Bean Validation provider is in runtime classpath
  - **monitor-app**: ✅ Hibernate Validator available via `spring-boot-starter-validation`
  - **engine-app**: ❌ **Missing** — has `jakarta.validation` API from `spring-boot-starter-web` transitive deps, but no Hibernate Validator implementation. `@Valid` on `@RequestBody` would be silently ignored without the starter.
  - **telegram-bot-app**: ❌ Missing (no HTTP endpoints)
  - **platform-core**: ❌ Missing (intentionally — pure domain library)
- [x] Verify which REST endpoints lack validation
  - **ALL internal endpoints lack `@Valid` across both directions:**
  - **monitor-app internal** (7 endpoints): metrics-snapshot, order-attempts, trade-state, positions, outcomes, latency-samples, warmup-calibration — all receive `@RequestBody` without `@Valid`, all use platform-core contract records with zero Bean Validation annotations.
  - **engine-app internal** (2 endpoints): execution/target, runtime — both lack `@Valid`. Engine-app also lacks spring-boot-starter-validation, so `@Valid` wouldn't work anyway.
  - **engine-app has NO validation capability at all** — needs `spring-boot-starter-validation` added to build.gradle.
- [x] Check for OpenAPI/Swagger documentation
  - **None exists.** No springdoc-openapi, springfox, or swagger dependencies in any build.gradle. No `@Operation`, `@ApiResponse`, `@Schema`, `@Tag` annotations anywhere. No OpenAPI spec files (`.yaml`/`.json`) in the repository.
- [x] Check for API specification generation
  - **Not configured.** No springdoc-openapi, no Swagger Codegen, no OpenAPI Generator, no protobuf, no GraphQL schema.
- [x] Check for contract between monitor and engine
  - **Via `platform-core` shared library.** 20+ contract classes in `platform-core/.../contract/engine/` (records, enums, request/response types). Both `monitor-app` and `engine-app` compile against the same platform-core jar.
  - **No contract testing** (Pact, Spring Cloud Contract) — incompatible changes compile fine but break at runtime.
  - **No validation annotations** on any platform-core contract records — internal API validation is entirely absent.
- [x] Check if internal API is versioned
  - **monitor-app internal:** `/internal/v1/engine/...` — ✅ versioned (v1)
  - **monitor-app public:** `/api/v1/...` and `/api/v2/monitor/...` — ✅ versioned
  - **engine-app internal:** `/internal/engine/...` — ❌ **NOT versioned** (no `/v1/` prefix)
  - `EngineControlService` (monitor-app) calls engine using unversioned paths. Future incompatible changes to engine API cannot be detected at the URL level. The modules must be deployed in lockstep.
- [x] Check how monitor-engine version incompatibility is detected
  - **None exists.** No version headers, no startup compatibility check, no runtime version comparison.
  - Version `"2.0.0"` is hardcoded as **Java string literals** in `MonitorOverviewService.java` and `EngineRuntimeControlService.java` — not sourced from any properties or build artifact.
  - No `Accept-Version`, `X-API-Version`, or version negotiation headers used in HTTP calls.
- [x] Check if build version is exposed via actuator
  - **`/actuator/info` returns empty `{}`** — no `build-info.properties` or `git.properties` generated.
  - Root `build.gradle` sets `version = '2.0.0'` for the artifact, but this value is not consumed at runtime.
  - Neither the `spring-boot` `buildInfo()` task nor `git-commit-id-plugin` is configured.
  - Full report: `Working/rest-api-validation-audit.md`

### Health and readiness
- [x] Check `readiness` and `liveness` probe configuration
  - **Probe endpoints (`management.endpoint.health.probes.enabled`): NOT configured** — no `management.endpoint.health.probes.*` in any YAML across all 3 modules. K8s-ready `/actuator/health/readiness` and `/actuator/health/liveness` endpoints are NOT available.
  - **Kubernetes probes: N/A** — zero K8s manifests (no deployment YAMLs, Helm charts, or Kustomize configs) exist in the repo
  - **Docker HEALTHCHECK: NOT configured** — root `Dockerfile` has no `HEALTHCHECK` instruction; none of the 3 Docker Compose files define `healthcheck:` blocks. `restart: unless-stopped` catches crashes but not application-level unavailability.
  - **Custom HealthIndicator: NONE** — zero `HealthIndicator`/`HealthContributor`/`HealthAggregator` beans across all modules. Only Spring Boot auto-configured indicators active (DiskSpace, SSL). No trading-readiness awareness at all.
  - **Actuator health exposure per module:**
    - monitor-app: ✅ `/actuator/health` exposed via `platform-core.yml` (`health,info,prometheus`)
    - engine-app: ⚠️ `spring-boot-starter-actuator` on classpath with NO explicit `management.endpoints.web.exposure.include` — only `/actuator/health` available by Spring Boot default
    - telegram-bot-app: ❌ No web server, no actuator — health endpoints not applicable
  - **CI/CD smoke test**: Uses basic `curl -sf http://localhost:$svc/actuator/health` (24×5s loop = 120s timeout). Does NOT distinguish readiness from liveness or validate any business-specific state.
  - **Path-mapping**: Not configured — default `/actuator/health` path preserved; no `/healthz`/`/readyz` aliases
  - **Full report**: `Working/readiness-liveness-probe-audit.md`
- [x] Check `/actuator/info` for Git commit info
  - **No `build-info.properties` or `git.properties` exists anywhere** — no `springBoot { buildInfo() }` in any build.gradle, no `gradle-git-properties` plugin, no `management.info.*` config
  - **`/actuator/info` returns empty `{}`** on monitor-app (where exposed via `platform-core.yml`)
  - **engine-app does not expose `/actuator/info`** — no actuator exposure config in engine-app at all (only default `/actuator/health`)
  - Version `"2.0.0"` exists only as Java string literals in `MonitorOverviewService.java` and `EngineRuntimeControlService.java` — not sourced from build metadata
  - No way to determine which Git commit a running instance was built from without external CI tracking
- [x] Check health indicator implementations
  - **Zero custom `HealthIndicator`, `HealthContributor`, `AbstractHealthIndicator`, or `@Endpoint` beans exist** in any module
  - **Auto-configured indicators only** (Spring Boot defaults):
    - `DiskSpaceHealthContributor` — active in engine-app and monitor-app (free disk ≥ 10MB)
    - `SslHealthContributor` — active in both (SSL cert validity)
    - `PingHealthIndicator` — active in both (always returns UP)
    - `DataSourceHealthIndicator` — active in monitor-app only (SQLite connection validation)
  - Full report: `Working/health-indicator-audit.md`
- [x] Verify: is health UP when credentials are missing?
  - **YES — health is UP regardless of credential state.** No health indicator checks credential presence. Exchange credential verification happens on-demand via `VenueDiagnosticsService.checkCredentials()`/`OperatorCredentialService`, not via the health endpoint. `CredentialAwareExecutionPort` returns FAILED for orders without credentials but this is not reflected in health status.
- [x] Verify: is health UP when exchange is unreachable?
  - **YES — health is UP regardless of exchange connectivity.** No health indicator checks exchange API reachability. All 5 venue exchanges can be unreachable while `/actuator/health` reports `UP`.
- [x] Verify: is health UP when DB is read-only?
  - **Partial — depends on SQLite JDBC driver behavior.** `DataSourceHealthIndicator` runs a validation query (`SELECT 1`) against the SQLite connection. If the DB file has read-only permissions preventing connection entirely, health reports DOWN. However, if the driver opens in read-only mode and the validation query succeeds despite writes failing, health would be UP while DB is effectively read-only. No health indicator validates write capability specifically.
- [x] Verify: is health UP when clock drift is excessive?
  - **YES — health is UP regardless of clock drift.** No clock drift detection, NTP sync check, or time-based health indicator exists anywhere. Clock drift of even a few seconds could cause HMAC signature rejection by exchanges, missed entry windows, or confused trade journal ordering — none of which would be reflected in health status.
- [x] Check if a separate trading-readiness indicator exists or is needed
  - **No trading-readiness indicator exists; one is strongly needed.** Current health checks (disk space, SSL, ping, DB connection) provide no signal about actual trading capability. A `TradingReadinessHealthIndicator` should check: engine loop status, live order mode, kill switch state, credential presence, exchange reachability, clock sync. See `Working/health-indicator-audit.md` for full design proposal.
- [x] Compare health status vs actual readiness to trade
  - **The application health endpoint provides effectively no meaningful signal about trading readiness.** In all 6 critical dimensions (loop status, live orders, credentials, exchange connectivity, clock sync, DB writability), health either always reports UP (5/6) or offers only partial driver-dependent coverage (1/6). The only dimensions with adequate coverage are disk space and SSL — neither of which are specific to trading readiness. Full comparison table in `Working/health-indicator-audit.md`.

### Application properties
- [x] Identify dangerous defaults
  - **6 critical/4 high/4 medium/6 low findings identified** — full report at `Working/application-properties-audit.md`
  - **Top 3 most dangerous:** (1) `security.operators.auth-enabled: false` — no auth by default; (2) `trading.bitget/okx/kucoin.mode: production` — inconsistent with bybit/gate's `testnet` defaults, could hit production URLs unexpectedly; (3) `server.shutdown` defaults to `immediate` — in-flight trades lost on SIGTERM
  - Additional concerns: scheduler pool size = 1 (all 6 `@Scheduled` methods share one thread); `TRADING_CANDIDATE_SOURCE_ENABLED` defaults to `true` (matchIfMissing) but `local-safe` does NOT disable it
- [x] Identify defaults that differ between code and documentation
  - **No code-vs-documentation drift detected.** All `.env.example` values that differ from code defaults (auth-enabled, credentials-enabled, profiles-active) are intentionally production-oriented and correctly documented as such
  - Detailed comparison table in `Working/application-properties-audit.md`
- [x] Create list of properties with their safe defaults
  - Full property-safe-defaults table in `Working/application-properties-audit.md` — 40+ properties across 10 categories
  - Key recommendations: set `trading.{bitget,okx,kucoin}.mode: testnet` (consistency), `server.shutdown: graceful` (data safety), `spring.task.scheduling.pool.size: 4` (prevent starvation), `TRADING_CANDIDATE_SOURCE_ENABLED: false` in local-safe profile
- [x] List all `application.yml` and `application-*.yml` files
  - **14 files across 3 modules + 1 shared config:**
    - `config/application.yaml` — optional runtime override
    - `monitor-app/src/main/resources/`: platform-core.yml, application.yml, application-local-safe.yml, application-staging.yml, application-prod-like.yml
    - `engine-app/src/main/resources/`: application.yml, application-local-safe.yml, application-testnet.yml, application-staging.yml, application-prod-like.yml
    - `telegram-bot-app/src/main/resources/`: application.yml, application-local-safe.yml, application-staging.yml
  - See `Working/application-properties-audit.md` for full catalog with each file's purpose

## Section 8 — Configuration Inventory

### Profile analysis
- [x] List all existing profiles (`local-safe`, `testnet`, `staging`, `prod-like`, `prod`)
  - **Profiles that EXIST in code (via `application-{profile}.yml` with `spring.config.activate.on-profile`):**
    - `local-safe` — all 3 modules (monitor, engine, telegram-bot); default via `build.gradle:13` (`localBootProfile = 'local-safe'`)
    - `testnet` — engine-app ONLY; enables loop + live orders for Gate testnet
    - `staging` — all 3 modules; auth + credentials + metrics ON, execution OFF
    - `prod-like` — monitor-app and engine-app ONLY (NOT telegram-bot-app); everything ON except execution (requires explicit ENV)
  - **`prod` does NOT exist** — no `application-prod.yml` anywhere; only `prod-like` is used in production contexts (Docker Compose, `.env.example`)
  - telegram-bot-app has NO `prod-like` profile — runs base defaults in production (token-driven bean activation via `@ConditionalOnProperty`)
  - Full report: `Working/profile-inventory.md`
- [x] Identify profiles that are documented but may not exist in code
  - **Profiles in code (via `application-{profile}.yml`):**
    - `local-safe` — all 3 modules ✅
    - `testnet` — engine-app ONLY ✅
    - `staging` — all 3 modules ✅
    - `prod-like` — monitor-app + engine-app ONLY; telegram-bot-app has NO `prod-like` profile ✅
    - `prod` — does NOT exist anywhere ❌
  - **Profile documentation sources checked:**
    - `CLAUDE.md` profile table (lines 112-119): `local-safe`, `staging`, `prod-like` — **3 profiles listed**
    - `README.md` profile table (lines 119-125): `local-safe`, `staging`, `prod-like` — **3 profiles listed**
    - `docs/03-runtime-config.md` (line 5): "Три явных профиля" — `local-safe`, `staging`, `prod-like` but then "Engine Testnet Profile" subsection documents `testnet`
    - `docs/07-runbook.md`: References `SPRING_PROFILES_ACTIVE=testnet` in usage examples
    - `docs/engine-tdd/requirements/acceptance-boundary.md` (ENG-ACC-005): `local-safe`, `staging`, `prod-like` — omits `testnet`
  - **Discrepancy 1 — `testnet` is under-documented in primary sources:**
    - `testnet` profile exists in code (`engine-app/src/main/resources/application-testnet.yml`), is actively used for Gate testnet execution, and is fully functional
    - However, it is **missing from the CLAUDE.md and README.md profile tables** — both only list 3 profiles (`local-safe`, `staging`, `prod-like`)
    - `docs/03-runtime-config.md` documents `testnet` but only as an aside in the "Engine Testnet Profile" section (lines 193-209), not as a first-class profile alongside the other three
    - A reader relying on CLAUDE.md or README.md alone would not discover the testnet profile exists
    - **Recommendation:** Add a `testnet` row to the CLAUDE.md and README.md profile tables, noting it applies to engine-app only and enables execution loop + live orders
  - **Discrepancy 2 — No profile documentation notes per-module availability:**
    - `testnet` exists only in engine-app (not monitor-app, not telegram-bot-app)
    - `prod-like` exists in monitor-app and engine-app but NOT telegram-bot-app
    - The deploy docker-compose.yml works around this by using `staging` for telegram-bot while `prod-like` for monitor/engine
    - Profile tables don't indicate which modules each profile applies to
  - **Discrepancy 3 — `prod` does not exist (confirmed consistent):**
    - No documentation claims `prod` exists; all documentation consistently uses `prod-like` for production contexts
    - This is intentional and correctly documented
  - **Discrepancy 4 — `testnet` not in acceptance-boundary TDD requirement:**
    - `docs/engine-tdd/requirements/acceptance-boundary.md` (ENG-ACC-005) explicitly scopes safety to `local-safe`, `staging`, and `prod-like`
    - `testnet` is intentionally excluded from the safe-defaults requirement because its purpose is to enable execution — this is correct behavior
  - **No `@Profile` annotations exist anywhere** — profile behavior is entirely property-driven via per-profile YAML files, not conditional bean registration
  - **telegram-bot-app profile YAMLs don't use `spring.config.activate.on-profile`** — unlike monitor-app and engine-app, telegram-bot-app relies on Spring Boot's filename-based profile activation convention only
- [x] Verify `local-safe` profile — exact configuration
  - **Complete verification at:** `Working/local-safe-profile-verification.md`
  - **monitor-app** (7 explicit overrides): auth-enabled=OFF, credentials-storage=OFF, require-master-key=OFF, engine-metrics=OFF, metadata-sync=OFF, metadata-cred-check=OFF, deepseek=OFF
  - **engine-app** (2 explicit overrides): execution-loop=OFF, metrics-publish=OFF
  - **telegram-bot-app** (no `spring.config.activate.on-profile` declared — uses filename convention): token=empty (bot disabled), monitor=http://localhost:8090, DEBUG logging
  - **Key findings:**
    1. `trading.candidate-source.enabled` defaults to `true` (matchIfMissing) — NOT disabled in local-safe, external API (`uainvest.com.ua`) polled every 60s during local dev
    2. Inconsistent venue defaults: bitget/okx/kucoin default to `production`, bybit/gate default to `testnet` — irrelevant without credentials/loop but confusing
    3. telegram-bot-app local-safe YAML lacks `spring.config.activate.on-profile` declaration (works via filename convention, but inconsistent with other modules)
    4. All 13 engine-app beans load unconditionally — execution loop ticks at 250ms but immediately exits via runtime guard
    5. No auth, no credentials, no execution, no live orders, no external API risk (except candidate source polling)
  - **Safety verdict: safe for local development** — all trading-critical features disabled
- [x] Verify `testnet` profile — exact configuration
  - ✅ **10 explicit overrides** identified in `engine-app/src/main/resources/application-testnet.yml`
  - ✅ Loop ON (2000ms interval), Live Orders ON, Kill Switch OFF — all 3 runtime guards removed
  - ✅ Only Gate testnet enabled ($25 max notional), credentials still checked (FAILED if missing)
  - ✅ Monitor has NO testnet profile — monitor behavior depends on its independently-set profile
  - ⚠️ **Documentation gap**: `testnet` is missing from CLAUDE.md and README.md profile tables
  - ⚠️ OKX and Bitget use same URL for testnet/production (mitigated: neither is in `live-enabled-venues`)
  - ⚠️ No double-confirmation mechanism before enabling loop + live orders + removing kill switch
  - Full report: `Working/testnet-profile-verification.md`
- [x] Verify `staging` profile — exact configuration
  - **Full report:** `Working/staging-profile-verification.md`
  - **Safety verdict:** Safe for staging/pre-production use ✅
  - **Key findings:**
    1. Auth ON, credentials storage ON, master key required — all critical protections active
    2. Engine loop OFF, live orders OFF, kill switch ON — no execution risk
    3. engine-app staging and prod-like profiles are byte-for-byte identical
    4. telegram-bot-app staging YAML lacks `spring.config.activate.on-profile` declaration (inconsistent pattern)
    5. telegram-bot-app has no `prod-like` profile — uses `staging` in production deploy
    6. Venue access mode mismatch: monitor uses `production`, engine uses `testnet` (inconsistent but harmless since execution is off)
    7. Candidate source polls external API every 60s (not disabled by staging)
    8. Metadata sync runs on startup (pulls from all 5 exchanges)
- [x] Verify `prod-like` profile — exact configuration
  - **Full report:** `Working/prod-like-profile-verification.md`
  - **monitor-app (5 overrides):** auth=ON, credentials-storage=ON, require-master-key=ON, engine-metrics=ON, metadata-require-credentials=ON
  - **engine-app (2 overrides):** execution-loop=OFF, metrics-publish=ON
  - **telegram-bot-app:** No prod-like profile exists — uses `staging` in Docker Compose
  - **engine-app staging vs prod-like:** Byte-for-byte identical — only env vars differentiate them
  - **monitor-app staging vs prod-like:** Only 1 difference — `metadata.require-credentials-on-startup: true` (prod-like) vs `false` (staging)
  - **Safety verdict: safe** — loop OFF, live orders OFF, kill switch ON, auth ON, master key required
  - **Notable:** `trading.candidate-source.enabled` inherits default `true` (matchIfMissing) — external API polled every 60s
  - **Finding:** Engine-app default venue access mode is `testnet`, monitor-app default is `production` — inconsistent but harmless (loop is OFF)
- [x] Document exact differences between profiles
  - **Full report:** `Working/profile-differences-comparison.md`
  - **Key finding 1:** engine-app staging and prod-like profiles are **byte-for-byte identical** — the only behavioral distinction comes from environment variables, not YAML overrides
  - **Key finding 2:** Only `testnet` profile reverses the 3 critical execution guards (loop ON, live ON, kill switch OFF) — all other profiles keep them safe
  - **Key finding 3:** telegram-bot-app lags behind in profile structure — lacks `spring.config.activate.on-profile` declarations and has no `prod-like` profile
  - **Key finding 4:** The candidate source external API poll is enabled by default in **all** profiles — no profile explicitly disables it
  - **Key finding 5:** The single difference between monitor-app staging and prod-like is `trading.metadata.require-credentials-on-startup` (`false` → `true`)
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
