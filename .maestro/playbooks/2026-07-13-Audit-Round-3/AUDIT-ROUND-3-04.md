# Audit Round 3 — Phase 4: External Dependencies & Persistence

**Deliverables:** External Dependency Matrix | Port and Network Matrix

## Section 10 — Database and Persistence

### Database identification
- [ ] Identify database used locally (interview SQLite file, JDBC URL)
- [ ] Identify database used in Docker (check Dockerfile/Compose)
- [ ] Identify database used on staging
- [ ] Identify planned production database
- [ ] Is SQLite the official production storage? Yes/No
- [ ] If SQLite is not production-grade, find document stating migration to PostgreSQL
- [ ] Record JDBC URL per environment
- [ ] Find physical location of SQLite file on disk
- [ ] Identify who owns the SQLite volume
- [ ] Check permissions on DB file

### SQLite configuration
- [ ] List all SQLite pragmas set (WAL, busy_timeout, journal_mode, etc.)
- [ ] Check if WAL mode is enabled
- [ ] Check busy timeout value
- [ ] Check how `database is locked` is handled
- [ ] Check connection pool configuration (HikariCP size, timeout)
- [ ] Count connections created
- [ ] Verify if more than one monitor instance can work with one SQLite file
- [ ] Check transaction boundaries
- [ ] Verify atomicity of execution operations
- [ ] Identify operations spanning multiple independent transactions
- [ ] Identify data that could be lost on crash
- [ ] Identify data that is only in-memory

### Schema and migrations
- [ ] List all existing tables
- [ ] List all Flyway migrations (files in `resources/db/migration`)
- [ ] Determine current migration version of local DB
- [ ] Determine how to check schema version before startup
- [ ] Check if Flyway runs automatically at startup
- [ ] Check if Flyway can start in parallel in two instances
- [ ] Check for Java migrations and understand why they're used
- [ ] Check if rollback scripts exist
- [ ] Check if migration tests exist (from V1 to current)
- [ ] Check if migration test exists from production version to current
- [ ] Check for baseline strategy
- [ ] Check for repair strategy
- [ ] Document who can run `flyway repair`

### Schema consistency
- [ ] Check for stale CHECK constraints
- [ ] Check for domain enum values missing from DB constraints
- [ ] Check for DB columns missing from JPA entities
- [ ] Check for domain fields missing from DB columns
- [ ] Verify `ddl-auto=validate` in ALL environments
- [ ] Check if any environment has `ddl-auto` enabled
- [ ] Check for foreign keys
- [ ] Check for indexes
- [ ] Check for uniqueness constraints on idempotency keys
- [ ] Identify timestamps stored in milliseconds vs seconds
- [ ] Verify all timestamps are UTC
- [ ] Check for timezone-dependent columns

### Data lifecycle
- [ ] Check for retention policy
- [ ] Check growth rate of order_attempts and metrics tables
- [ ] Check for cleanup jobs
- [ ] Verify cleanup does not delete audit trail
- [ ] Identify financial journal data that must be immutable
- [ ] Check if append-only records are needed

### Backups
- [ ] Check if DB backup exists
- [ ] Check backup frequency
- [ ] Check if backup restore was ever tested
- [ ] Determine RPO (Recovery Point Objective)
- [ ] Determine RTO (Recovery Time Objective)
- [ ] Check if DB file is copied during writes
- [ ] Verify if that backup strategy is safe for SQLite
- [ ] Check if SQLite Online Backup API should be used
- [ ] Check where backups are stored
- [ ] Check if backups are encrypted
- [ ] Check backup retention period
- [ ] Verify ability to restore pre-trade state
- [ ] Check for trade journal export
- [ ] Check for manual recovery runbook
- [ ] Document pre-migration checklist for production DB

## Section 11 — External Services and Network

### External APIs
- [ ] List all external HTTP APIs the project calls
- [ ] Identify APIs mandatory for startup
- [ ] Identify APIs mandatory only for trading
- [ ] Identify APIs that are optional
- [ ] Identify APIs used only for UI enrichment
- [ ] Identify APIs used only for AI advisor
- [ ] Check behavior when funding source (uainvest.com.ua) is unavailable
- [ ] Check behavior when DeepSeek AI is unavailable
- [ ] Verify AI advisor cannot block the main funding flow
- [ ] Check behavior when exchange public API is unavailable
- [ ] Check behavior when exchange private API is unavailable

### URLs and routing
- [ ] List all production exchange URLs
- [ ] List all testnet exchange URLs
- [ ] Document where URL selection happens (profile, code, config)
- [ ] Verify testnet profile cannot use production URL
- [ ] Verify production profile cannot use sandbox URL
- [ ] Check for allowlist of permitted hosts
- [ ] Check for DNS dependency
- [ ] Check for system proxy support
- [ ] Check for HTTP proxy support
- [ ] Check for SOCKS proxy support
- [ ] Check if VPN is needed for Bybit
- [ ] Check where VPN tunnel should terminate
- [ ] Check what happens when external IP changes
- [ ] Check if exchange keys are IP-restricted

### Network requirements
- [ ] Document required outbound ports
- [ ] Document required inbound ports
- [ ] Determine if public inbound access is needed at all
- [ ] Determine if engine port can be fully closed from outside
- [ ] List hostnames for firewall allowlist
- [ ] Check DNS resolvers used on VPS
- [ ] Check for fallback on DNS failure

### Client configuration
- [ ] Check connect timeout settings
- [ ] Check request timeout settings
- [ ] Check read timeout settings
- [ ] Check for overall execution deadline
- [ ] Check retry configuration per call type
- [ ] Identify which HTTP methods allow retries
- [ ] Verify retry cannot create duplicate orders
- [ ] Check for client-generated idempotency keys
- [ ] Check how HTTP 429 is handled
- [ ] Check if `Retry-After` header is honored
- [ ] Document rate limits per exchange
- [ ] Check for rate-limit budget per endpoint
- [ ] Check if public and private rate limits are separated

### Resilience
- [ ] Check for circuit breaker implementation
- [ ] Check for bulkhead between exchanges
- [ ] Verify: can a slow exchange (Gate) delay another (Bybit)?
- [ ] Verify: can AI provider exhaust HTTP threads?
- [ ] Check for connection pooling
- [ ] Document connections per host
- [ ] Check keep-alive settings
- [ ] Check for HTTP/2 support
- [ ] Check TLS error handling
- [ ] Check hostname verification
- [ ] Check for custom trust store
- [ ] Check for certificate pinning
- [ ] Check for MITM/proxy detection

### Observability of network
- [ ] Check if remote IP is logged
- [ ] Check if DNS/connect/TLS/TTFB latency is measured separately
- [ ] Identify external APIs with SLAs
- [ ] Identify APIs that can change contract without notice
- [ ] Check for contract tests against each exchange
- [ ] Check for recorded fixtures
- [ ] Document how fixtures are updated on API change
- [ ] Identify who tracks exchange endpoint deprecations

## Phase output
- [ ] Compile External Dependency Matrix (service, environment, URL source, auth, timeout, retry, rate limit, failure impact)
- [ ] Compile Port and Network Matrix (component, listen address, port, protocol, external exposure, auth, required callers)
