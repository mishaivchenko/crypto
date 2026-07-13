# Audit Round 3 — Phase 6: Final Synthesis

**Deliverables:** Findings by Severity (P0–P3) | Owner Decision Register | Final Readiness Verdict | Installation Guide

**Prerequisites:** All phases 1–5 completed with their deliverables

## Section 16 — Deployment Process

### Environment inventory
- [ ] List all deployment environments that actually exist
- [ ] List environments that exist only on paper
- [ ] Document where local development runs
- [ ] Document where staging runs
- [ ] Document where testnet should run
- [ ] Document where production should run
- [ ] Confirm if Mac mini is the official staging host
- [ ] Assess if Mac mini is acceptable for automated execution
- [ ] Check if Mac mini can sleep (and if sleep is disabled)
- [ ] Check auto-start behavior after reboot
- [ ] Check `launchd` (macOS) or `systemd` (Linux) service definitions
- [ ] Check Docker restart policies

### Deployment execution
- [ ] Identify who performs deployments
- [ ] Check if deployment runs from GitHub Actions
- [ ] Check if deployment uses SSH
- [ ] Check if deployment runs directly on self-hosted runner
- [ ] Locate checkout path
- [ ] Locate Compose project directory
- [ ] Locate `.env` file location
- [ ] Document who has access to the host
- [ ] Check for dedicated system user
- [ ] Check for `sudo` requirement
- [ ] List directories that must exist
- [ ] List required permissions
- [ ] List required open ports
- [ ] Check for firewall
- [ ] Check for reverse proxy
- [ ] Check for TLS termination

### Access and connectivity
- [ ] Determine if public domain is needed
- [ ] Determine if UI must be accessible via internet
- [ ] Determine how operator connects
- [ ] Evaluate Tailscale as access method
- [ ] Evaluate WireGuard as access method
- [ ] Determine if VPN-only access is needed
- [ ] Verify engine is accessible only via localhost/internal network
- [ ] Document how monitor calls engine
- [ ] Document how engine calls monitor
- [ ] Check for service discovery
- [ ] Assess need for service discovery with single-host MVP

### Deployment units
- [ ] Define deployment unit
- [ ] Determine if all services deploy together
- [ ] Determine if only monitor can be updated
- [ ] Determine if only engine can be updated
- [ ] Document how API compatibility is ensured
- [ ] Check pre-deploy backup procedure
- [ ] Check migration compatibility verification
- [ ] Check how execution is disabled before deploy

### Safety during deployment
- [ ] Check if open positions must be closed before deploy
- [ ] Determine how to detect open positions
- [ ] Check scheduler shutdown mechanism
- [ ] Check how current requests are awaited
- [ ] Document safe deploy without accidental execution
- [ ] Check post-deploy health check
- [ ] Check trading-readiness verification
- [ ] Check exchange connectivity verification
- [ ] Check credential verification without order creation
- [ ] Document application rollback procedure
- [ ] Document database rollback procedure
- [ ] Assess what happens if application rollback is impossible due to migration

### Deployment constraints
- [ ] Determine acceptable downtime
- [ ] Determine acceptable deployment window
- [ ] Check if deploy is allowed near funding events
- [ ] Check if deployment freeze is needed before funding window
- [ ] Check for maintenance mode
- [ ] Check for failed-deploy runbook
- [ ] Check for production deploy job
- [ ] Document why current production job is a stub
- [ ] List prerequisites missing for production deployment

### Infrastructure requirements
- [ ] Determine if VPS is needed now
- [ ] Propose region for execution VPS
- [ ] Determine if Singapore region is needed
- [ ] List exchanges available from chosen region
- [ ] Estimate network latency to Gate from chosen region
- [ ] Estimate network latency to Bybit from chosen region
- [ ] Assess need for separate execution hosts per region
- [ ] Assess if this is premature for MVP
- [ ] Define minimum viable production topology

## Section 17 — Infrastructure Provisioning

- [ ] Document how the current host was created (manual or automated)
- [ ] Check for Terraform configuration
- [ ] Check for Ansible playbooks
- [ ] Check for cloud-init configuration
- [ ] Check for shell provisioning script
- [ ] Document OS and version used
- [ ] Propose preferred Linux distribution
- [ ] List required OS packages
- [ ] Document Docker installation method
- [ ] Document Docker Compose installation method
- [ ] Document application user creation
- [ ] Document directory creation
- [ ] Document permission setup
- [ ] Document firewall configuration
- [ ] Document timezone setup
- [ ] Document NTP configuration
- [ ] Document clock synchronization check
- [ ] Document monitoring agent installation
- [ ] Document log rotation setup
- [ ] Document backup job setup
- [ ] Document Tailscale/VPN installation
- [ ] Document automatic security updates
- [ ] Assess if automatic reboot is safe with open positions
- [ ] Check for host hardening checklist
- [ ] Check if SSH password login is disabled
- [ ] Check SSH key usage
- [ ] Check if root login is disabled
- [ ] Check for fail2ban
- [ ] Assess if fail2ban is needed with VPN-only access
- [ ] Check for disk encryption
- [ ] Check for encrypted volume for secrets and DB
- [ ] Check disk usage tracking
- [ ] Check inode usage tracking
- [ ] Check memory pressure tracking
- [ ] Check CPU steal tracking
- [ ] Check network packet loss tracking
- [ ] Check host reboot tracking
- [ ] Document host recovery from scratch
- [ ] Can the host be reproduced in one hour?
- [ ] List steps that remain manual
- [ ] List manual steps that cannot be verified
- [ ] Assess need for IaC before testnet
- [ ] Assess need for IaC before live
- [ ] Define minimum IaC scope for MVP

## Section 18 — Time Synchronization

- [ ] Identify which system clock is used
- [ ] Verify UTC is used
- [ ] Check NTP synchronization on host
- [ ] Identify NTP daemon
- [ ] List current NTP sources
- [ ] Check if clock offset is measured
- [ ] Check if alert exists for clock drift
- [ ] Define maximum acceptable drift
- [ ] Document behavior when drift is exceeded
- [ ] Assess if execution should auto-block on drift
- [ ] Check if exchange server-time offset is measured
- [ ] List which exchanges have server-time endpoints
- [ ] Determine how often offset should be measured
- [ ] Locate where offset is stored
- [ ] Document how offset is used in order submit time
- [ ] Document how sudden NTP correction is handled
- [ ] Check if monotonic clock is used for duration
- [ ] Check if wall clock is used for scheduling
- [ ] Check for JVM pause monitoring
- [ ] Check for GC pause monitoring
- [ ] Check if scheduler lag is measured
- [ ] Check if difference between target and actual submit time is measured
- [ ] Check if network request duration is measured
- [ ] Check if exchange acknowledgement delay is measured
- [ ] Check if fill delay is measured
- [ ] Check for per-venue latency history
- [ ] Check for per-host latency history
- [ ] Check if sleep/hibernation can go undetected
- [ ] Document behavior after resume
- [ ] Assess if service should stop execution after detected resume
- [ ] Check for CPU frequency/power-saving impact
- [ ] Assess need for dedicated host performance mode
- [ ] Define timing precision required for MVP
- [ ] Define timing precision current deployment can provide

## Section 19 — Observability Stack

### Components
- [ ] List all observability components that exist
- [ ] List all observability components actually running
- [ ] Verify Spring Boot Actuator usage
- [ ] Verify Micrometer usage
- [ ] Verify Prometheus is running (check Compose)
- [ ] Verify Grafana is running (check Compose)
- [ ] Check for log aggregation (Loki, Elasticsearch)
- [ ] Check for tracing (OpenTelemetry)
- [ ] Check for correlation ID across requests
- [ ] Verify trade ID is in all relevant logs
- [ ] Verify order attempt ID is in all relevant logs
- [ ] Verify client order ID is in all relevant logs
- [ ] Verify venue is in metrics and logs
- [ ] Verify symbol is in metrics (and check cardinality)
- [ ] Verify no secrets leak into metric labels

### Actuator endpoints
- [ ] List enabled actuator endpoints
- [ ] List endpoints accessible externally
- [ ] Check if metrics endpoints are secured
- [ ] Check if Prometheus can read internal data without auth

### Custom metrics
- [ ] List all custom Micrometer metrics
- [ ] Check metrics for signal ingestion
- [ ] Check metrics for scheduler
- [ ] Check metrics for order submission
- [ ] Check metrics for fills
- [ ] Check metrics for positions
- [ ] Check metrics for exits
- [ ] Check metrics for PnL
- [ ] Check metrics for reconciliation
- [ ] Check metrics for clock drift
- [ ] Check metrics for scheduler lag
- [ ] Check metrics for external API latency
- [ ] Check metrics for HTTP 429
- [ ] Check metrics for rejected orders
- [ ] Check metrics for missing credentials
- [ ] Check metrics for stale metadata
- [ ] Check metrics for kill switch
- [ ] Check for open positions metric
- [ ] Check for unknown exchange positions metric
- [ ] Check for recovery-required state metric

### Dashboards and alerting
- [ ] Check for Grafana dashboards
- [ ] Verify which dashboards are actually imported
- [ ] Check if dashboard JSON has fixed datasource IDs (portable?)
- [ ] Check if Prometheus datasource is auto-provisioned
- [ ] Check Grafana provisioning configuration
- [ ] Check for default admin password
- [ ] Locate Grafana password storage
- [ ] Check if Grafana is exposed externally
- [ ] Check Prometheus data retention
- [ ] Locate Prometheus volume
- [ ] Check for disk-full behavior
- [ ] Check for alerting rules
- [ ] Check for Alertmanager
- [ ] Identify alert destination
- [ ] Check if Telegram is used for operational alerts
- [ ] Check if informational and critical alerts are separated
- [ ] Assess alert fatigue risk
- [ ] List mandatory P0 alerts before live
- [ ] Check for alert on open position without heartbeat
- [ ] Check for alert on exit rejection
- [ ] Check for alert on reconciliation mismatch
- [ ] Check for alert on app restart with open position
- [ ] Check for alert on DB migration failure
- [ ] Check for alert on clock drift
- [ ] Check for alert on prolonged scheduler lag
- [ ] Check for alert on invalid credentials
- [ ] Check for alert on repeated 429
- [ ] Check for alert on host disk usage
- [ ] Check for alert on host reboot
- [ ] Check for alert on container crash loop
- [ ] Collect alert thresholds requiring owner decision
- [ ] Check for observability runbook

## Section 20 — Logging and Audit Trail

- [ ] Identify logging framework
- [ ] Identify default log level
- [ ] Identify intended production log level
- [ ] Check for per-package log levels
- [ ] Check for structured JSON logging
- [ ] Check if logs are plain text
- [ ] Check for UTC timestamps in logs
- [ ] Check for application name in log pattern
- [ ] Check for application version in logs
- [ ] Check for hostname/container ID in logs
- [ ] Check for correlation ID in logs
- [ ] Check for trade ID in logs
- [ ] Check for funding event ID in logs
- [ ] Check for attempt ID in logs
- [ ] Check for venue in logs
- [ ] Check for symbol in logs
- [ ] Check if HTTP request bodies are logged
- [ ] Check if HTTP response bodies are logged
- [ ] Verify these do not contain credentials, API signatures, or operator tokens
- [ ] Check if exchange error responses are fully logged
- [ ] Check for centralized log sanitizer
- [ ] Check for log sampling
- [ ] Assess if high-frequency scheduler could fill disk
- [ ] Check for log rotation
- [ ] Identify who is responsible for rotation
- [ ] Determine log retention period
- [ ] Locate physical log storage
- [ ] Verify logs survive container recreation
- [ ] Assess if logs are sufficient for trade investigation
- [ ] Verify full timeline can be reconstructed: signal → approval → arm → scheduler decision → submit → exchange response → fill → position → exit → outcome
- [ ] Check for immutable trade journal
- [ ] Document how trade journal differs from application logs
- [ ] Check if journal can be deleted via UI/API
- [ ] Assess need for modification protection
- [ ] Check if journal can be exported to JSON/CSV
- [ ] Check audit events for: configuration changes
- [ ] Check audit for: live orders enabled
- [ ] Check audit for: kill switch changes
- [ ] Check audit for: credential changes
- [ ] Check audit for: manual run-once
- [ ] Check audit for: emergency close
- [ ] Identify P0 audit gaps

## Section 21 — Security Architecture

### Authentication
- [ ] Check if Spring Security is used
- [ ] If not, identify how the operator API is secured
- [ ] Review X-Operator-Token implementation
- [ ] Check how operator tokens are hashed
- [ ] Check if salt is used
- [ ] Check for token expiration
- [ ] Check for token rotation
- [ ] Check for roles
- [ ] Check for authorization per operation

### Authorization gaps
- [ ] Can any operator enable live trading?
- [ ] Can any operator modify credentials?
- [ ] Can any operator trigger emergency close?
- [ ] Check internal monitor API protection
- [ ] Check engine API protection
- [ ] Document why engine API may be accessible without auth
- [ ] List endpoints that can send orders
- [ ] List endpoints that change runtime flags
- [ ] List endpoints that return decrypted credentials

### Network security
- [ ] Verify network policy restrictions on sensitive endpoints
- [ ] Check for TLS
- [ ] Check for mTLS
- [ ] Assess need for mTLS in single-host MVP
- [ ] Check for CORS policy
- [ ] Check for CSRF protection
- [ ] Check if browser cookie auth is used
- [ ] Check if UI sends a static token
- [ ] Determine where UI stores the token
- [ ] Can token be stolen via XSS?
- [ ] Check for Content Security Policy
- [ ] Check for input validation
- [ ] Check for SQL injection risk
- [ ] Check for SSRF risk via configurable URLs
- [ ] Check for path traversal risk
- [ ] Check for unsafe deserialization
- [ ] Check for command execution from application

### Security scanning
- [ ] Check for public actuator endpoints
- [ ] Check for dependency CVE scan
- [ ] Check for container scan
- [ ] Check for secret scan
- [ ] Check for SAST
- [ ] Check for DAST
- [ ] Check for rate limiting on operator API
- [ ] Check for rate limiting on engine API
- [ ] Check for rate limiting on credential endpoints
- [ ] Check for brute-force protection
- [ ] Check for failed authentication audit
- [ ] Check for IP allowlist
- [ ] Assess need for VPN-only access

### Security priorities
- [ ] List mandatory security controls before testnet
- [ ] List mandatory security controls before live
- [ ] List controls that can be deferred (no financial risk)

## Section 22 — Testing Infrastructure

### Test types
- [ ] List test source sets
- [ ] Check for unit tests
- [ ] Check for integration tests
- [ ] Check for architecture tests
- [ ] Check for contract tests
- [ ] Check for migration tests
- [ ] Check for UI tests
- [ ] Check for end-to-end tests
- [ ] Check for exchange sandbox tests
- [ ] Check for testnet smoke tests

### Test environment
- [ ] Identify tests that run without network
- [ ] Identify tests that require network
- [ ] Identify tests that require credentials
- [ ] Identify tests with potential financial side effects
- [ ] Check for WireMock usage
- [ ] List mocked APIs
- [ ] List unmocked APIs
- [ ] Check for deterministic test clock
- [ ] Check for scheduler timing tests
- [ ] Check for restart recovery tests
- [ ] Check for reconciliation tests
- [ ] Check for duplicate order prevention tests
- [ ] Check for crash-between-submit-and-persistence tests
- [ ] Check for timeout-after-exchange-accepted tests
- [ ] Check for partial fill tests
- [ ] Check for exit rejection tests
- [ ] Check for stale credential tests
- [ ] Check for wrong environment URL tests
- [ ] Check for kill switch tests
- [ ] Check for dangerous profile combination tests
- [ ] Check for configuration fail-fast tests

### Database testing
- [ ] Check if SQLite in-memory is used for tests
- [ ] Verify how it differs from file-based SQLite
- [ ] Check for DB locking scenario tests
- [ ] Check if Flyway migrations are tested against real SQLite file

### Coverage analysis
- [ ] Run `./gradlew test --no-daemon` — record test results
- [ ] Run `./gradlew engineTddDocsCheck --no-daemon` — record result
- [ ] Run `./gradlew :engine-app:engineTddCoverageVerification --no-daemon` — record result
- [ ] Identify classes covered by JaCoCo gate
- [ ] Identify production classes excluded from coverage
- [ ] Document why each exclusion exists
- [ ] Check actual Pitest coverage
- [ ] Record Pitest execution time
- [ ] Check for excluded mutants and document why
- [ ] Check for flaky tests
- [ ] Check for `sleep()` in tests
- [ ] Check for order-dependent tests
- [ ] Check for tests dependent on local timezone
- [ ] Check for tests dependent on system clock
- [ ] Check for tests dependent on CPU speed
- [ ] Check for tests dependent on filesystem paths
- [ ] Verify tests work on both Linux and macOS

### Testing standards
- [ ] Define canonical test command for PR
- [ ] Define canonical full verification command
- [ ] Identify test gaps blocking DevOps changes
- [ ] Identify test gaps blocking production deploy

## Section 23 — Dependency and Supply-Chain Security

- [ ] Check OWASP Dependency Check plugin version
- [ ] Check which feeds are used (NVD, etc.)
- [ ] Check if NVD API key is needed
- [ ] Find where NVD key is stored
- [ ] Document behavior when NVD is unavailable
- [ ] Assess if stale vulnerability DB can give false negatives
- [ ] Check CVSS thresholds that fail the build
- [ ] Check existing suppressions and why each exists
- [ ] Check for suppression expiration dates
- [ ] Run `./gradlew security --no-daemon` — record if safe
- [ ] Check for GitHub Dependabot
- [ ] Check for Renovate
- [ ] Document who reviews dependency updates
- [ ] Check for automatic merge
- [ ] Assess if automatic merge is acceptable for trading system
- [ ] Check for dependency verification
- [ ] Check for lockfiles
- [ ] Check for SBOM
- [ ] Check for container vulnerability scan
- [ ] Check if OS packages are scanned
- [ ] Check if Gradle dependencies are scanned
- [ ] Check if GitHub Actions are scanned
- [ ] Check if secrets are scanned
- [ ] Check if Git history is scanned
- [ ] Check if Docker build context is scanned
- [ ] Check for artifact signing
- [ ] Check if third-party actions are pinned by SHA
- [ ] Check if Docker base images are pinned by digest
- [ ] Check for Maven repository allowlist
- [ ] Check if build can download from `mavenLocal()`
- [ ] Assess if compromised local artifact could enter image
- [ ] Assess need for repository content filtering
- [ ] Define supply-chain controls mandatory for MVP
- [ ] Define controls for later implementation

## Section 24 — Developer Workstation Setup

### Required tools
- [ ] Determine required JDK version
- [ ] Check if separate Gradle installation is needed
- [ ] Check if Docker Desktop is needed
- [ ] Check if Docker Compose plugin is needed
- [ ] Check if Node.js is needed
- [ ] Check if npm/yarn/pnpm is needed
- [ ] Check if Python is needed
- [ ] Check if SQLite CLI is needed
- [ ] Check for required system tools (`curl`, `jq`, `openssl`, `make`)
- [ ] Check if GitHub CLI is needed
- [ ] Check if Tailscale is needed
- [ ] Check if native Telegram libraries are needed
- [ ] Check if certificates need to be installed

### Local runtime
- [ ] List required environment variables for safe local run
- [ ] Verify project can run with zero credentials
- [ ] Verify UI can run without engine
- [ ] Verify engine can run in fully dry-run mode
- [ ] Document local DB creation
- [ ] Document migration application
- [ ] Check for seed data
- [ ] Check for demo operator
- [ ] Document operator token creation
- [ ] Document health check
- [ ] Document how to open UI
- [ ] Document how to stop processes
- [ ] Document how to clean local data
- [ ] Document how to restore local DB

### Developer workflow
- [ ] Document test execution
- [ ] Document full audit execution
- [ ] Document dependency report generation
- [ ] Document security scan execution
- [ ] Document Docker image build
- [ ] Document Compose startup
- [ ] Identify undocumented steps
- [ ] Identify steps that don't work on a clean machine
- [ ] Identify steps that depend on the project author

### Bootstrap improvements
- [ ] Assess need for one-command bootstrap
- [ ] Assess need for Makefile
- [ ] Assess need for `justfile`
- [ ] Assess need for devcontainer
- [ ] Assess need for Nix/Devbox
- [ ] Propose minimum viable solution
- [ ] Define officially supported setup

## Section 25 — Staging Environment

### Current state
- [ ] Define what staging currently means
- [ ] Identify where it's physically running
- [ ] Determine which versions are installed
- [ ] Determine which commit is currently deployed
- [ ] Identify current image tag
- [ ] Check which Spring profile is active
- [ ] Check which runtime flags are active
- [ ] Check if execution loop is enabled
- [ ] Check if live orders are enabled
- [ ] Check kill switch state
- [ ] List credentials present (testnet or production)
- [ ] List available exchanges
- [ ] Check if VPN is needed
- [ ] Check DNS functionality
- [ ] Verify clock synchronization
- [ ] Check DB schema version
- [ ] Locate DB file
- [ ] Check if backup exists
- [ ] Check if Prometheus is running
- [ ] Check if Grafana is running
- [ ] Check if alerts are configured
- [ ] Check who receives alerts
- [ ] Check access control
- [ ] Check how operator connects
- [ ] Check if staging is accidentally publicly accessible
- [ ] Verify staging cannot make production orders
- [ ] List safeguards preventing this

### Staging validation
- [ ] Check if restart test was ever performed
- [ ] Check if recovery test was ever performed
- [ ] Check if controlled Gate end-to-end test was performed
- [ ] Record evidence and timestamps
- [ ] Document staging update procedure
- [ ] Document staging rollback procedure
- [ ] Document staging cleanup procedure
- [ ] Document how staging differs from production
- [ ] List differences that make staging non-representative
- [ ] Assess Mac mini as staging execution host
- [ ] List checks needed for this decision

## Section 26 — Production Environment Requirements

- [ ] Define what production means for MVP
- [ ] Determine if separate production environment is needed before first micro-trade
- [ ] Propose hosting platform (VPS or cloud managed)
- [ ] Propose provider
- [ ] Propose region
- [ ] Document reasoning for region
- [ ] Check exchange restrictions in that region
- [ ] Estimate latency to Gate
- [ ] Estimate latency to Bybit
- [ ] Estimate latency to BingX
- [ ] Define minimum CPU
- [ ] Define minimum RAM
- [ ] Define minimum disk
- [ ] Assess need for NVMe
- [ ] Assess need for dedicated CPU
- [ ] Assess need for static public IP
- [ ] Assess need for IPv6
- [ ] Assess need for DDoS protection
- [ ] Assess need for reverse proxy
- [ ] Assess need for domain name
- [ ] Assess need for public TLS certificate
- [ ] Determine if access should be only via VPN
- [ ] Propose OS
- [ ] Document update management
- [ ] Document unattended reboot prevention
- [ ] Document how restart aligns with trading windows
- [ ] Assess need for UPS
- [ ] Assess need for second host
- [ ] Assess need for failover
- [ ] Evaluate failover risk regarding duplicate execution
- [ ] Can active-active engine exist?
- [ ] Should more than one active engine be prohibited?
- [ ] How to implement leader ownership?
- [ ] Is single-node production sufficient for MVP?
- [ ] Document accepted single-node risks
- [ ] Define RPO
- [ ] Define RTO
- [ ] Document backup procedures
- [ ] Document disaster recovery
- [ ] Document emergency access
- [ ] Document production access list
- [ ] Document production change recording
- [ ] Assess need for manual approval for each live enable
- [ ] Assess need for two-person approval
- [ ] Assess if this is over-engineered for personal MVP
- [ ] Define minimum production checklist
- [ ] List requirements that must block live deployment

## Section 27 — Release and Rollback

- [ ] Document how release version is formed
- [ ] Check for release branches
- [ ] Check for release tags
- [ ] Determine who creates releases
- [ ] List mandatory checks for release
- [ ] Check for changelog
- [ ] Identify changes requiring migration review
- [ ] Identify changes requiring exchange contract review
- [ ] Identify changes requiring financial-safety review
- [ ] Define backward compatibility policy
- [ ] Define DB compatibility policy
- [ ] Check if new version can run alongside old
- [ ] Can old and new versions execute trades simultaneously?
- [ ] How is double execution prevented during deployment?
- [ ] Should execution be put in maintenance mode?
- [ ] How is kill-switch state preserved?
- [ ] Assess need for canary deployment
- [ ] Does canary make sense for single-node MVP?
- [ ] Assess need for shadow deployment
- [ ] Document testnet validation before live
- [ ] Define mandatory smoke tests
- [ ] Define rollback trigger
- [ ] Estimate rollback speed
- [ ] Locate previous image storage
- [ ] Document handling of incompatible migration
- [ ] Assess forward-only migration policy
- [ ] Document DB backup restoration procedure
- [ ] Verify rollback was ever tested practically
- [ ] Determine who decides on rollback
- [ ] List events that automatically stop rollout

## Section 28 — Runtime Recovery and Disaster Scenarios

- [ ] Document behavior on monitor crash
- [ ] Document behavior on engine crash
- [ ] Document behavior on Telegram bot crash
- [ ] Document behavior on host crash
- [ ] Document behavior on monitor-engine network partition
- [ ] Document behavior after monitor restart
- [ ] Document behavior after engine restart
- [ ] Verify submitted attempt keys are restored
- [ ] Verify restart cannot send duplicate orders
- [ ] Verify restart cannot miss an exit
- [ ] Verify restart cannot incorrectly consider position closed
- [ ] Determine how system finds real open orders
- [ ] Determine how system finds real positions
- [ ] Check for reconciliation on startup
- [ ] Check for periodic reconciliation
- [ ] Document behavior on DB-exchange mismatch
- [ ] Document who receives alert
- [ ] Verify further execution is blocked
- [ ] Check for manual resolution endpoint
- [ ] Check for emergency close endpoint
- [ ] Verify emergency close works without monitor
- [ ] Verify emergency close works with unavailable DB
- [ ] Check for separate recovery CLI
- [ ] Check for orphan position runbook
- [ ] Check for unknown order state runbook
- [ ] Check for post-submit timeout runbook
- [ ] Check for partial fill runbook
- [ ] Check for exit rejection runbook
- [ ] Check for invalid credentials runbook
- [ ] Check for wrong account mode runbook
- [ ] Check for insufficient balance runbook
- [ ] Check for exchange maintenance runbook
- [ ] Check for clock drift runbook
- [ ] Check for corrupt DB runbook
- [ ] Check for disk full runbook
- [ ] Check for expired TLS certificate runbook
- [ ] Check for compromised API key runbook
- [ ] Check for failed deployment runbook
- [ ] Check for failed migration runbook
- [ ] Identify scenarios current system cannot recover from
- [ ] Define mandatory recovery requirements before live

## Section 29 — Documentation and Ownership

- [ ] List all technical documents found
- [ ] Mark documents that are current
- [ ] Mark documents that contradict code
- [ ] Mark documents for old architecture
- [ ] Identify primary README
- [ ] Check for setup guide
- [ ] Check for configuration reference
- [ ] Check for dependency inventory
- [ ] Check for runtime topology
- [ ] Check for deployment topology
- [ ] Check for CI/CD description
- [ ] Check for secrets policy
- [ ] Check for backup runbook
- [ ] Check for deployment runbook
- [ ] Check for rollback runbook
- [ ] Check for recovery runbook
- [ ] Check for incident runbook
- [ ] Check for monitoring runbook
- [ ] Check for exchange connectivity runbook
- [ ] Check for environment matrix
- [ ] Check for port matrix
- [ ] Check for external dependency matrix
- [ ] Check for software version matrix
- [ ] Check for ADR on Java version
- [ ] Check for ADR on SQLite/PostgreSQL
- [ ] Check for ADR on Docker deployment
- [ ] Check for ADR on single-host MVP
- [ ] Check for ADR on Singapore execution host
- [ ] Check for ADR on Prometheus/Grafana

### Ownership
- [ ] Identify who owns each runtime process
- [ ] Identify who owns CI
- [ ] Identify who owns production host
- [ ] Identify who owns exchange credentials
- [ ] Identify who is responsible for dependency updates
- [ ] Identify who is responsible for vulnerability response
- [ ] Identify who is responsible for backups
- [ ] Identify who is responsible for alerts
- [ ] Identify who makes live enable decisions
- [ ] Identify documents that should auto-update
- [ ] Identify documents that should be generated from code

## Section 32 Deliverables — Compilation

### 32.1 Technology Matrix
- [ ] Compile final Technology Matrix with all resolved versions
- [ ] Mark each technology: purpose, runtime required? status (VERIFIED/INFERRED/DOCUMENTED_ONLY), evidence

### 32.2 Direct Dependency Matrix
- [ ] Compile per-module Direct Dependency Matrix
- [ ] Include: module, dependency, configuration, version, actual usage, required?, risk, recommendation

### 32.3 Transitive Conflict Report
- [ ] Compile Transitive Conflict Report
- [ ] Include: library, resolved version, requested versions, brought by, conflict severity, risk

### 32.4 Unused Dependency Report
- [ ] Compile Unused Dependency Report
- [ ] Include: module, dependency, evidence of non-use, confidence, suggested action

### 32.5 Dependency Risk Register
- [ ] Compile Dependency Risk Register
- [ ] Include: dependency, version, CVE/EOL/license issue, severity, exposure, required action

### 32.6 Runtime Process Matrix
- [ ] Compile Runtime Process Matrix
- [ ] Include: process, artifact, main class, port, profile, dependencies, state owned, required

### 32.7 Environment Matrix
- [ ] Compile Environment Matrix (local-safe, testnet, staging, prod-like, intended production)
- [ ] Include: auth, internal auth, execution loop, live orders, kill switch, credentials, exchange URLs, metadata sync, DB path, observability, public ports

### 32.8 External Dependency Matrix
- [ ] Compile External Dependency Matrix
- [ ] Include: service, environment, URL source, auth, timeout, retry, rate limit, failure impact

### 32.9 Port and Network Matrix
- [ ] Compile Port and Network Matrix
- [ ] Include: component, listen address, port, protocol, external exposure, auth, required callers

### 32.10 Secret Inventory
- [ ] Compile Secret Inventory (NO actual values!)
- [ ] Include: secret type, environment name, consumer, current storage, rotation, exposure risk

### 32.11 Docker Report
- [ ] Compile Docker Report
- [ ] Include: image/service, base image, user, architecture, size, volumes, ports, hardening gaps

### 32.12 CI/CD Pipeline Map
- [ ] Compile CI/CD Pipeline Map
- [ ] Include: job, trigger, runner, commands, secrets, artifact, deploy side effect, status

### 32.13 Deployment Topology
- [ ] Create diagram 1: Current local development topology
- [ ] Create diagram 2: Current Mac mini staging topology
- [ ] Create diagram 3: Current Docker Compose topology
- [ ] Create diagram 4: Minimal recommended single-node MVP production topology

### 32.14 Reproducibility Report
- [ ] Answer: can project be built on clean machine?
- [ ] Answer: can it be built without local cache?
- [ ] Answer: are all versions pinned?
- [ ] Answer: does dependency locking exist?
- [ ] Answer: does dependency verification exist?
- [ ] Answer: are Docker images reproducible?
- [ ] Answer: can commit be determined for each deployed artifact?
- [ ] Answer: can environment be restored from scratch?

### 32.15 Installation Guide
- [ ] Write Prerequisites section
- [ ] Write Local Safe Setup section
- [ ] Write Test Execution section
- [ ] Write Local Runtime section
- [ ] Write Docker Runtime section
- [ ] Write Staging Setup section
- [ ] Write Production Prerequisites section
- [ ] Write Verification Commands section
- [ ] Write Shutdown Commands section
- [ ] Write Cleanup and Recovery section

### 32.16 Findings by Severity
- [ ] Compile P0 findings (could cause financial impact or loss of control)
- [ ] Compile P1 findings (blocks safe testnet/production runtime)
- [ ] Compile P2 findings (blocks reproducible deployment or recovery)
- [ ] Compile P3 findings (maintainability, cleanup, DX)
- [ ] For each finding: fact, evidence, affected files, impact, recommendation, mandatory before Gate testnet? mandatory before live?

### 32.17 Owner Decision Register
- [ ] Compile Owner Decision Register from all sections requiring human decision
- [ ] Include: decision, options, current evidence, recommended default, owner answer required

### 32.18 Final Readiness Verdict
- [ ] Assess: Developer setup readiness (READY / READY WITH CONDITIONS / NOT READY / UNKNOWN)
- [ ] Assess: Clean-machine build readiness
- [ ] Assess: CI readiness
- [ ] Assess: Docker readiness
- [ ] Assess: Staging readiness
- [ ] Assess: Testnet deployment readiness
- [ ] Assess: Production infrastructure readiness
- [ ] Assess: Operational recovery readiness
- [ ] Assess: Dependency security readiness

## Section 33 — Final Conclusions

### Final answer checklist
- [ ] Answer: what is the complete technology stack?
- [ ] Answer: what exact versions are used?
- [ ] Answer: what direct dependencies does each module need?
- [ ] Answer: which dependencies are not used?
- [ ] Answer: which dependencies conflict or are outdated?
- [ ] Answer: which dependencies have security or licensing risks?
- [ ] Answer: what minimum software set does a developer need?
- [ ] Answer: what minimum software set does a runtime host need?
- [ ] Answer: which processes must run for the full project?
- [ ] Answer: which processes are mandatory for execution?
- [ ] Answer: which processes are optional?
- [ ] Answer: which external services are mandatory?
- [ ] Answer: which environment variables are mandatory?
- [ ] Answer: which profiles exist and are they safe?
- [ ] Answer: can build be reproduced on a clean machine?
- [ ] Answer: can Docker images be reproduced?
- [ ] Answer: can staging be deployed from scratch?
- [ ] Answer: can production be deployed from scratch?
- [ ] Answer: can rollback be performed safely?
- [ ] Answer: can system recover from host restart?
- [ ] Answer: can deployed code version be identified?
- [ ] Answer: can it be proven that CI cannot create a real order?
- [ ] Answer: which DevOps components actually work?
- [ ] Answer: which DevOps components are stubs?
- [ ] Answer: what must be fixed before Gate automated testnet?
- [ ] Answer: what must be fixed before first live micro-trade?
- [ ] Answer: which technologies and tools are redundant for MVP?
- [ ] Answer: which technologies are missing but truly needed?
- [ ] Answer: which human decisions block the final deployment architecture?
- [ ] Answer: what is the next engineering phase after this audit?

## Phase output
- [ ] Complete Findings by Severity (P0–P3)
- [ ] Complete Owner Decision Register
- [ ] Complete Final Readiness Verdict
- [ ] Complete Installation Guide
- [ ] Complete Section 33 Final Conclusions
- [ ] Verify all 18 deliverables from Section 32 are present
- [ ] Verify all evidence is appropriately classified (VERIFIED/INFERRED/DOCUMENTED_ONLY/CONTRADICTED/UNKNOWN)
- [ ] Save final audit report to `tasks/audit-round-3/`
