# Audit Round 3 — Phase 3: Spring Boot, Config & Secrets

**Deliverables:** Runtime Process Matrix | Environment Matrix | Secret Inventory

## Section 6 — Spring Boot and Application Framework

### Version and starters
- [x] Run `./gradlew dependencyInsight --dependency spring-boot --no-daemon` — record exact Spring Boot version
  - **Spring Boot version: 3.5.14** (Spring Cloud 2025.0.2)
  - Confirmed via `build.gradle` line: `springBootVersion = '3.5.14'` and `dependencyInsight` on both modules
  - BOM: `org.springframework.boot:spring-boot-dependencies:3.5.14`
- [ ] Identify all Spring Boot starters in each application module
- [ ] Identify starters that are included but not needed
- [ ] Check which auto-configurations are active (`spring.autoconfigure.log` or equivalent)
- [ ] Check which auto-configurations are excluded explicitly
- [ ] Check for unexpected beans from transitive starters
- [ ] Review custom `@SpringBootApplication` configurations

### Application structure
- [ ] Check how `Clock` is created and injected (determinism for testing)
- [ ] Check how HTTP clients are created (Feign config, `@Bean` method)
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
