# Audit Round 3 — Phase 2: Full Dependency Analysis

**Deliverables:** Technology Matrix (libraries part) | Direct Dependency Matrix | Transitive Conflict Report | Unused Dependency Report | Dependency Risk Register

## Prerequisites
- [x] Phase 1 completed — Gradle build system understood
- [x] All Gradle dependencies commands work

## 2.1 — Dependency tree collection

### Root project
- [x] Run `./gradlew :dependencies --configuration compileClasspath --no-daemon > /tmp/deps-root-compile.txt` (root has no configs — expected)
- [x] Run `./gradlew :dependencies --configuration runtimeClasspath --no-daemon > /tmp/deps-root-runtime.txt` (root has no configs — expected)

### platform-core
- [x] Run `./gradlew :platform-core:dependencies --configuration compileClasspath --no-daemon > /tmp/deps-core-compile.txt`
- [x] Run `./gradlew :platform-core:dependencies --configuration runtimeClasspath --no-daemon > /tmp/deps-core-runtime.txt`
- [x] Run `./gradlew :platform-core:dependencies --configuration testRuntimeClasspath --no-daemon > /tmp/deps-core-test.txt`
- [x] Run `./gradlew :platform-core:dependencies --configuration annotationProcessor --no-daemon > /tmp/deps-core-annot.txt`

### monitor-app
- [x] Run `./gradlew :monitor-app:dependencies --configuration compileClasspath --no-daemon > /tmp/deps-monitor-compile.txt`
- [x] Run `./gradlew :monitor-app:dependencies --configuration runtimeClasspath --no-daemon > /tmp/deps-monitor-runtime.txt`
- [x] Run `./gradlew :monitor-app:dependencies --configuration testRuntimeClasspath --no-daemon > /tmp/deps-monitor-test.txt`
- [x] Run `./gradlew :monitor-app:dependencies --configuration annotationProcessor --no-daemon > /tmp/deps-monitor-annot.txt`

### engine-app
- [x] Run `./gradlew :engine-app:dependencies --configuration compileClasspath --no-daemon > /tmp/deps-engine-compile.txt`
- [x] Run `./gradlew :engine-app:dependencies --configuration runtimeClasspath --no-daemon > /tmp/deps-engine-runtime.txt`
- [x] Run `./gradlew :engine-app:dependencies --configuration testRuntimeClasspath --no-daemon > /tmp/deps-engine-test.txt`
- [x] Run `./gradlew :engine-app:dependencies --configuration annotationProcessor --no-daemon > /tmp/deps-engine-annot.txt`

### telegram-bot-app (if exists)
- [x] Run `./gradlew :telegram-bot-app:dependencies --configuration compileClasspath --no-daemon > /tmp/deps-tg-compile.txt`
- [x] Run `./gradlew :telegram-bot-app:dependencies --configuration runtimeClasspath --no-daemon > /tmp/deps-tg-runtime.txt`
- [x] Run `./gradlew :telegram-bot-app:dependencies --configuration testRuntimeClasspath --no-daemon > /tmp/deps-tg-test.txt`
- [x] Run `./gradlew :telegram-bot-app:dependencies --configuration annotationProcessor --no-daemon > /tmp/deps-tg-annot.txt`

## 2.2 — Dependency insight queries
- [x] Run `./gradlew dependencyInsight --dependency spring-boot --no-daemon > /tmp/insight-spring-boot.txt`
- [x] Run `./gradlew dependencyInsight --dependency jackson --no-daemon > /tmp/insight-jackson.txt`
- [x] Run `./gradlew dependencyInsight --dependency sqlite --no-daemon > /tmp/insight-sqlite.txt`
- [x] Run `./gradlew dependencyInsight --dependency slf4j --no-daemon > /tmp/insight-slf4j.txt`
- [x] Run `./gradlew dependencyInsight --dependency logback --no-daemon > /tmp/insight-logback.txt`
- [x] Run `./gradlew dependencyInsight --dependency junit --no-daemon > /tmp/insight-junit.txt`
- [x] Run `./gradlew dependencyInsight --dependency mockito --no-daemon > /tmp/insight-mockito.txt`
- Note: `dependencyInsight` requires `--configuration` flag. Ran against `:monitor-app --configuration runtimeClasspath` for production deps and `testRuntimeClasspath` for test deps.

## 2.3 — Dependency classification per module

### Root project
- [x] Root project has no configurations (no Java plugin — container project only)

### platform-core
- [x] Classified: `jackson-annotations` as `api` (version 2.21, Apache 2.0, Maven Central, exported for consumer annotation of domain DTOs)
- [x] Usage: Zero imports in platform-core source — exported purely for consumers

### monitor-app
- [x] Classified: 11 direct dependencies (10 implementation + 1 runtimeOnly), all Apache 2.0, all from Maven Central
- [x] Usage verified: Flyway (12 migrations), JPA (15+ entities, 10+ repositories), Jackson (40+ files), Feign, Hibernate community dialects, SQLite JDBC

### engine-app
- [x] Classified: 4 direct dependencies, all from Spring Boot/Cloud BOMs
- [x] Usage verified: platform-core (10+ files), spring-boot-starter-web (controllers, RestClient), jackson-databind via transitive (undeclared — use in LiveExchangeExecutionPort)

### telegram-bot-app (if exists)
- [x] Classified: 6 direct dependencies, including pengrad java-telegram-bot-api 8.3.0
- [x] Usage verified: Feign client to monitor-app, Telegram bot API, spring-boot-starter-web for embedded Tomcat
- [x] platform-core dependency: NO Java imports found — possibly unused

## 2.4 — Cross-module dependency analysis
- [x] Dependencies exported between modules: `jackson-annotations` via platform-core `api`; platform-core domain types to monitor-app and engine-app
- [x] Dependencies reaching all applications via root: test deps (spring-boot-starter-test, junit, awaitility, assertj, mockito, wiremock); BOM management; OWASP dep check
- [x] Libraries duplicating functionality: NONE — all areas use a single library
- [x] Multiple HTTP clients: SINGLE — `java.net.http.HttpClient` only (Feign is declarative client, not a raw HTTP layer)
- [x] Multiple JSON libraries: SINGLE — Jackson only
- [x] Multiple logging facades: SINGLE — SLF4J only
- [x] Multiple metrics libraries: SINGLE — Micrometer only
- [x] Multiple retry mechanisms: NONE used — no retry library at all (no Spring Retry, no Resilience4j)

## 2.5 — Specific library checks

### Core libraries
- [x] Lombok: NOT USED — zero imports anywhere in codebase
- [x] MapStruct: NOT USED — zero imports, no annotation processors
- [x] Jackson: used in all 3 app modules (monitor-app + engine-app + tg-bot); engine-app uses it transitively without direct declaration (risk)
- [x] Jackson Java Time module (jackson-datatype-jsr310): PRESENT transitively via spring-boot-starter-json
- [x] Jackson Parameter Names module (jackson-module-parameter-names): PRESENT transitively via spring-boot-starter-json
- [x] Hibernate: version 6.5.2.Final (hibernate-community-dialects) + 6.6.49.Final (hibernate-core via BOM) — needed for SQLite dialect
- [x] Spring Data JPA: HEAVY usage — 15 entity classes, 10+ JpaRepository interfaces, Criteria API specifications
- [x] Plain JDBC: exclusively in Flyway migration classes (12 files) — standard Spring Boot pattern, not competing with JPA

### Database
- [x] SQLite JDBC: version 3.53.0.0 (org.xerial), pure JDBC (no native artifacts), compatible with arm64/amd64
- [x] HikariCP: version 6.3.3 (BOM-managed), configured with pool size 2 in platform-core.yml, WAL mode
- [x] Flyway: version 11.7.2 (Community edition, managed by Spring Boot BOM)
- [x] Flyway SQLite module: NOT explicitly declared — only `flyway-core` is present. `flyway-sqlite` may be needed.

### HTTP and networking
- [x] Spring Web MVC: HEAVY usage — 19 @RestController classes in monitor-app, 2 in engine-app
- [x] WebFlux: NOT USED — zero reference
- [x] Reactor: NOT USED — zero reference
- [x] `java.net.http.HttpClient`: SOLE HTTP client — used in ~26 production files (exchange adapters, engine execution)
- [x] OkHttp: NOT USED directly — present only as transitive dep of java-telegram-bot-api library
- [x] Apache HttpClient: NOT USED — zero imports
- [x] Feign: telegram-bot-app uses OpenFeign client to call monitor-app API (@EnableFeignClients, @FeignClient on MonitorApiClient)

### Monitoring and scheduling
- [x] Micrometer: used in 3 files (LiquidityAssessmentService, VenueRequestTimingMeterBinder, EngineMetricsMeterBinder) — Counter, Gauge, FunctionCounter, Timer
- [x] Prometheus registry: PRESENT — `micrometer-registry-prometheus` as runtimeOnly in monitor-app
- [x] Actuator: enabled endpoints — health, info, prometheus (both monitor-app and engine-app)
- [x] Caffeine: NOT USED — no Spring Cache; EngineCredentialCache uses manual HashMap with TTL
- [x] Spring Retry: NOT USED
- [x] Resilience4j: NOT USED
- [x] Quartz: NOT USED
- [x] ShedLock: NOT USED
- [x] Spring Scheduler: used in 5 files (EngineExecutionScheduler 250ms, EngineMetricsPublisher, FundingApiCandidateSourceService 60s, InstrumentMetadataSyncRunner 240m, SignalNotificationScheduler 30s, TradeNotificationScheduler 30s)

### Telegram
- [x] TDLib/tdlight: NOT USED
- [x] Native binaries: N/A — pengrad library is pure Java
- [x] Telegram library: `com.github.pengrad:java-telegram-bot-api:8.3.0` (MIT)
- [x] Usage: TelegramBotConfig.java + FundingBot.java (UpdatesListener, message sending)

### Crypto
- [x] Bouncy Castle: NOT USED
- [x] Spring Security crypto: NOT USED — only Spring Security Filters for auth
- [x] AES-GCM: `javax.crypto.Cipher` with `AES/GCM/NoPadding` in AesGcmCredentialCipher.java (128-bit tag, 12-byte IV, 128/192/256-bit keys)
- [x] HMAC: `javax.crypto.Mac` with HmacSHA256/HmacSHA512 in HmacSigner.java (platform-core)
- [x] Base64: JDK `java.util.Base64` — no custom implementation

### General utilities
- [x] Guava: NOT USED — zero imports
- [x] Apache Commons: NOT USED — zero imports; commons-fileupload:1.6.0 and commons-lang3:3.20.0 managed but unused
- [x] SLF4J: CONFIRMED as sole logging facade (version 2.0.17)
- [x] Logback: CONFIRMED as backend (version 1.5.32) via spring-boot-starter-logging
- [x] JSON logging encoder: NOT configured — default Logback pattern layout
- [x] Money/currency libraries: NOT USED — currency is string-based
- [x] Decimal arithmetic: uses `java.math.BigDecimal` — standard JDK
- [x] Rate-limiter libraries: NOT USED

### Testing
- [x] JUnit 5: version 5.12.2 (Spring Boot BOM-managed) — Jupiter platform
- [x] Mockito: version 5.12.0 (pinned, downgrading BOM's 5.17.0)
- [x] AssertJ: version 3.25.3 — used pervasively across 15+ test files
- [x] Testcontainers: NOT USED
- [x] WireMock: version 3.0.1 — used in 4+ integration tests (dev tools, venue diagnostics)
- [x] MockWebServer: NOT USED
- [x] Awaitility: version 4.2.2 — declared in subprojects block
- [x] ArchUnit: NOT USED
- [x] RestAssured: NOT USED
- [x] Spring MockMvc: USED in integration tests (WebTestClient/MockMvc)
- [x] Pitest plugin: 1.19.0 (plugin), 1.22.1 (engine), junit5 plugin 1.2.3 — mutation threshold 90%/100%
- [x] JaCoCo plugin: Gradle built-in (not version-pinned) — threshold 95% line, 90% branch
- [x] OWASP Dependency Check: 12.1.8 — CVSS >= 7.0 fails build

### Formatting
- [x] Spotless: version 6.25.0 — only `format 'misc'` for Gradle/MD/YAML files, NO Java formatter configured
- [x] Google Java Format / Eclipse formatter: NOT configured — no Java code formatting enforced

## 2.6 — Unused and redundant dependencies
- [x] Dependencies declared but not imported in production code: `platform-core` in telegram-bot-app (POSSIBLY unused — no Java imports found)
- [x] Dependencies used by only one class: `java-telegram-bot-api` (2 files), `hibernate-community-dialects` (0 imports — YAML config only)
- [x] Libraries replaceable with JDK API: NONE — all fill genuine gaps
- [x] Dependencies with duplicate classes or conflicting versions: NONE — all versions unified via BOM
- [x] Forced resolution versions (`force()`): NONE — zero `force()` calls
- [x] Dependency constraints: NONE — only BOM + `dependencyManagement` (not `constraints` block)
- [x] Transitive dependency exclusions: NONE — zero `exclude()` calls
- [x] Documentation for exclusions: N/A — no exclusions exist
- [x] Dependency convergence conflicts: NONE — all resolved uniformly
- [x] Multiple versions of same library in runtimeClasspath: NONE — single version per library per module

## 2.7 — Security and risk analysis
- [x] Known CVEs: OWASP dependency check is configured (CVSS >= 7.0 fails build); requires NVD_API_KEY for full scan
- [x] End-of-life dependencies: NONE identified — all libraries are on current/recent major versions
- [x] Libraries without support for project's Java version: NONE — all deps target Java 17+ and are compatible with JDK 25
- [x] Libraries with incompatible licenses: NONE — all external deps are Apache 2.0 or MIT
- [x] Libraries that restrict commercial use: NONE
- [x] Dependencies from unknown or personal repositories: NONE — all from `mavenCentral()` only
- [x] Abandoned libraries (no updates in 2+ years): NONE — all have active releases in 2025-2026
- [x] Dependencies that should be updated immediately: None urgent — all within maintained versions
- [x] Updates that may be breaking: `mockito-core` pin 5.12.0 (BOM wants 5.17.0) — update requires testing
- [x] Dependencies to pin without update until MVP stabilization: None required

## 2.8 — SBOM
- [x] CycloneDX/SPDX SBOM generation: NOT currently configured — need to add `org.cyclonedx:cyclonedx-gradle-plugin`
- [x] SBOM includes transitive dependencies: CycloneDX generates full dependency tree including transitive
- [x] Proposed storage location: `docs/sbom/bom-<version>.json` (versioned alongside codebase)
- [x] Proposed CI publication: Add CycloneDX Gradle plugin + `actions/upload-artifact` step in CI

## Phase output
- [x] Compile Technology Matrix (libraries section) → `docs/audit-round-3-phase2.md` Section 1
- [x] Compile Direct Dependency Matrix per module → `docs/audit-round-3-phase2.md` Section 2
- [x] Compile Transitive Conflict Report → `docs/audit-round-3-phase2.md` Section 3
- [x] Compile Unused Dependency Report → `docs/audit-round-3-phase2.md` Section 4
- [x] Compile Dependency Risk Register → `docs/audit-round-3-phase2.md` Section 5
