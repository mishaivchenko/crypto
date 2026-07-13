# Audit Round 3 — Phase 2: Full Dependency Analysis

**Deliverables:** Technology Matrix (libraries part) | Direct Dependency Matrix | Transitive Conflict Report | Unused Dependency Report | Dependency Risk Register

## Prerequisites
- [ ] Phase 1 completed — Gradle build system understood
- [ ] All Gradle dependencies commands work

## 2.1 — Dependency tree collection

### Root project
- [ ] Run `./gradlew :dependencies --configuration compileClasspath --no-daemon > /tmp/deps-root-compile.txt`
- [ ] Run `./gradlew :dependencies --configuration runtimeClasspath --no-daemon > /tmp/deps-root-runtime.txt`

### platform-core
- [ ] Run `./gradlew :platform-core:dependencies --configuration compileClasspath --no-daemon > /tmp/deps-core-compile.txt`
- [ ] Run `./gradlew :platform-core:dependencies --configuration runtimeClasspath --no-daemon > /tmp/deps-core-runtime.txt`
- [ ] Run `./gradlew :platform-core:dependencies --configuration testRuntimeClasspath --no-daemon > /tmp/deps-core-test.txt`
- [ ] Run `./gradlew :platform-core:dependencies --configuration annotationProcessor --no-daemon > /tmp/deps-core-annot.txt`

### monitor-app
- [ ] Run `./gradlew :monitor-app:dependencies --configuration compileClasspath --no-daemon > /tmp/deps-monitor-compile.txt`
- [ ] Run `./gradlew :monitor-app:dependencies --configuration runtimeClasspath --no-daemon > /tmp/deps-monitor-runtime.txt`
- [ ] Run `./gradlew :monitor-app:dependencies --configuration testRuntimeClasspath --no-daemon > /tmp/deps-monitor-test.txt`
- [ ] Run `./gradlew :monitor-app:dependencies --configuration annotationProcessor --no-daemon > /tmp/deps-monitor-annot.txt`

### engine-app
- [ ] Run `./gradlew :engine-app:dependencies --configuration compileClasspath --no-daemon > /tmp/deps-engine-compile.txt`
- [ ] Run `./gradlew :engine-app:dependencies --configuration runtimeClasspath --no-daemon > /tmp/deps-engine-runtime.txt`
- [ ] Run `./gradlew :engine-app:dependencies --configuration testRuntimeClasspath --no-daemon > /tmp/deps-engine-test.txt`
- [ ] Run `./gradlew :engine-app:dependencies --configuration annotationProcessor --no-daemon > /tmp/deps-engine-annot.txt`

### telegram-bot-app (if exists)
- [ ] Run `./gradlew :telegram-bot-app:dependencies --configuration compileClasspath --no-daemon > /tmp/deps-tg-compile.txt`
- [ ] Run `./gradlew :telegram-bot-app:dependencies --configuration runtimeClasspath --no-daemon > /tmp/deps-tg-runtime.txt`
- [ ] Run `./gradlew :telegram-bot-app:dependencies --configuration testRuntimeClasspath --no-daemon > /tmp/deps-tg-test.txt`
- [ ] Run `./gradlew :telegram-bot-app:dependencies --configuration annotationProcessor --no-daemon > /tmp/deps-tg-annot.txt`

## 2.2 — Dependency insight queries
- [ ] Run `./gradlew dependencyInsight --dependency spring-boot --no-daemon > /tmp/insight-spring-boot.txt`
- [ ] Run `./gradlew dependencyInsight --dependency jackson --no-daemon > /tmp/insight-jackson.txt`
- [ ] Run `./gradlew dependencyInsight --dependency sqlite --no-daemon > /tmp/insight-sqlite.txt`
- [ ] Run `./gradlew dependencyInsight --dependency slf4j --no-daemon > /tmp/insight-slf4j.txt`
- [ ] Run `./gradlew dependencyInsight --dependency logback --no-daemon > /tmp/insight-logback.txt`
- [ ] Run `./gradlew dependencyInsight --dependency junit --no-daemon > /tmp/insight-junit.txt`
- [ ] Run `./gradlew dependencyInsight --dependency mockito --no-daemon > /tmp/insight-mockito.txt`

## 2.3 — Dependency classification per module

For each module, classify each dependency into:

### Root project
- [ ] List all `api` dependencies declared in root
- [ ] List all `implementation` dependencies in root
- [ ] List all `runtimeOnly` dependencies in root
- [ ] List all `compileOnly` dependencies in root
- [ ] List all `annotationProcessor` in root
- [ ] List all `testImplementation` in root
- [ ] List all `testRuntimeOnly` in root

### platform-core
- [ ] Repeat all configuration categories for platform-core
- [ ] For each dependency: record version, license, source repository, purpose
- [ ] For each dependency: find real usage locations (imports, class references)

### monitor-app
- [ ] Repeat all configuration categories for monitor-app
- [ ] For each dependency: record version, license, source repository, purpose
- [ ] For each dependency: find real usage locations (imports, class references)

### engine-app
- [ ] Repeat all configuration categories for engine-app
- [ ] For each dependency: record version, license, source repository, purpose
- [ ] For each dependency: find real usage locations (imports, class references)

### telegram-bot-app (if exists)
- [ ] Repeat all configuration categories for telegram-bot-app
- [ ] For each dependency: record version, license, source repository, purpose
- [ ] For each dependency: find real usage locations (imports, class references)

## 2.4 — Cross-module dependency analysis
- [ ] Identify which dependencies are exported between modules
- [ ] Identify dependencies that reach all applications via root configuration
- [ ] Check for libraries duplicating functionality
- [ ] Check for multiple HTTP clients
- [ ] Check for multiple JSON libraries
- [ ] Check for multiple logging facades
- [ ] Check for multiple metrics libraries
- [ ] Check for multiple retry mechanisms

## 2.5 — Specific library checks

### Core libraries
- [ ] Lombok: check usage and if still needed
- [ ] MapStruct: check usage and configuration
- [ ] Jackson: check direct usage, which modules are configured
- [ ] Jackson Java Time module: is it present?
- [ ] Jackson Parameter Names module: is it present?
- [ ] Hibernate: check resolved version and why
- [ ] Spring Data JPA: check usage depth
- [ ] Plain JDBC: check if used alongside JPA

### Database
- [ ] SQLite JDBC driver: version, native artifacts per platform
- [ ] SQLite driver arm64/amd64 compatibility
- [ ] HikariCP: check configuration
- [ ] Flyway: Community vs Teams, version
- [ ] Flyway SQLite module: is it present and configured?

### HTTP and networking
- [ ] Spring Web MVC: verify usage
- [ ] WebFlux: check if used at all
- [ ] Reactor: check if needed
- [ ] `java.net.http.HttpClient`: check usage
- [ ] OkHttp: check usage
- [ ] Apache HttpClient: check usage
- [ ] Feign: verify usage and configuration

### Monitoring and scheduling
- [ ] Micrometer: check registries configured
- [ ] Prometheus registry: is it present?
- [ ] Actuator: which endpoints are enabled
- [ ] Caffeine: check cache usage and data criticality
- [ ] Spring Retry: check usage
- [ ] Resilience4j: check usage
- [ ] Quartz: check if used
- [ ] ShedLock: check if used
- [ ] Spring Scheduler: check usage pattern

### Telegram
- [ ] TDLib or tdlight: check if used
- [ ] Native binaries: for which OS/CPU
- [ ] Telegram library version
- [ ] Official Telegram Bot API client: check usage

### Crypto
- [ ] Bouncy Castle: check if used
- [ ] Spring Security crypto: check usage
- [ ] AES-GCM libraries: identify which
- [ ] HMAC libraries: identify which
- [ ] Base64 implementation: custom or JDK?

### General utilities
- [ ] Guava: check usage depth
- [ ] Apache Commons: identify which modules
- [ ] SLF4J: confirm as logging facade
- [ ] Logback: confirm as backend, check extensions
- [ ] JSON logging encoder: check if configured
- [ ] Money/currency libraries: check if used
- [ ] Decimal arithmetic libraries: check if used
- [ ] Rate-limiter libraries: check if used

### Testing
- [ ] JUnit 5: check version and extensions
- [ ] Mockito: check version
- [ ] AssertJ: check usage
- [ ] Testcontainers: check if used
- [ ] WireMock: check if used
- [ ] MockWebServer: check if used
- [ ] Awaitility: check if used
- [ ] ArchUnit: check if used
- [ ] RestAssured: check if used
- [ ] Spring MockMvc: check usage
- [ ] Pitest plugin: version
- [ ] JaCoCo plugin: version
- [ ] OWASP Dependency Check: version

### Formatting
- [ ] Spotless: which formatters apply to Java
- [ ] Check Google Java Format or Eclipse formatter usage

## 2.6 — Unused and redundant dependencies
- [ ] Identify dependencies declared but not imported in production code
- [ ] Identify dependencies used by only one class
- [ ] Identify libraries replaceable with JDK API
- [ ] Identify dependencies with duplicate classes or conflicting versions
- [ ] Identify forced resolution versions (`force()`)
- [ ] Identify dependency constraints
- [ ] Identify transitive dependency exclusions
- [ ] For each exclusion: document why it was added
- [ ] Check for dependency convergence conflicts
- [ ] Check for multiple versions of same library in runtimeClasspath

## 2.7 — Security and risk analysis
- [ ] Check for known CVEs in resolved dependency tree
- [ ] Check for end-of-life dependencies
- [ ] Check for libraries without support for project's Java version
- [ ] Check for libraries with incompatible licenses
- [ ] Check for libraries that restrict commercial use
- [ ] Check for dependencies from unknown or personal repositories
- [ ] Check for abandoned libraries (no updates in 2+ years)
- [ ] Identify dependencies that should be updated immediately
- [ ] Identify updates that may be breaking
- [ ] Identify dependencies to pin without update until MVP stabilization

## 2.8 — SBOM
- [ ] Verify if CycloneDX or SPDX SBOM can be generated
- [ ] Check if SBOM includes transitive dependencies
- [ ] Propose SBOM storage location
- [ ] Propose SBOM publication as CI artifact

## Phase output
- [ ] Compile Technology Matrix (libraries section)
- [ ] Compile Direct Dependency Matrix per module
- [ ] Compile Transitive Conflict Report
- [ ] Compile Unused Dependency Report
- [ ] Compile Dependency Risk Register
