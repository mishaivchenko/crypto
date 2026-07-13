# Audit Round 3 — Phase 5: Docker & CI/CD

**Deliverables:** Docker Report | CI/CD Pipeline Map | Deployment Topology

## Section 12 — Docker Images

### Dockerfile inventory
- [ ] Count all Dockerfiles in the project
- [ ] Check if a generic Dockerfile is used for all modules
- [ ] List build arguments and their default values
- [ ] Check if build argument values are validated
- [ ] Identify build stages
- [ ] Is the app built inside Docker (multi-stage) or is JAR pre-built?
- [ ] Does image build depend on local `build/libs/`?
- [ ] Can image be built on a clean machine with one command?
- [ ] Run `docker version` — record Docker version

### Base images
- [ ] Identify base image used for build stage
- [ ] Identify base image used for runtime stage
- [ ] Document why this specific JRE version was chosen
- [ ] Check if base image is pinned by digest
- [ ] Check if repeated builds can get a different base image
- [ ] Check if base image is scanned for vulnerabilities
- [ ] Check how often base image is updated

### Architecture
- [ ] Check for multi-architecture image support
- [ ] Check if `linux/amd64` is built
- [ ] Check if `linux/arm64` is built
- [ ] Verify both variants are tested

### Container security
- [ ] Identify which user the container runs under
- [ ] Check if it runs as root
- [ ] Check for non-root UID/GID
- [ ] Identify which directories are writable
- [ ] Check if read-only root filesystem is possible
- [ ] Identify required Linux capabilities
- [ ] Check if all capabilities can be dropped
- [ ] Check if `NET_ADMIN` capability is needed
- [ ] Check for `HEALTHCHECK` instruction in Dockerfile
- [ ] Check how `SIGTERM` is handled
- [ ] Is Java process PID 1?
- [ ] Check if an init process (tini, dumb-init) is needed
- [ ] Check for zombie process risk

### Image contents
- [ ] List environment variables embedded in image
- [ ] Check if secrets can leak into image history
- [ ] Check if `.dockerignore` exists
- [ ] Verify `.dockerignore` excludes: `.git`, `.env`, `*.db`, `*.log`, secrets
- [ ] Check if Docker context can include credentials
- [ ] Run `docker image inspect <image>` — record image id, size, layers
- [ ] Run `docker history <image>` — review layer contents
- [ ] Check image size (smallest and largest)
- [ ] Check for unnecessary files in image
- [ ] Check if runtime image contains a shell
- [ ] Check if runtime image contains a package manager
- [ ] Determine if shell is required for operations

### Volumes and data
- [ ] Identify where application logs go (stdout vs files)
- [ ] Check if app writes logs to stdout
- [ ] Check if app writes logs to files inside container
- [ ] Locate SQLite volume
- [ ] Check who creates the volume directory
- [ ] Check volume permissions
- [ ] Verify data persists after container recreate
- [ ] Document volume backup procedure
- [ ] Check what happens if image UID changes

### JVM and resource settings
- [ ] Check for resource limits in Docker/Compose
- [ ] Check for JVM memory limits
- [ ] Verify JVM respects cgroup limits
- [ ] Check if `-Xms` and `-Xmx` are set
- [ ] Propose `MaxRAMPercentage` and `InitialRAMPercentage` as alternatives
- [ ] Check GC configuration
- [ ] Identify which GC is used
- [ ] Check if GC pauses are logged
- [ ] Check timezone and locale configuration
- [ ] Check CA certificates installation
- [ ] Verify HTTPS works in minimal image
- [ ] Check for SBOM for Docker image
- [ ] Check for image signature
- [ ] Evaluate need for Cosign
- [ ] Check for SLSA provenance attestation
- [ ] List mandatory Docker hardening changes before production

## Section 13 — Docker Compose

### Services
- [ ] List all services defined in Compose
- [ ] Identify mandatory services
- [ ] Identify optional services
- [ ] Check if monitor service is defined
- [ ] Check if engine service is defined
- [ ] Check if telegram-bot service is defined
- [ ] Document why Telegram bot may be absent
- [ ] Check if Prometheus is a service
- [ ] Check if Grafana is a service
- [ ] Check if database is a separate service (it shouldn't be for SQLite)
- [ ] Document why SQLite is not a separate service
- [ ] Run `docker compose config` — validate Compose file
- [ ] Run `docker compose version` — record version

### Networking
- [ ] List networks created by Compose
- [ ] Verify engine is isolated in an internal network
- [ ] List ports published to host
- [ ] Check if engine port needs to be published
- [ ] Check if Prometheus port needs to be published
- [ ] Check if Grafana port needs to be published

### Volumes and paths
- [ ] List all volumes
- [ ] List all bind mounts
- [ ] Identify paths that depend on host OS
- [ ] Verify Compose works on Linux
- [ ] Verify Compose works on macOS
- [ ] Run `docker compose images` — record current image tags/digests

### Profiles and environments
- [ ] Check for Compose profiles
- [ ] Check for dev/staging/prod separation in Compose
- [ ] Check if single compose file handles all environments
- [ ] Check for override files (`docker-compose.override.yml`)
- [ ] Check how secrets are passed (via `.env`, Compose secrets)
- [ ] Check if Compose secrets can be used

### Dependencies and health
- [ ] Check `depends_on` configuration
- [ ] Check for health-based dependencies
- [ ] Document what happens if monitor is not ready yet
- [ ] Document what happens if Prometheus didn't start
- [ ] Check restart policies
- [ ] Should engine auto-restart?
- [ ] Is auto-restart safe during an open position?
- [ ] Document what happens after restart with an open position
- [ ] Check startup ordering
- [ ] Check graceful stop ordering
- [ ] Check resource limits in Compose
- [ ] Check log rotation configuration

### Operations
- [ ] Check how upgrade is performed
- [ ] Check how rollback is performed
- [ ] Check how database backup is performed
- [ ] Check how current deployed image tag is verified
- [ ] Check if `latest` tag is used (and risks)
- [ ] Check if image digests are fixed
- [ ] Check if `docker compose pull` can unexpectedly update a service
- [ ] Document canonical start command
- [ ] Document canonical stop command
- [ ] Document canonical safe-update command
- [ ] Document environment verification command before start
- [ ] Document smoke test after start
- [ ] Check for Compose config validation in CI
- [ ] Assess: is current Compose production-ready or just a dev scaffold?
- [ ] Run `docker compose ps` — record current service status

## Sections 14-15 — CI Pipeline and Artifact Build

### Workflow inventory
- [ ] List all GitHub Actions workflow files
- [ ] Read each workflow YAML and record trigger events
- [ ] Record what runs on `pull_request`
- [ ] Record what runs on `push`
- [ ] Record what runs on schedule
- [ ] Record what runs manually (`workflow_dispatch`)

### Jobs and conditions
- [ ] Identify mandatory (required) jobs
- [ ] Identify jobs with `continue-on-error`
- [ ] Identify conditionally skipped jobs
- [ ] Check path filters: does a shared module change trigger engine tests?
- [ ] Does a Gradle config change trigger the full test set?
- [ ] Does a Dockerfile change trigger application tests?
- [ ] Does a migration change trigger migration tests?

### CI environment
- [ ] Identify JDK version used in CI
- [ ] Identify Gradle version used
- [ ] Verify Gradle Wrapper is used
- [ ] Check for Wrapper validation step
- [ ] Check dependency cache configuration
- [ ] Review cache key formation
- [ ] Check if cache can mask dependency problems
- [ ] Check if clean build without cache exists
- [ ] Check for matrix builds (OS, JDK, architecture)
- [ ] Identify runner type (Ubuntu, macOS, self-hosted)
- [ ] Identify jobs on self-hosted Mac mini
- [ ] Check which credentials are available to self-hosted runner
- [ ] Check if runner is isolated from personal environment
- [ ] Check if workspace is cleaned after job
- [ ] Check if previous build can leave `.env`
- [ ] Check if job can read user's home directory
- [ ] Check if runner has persistent privileges

### CI workflow analysis
- [ ] Check for protected environments
- [ ] Check if manual approval is required for staging
- [ ] Check if manual approval is required for production
- [ ] List GitHub permissions assigned to workflow
- [ ] Check if `permissions: read-all` or unnecessarily broad
- [ ] Check for OIDC usage
- [ ] Check for long-lived cloud credentials
- [ ] Identify all third-party GitHub Actions
- [ ] Check if actions are pinned by commit SHA (vs `@v4` mutable tags)
- [ ] Assess supply-chain risk of each action

### Build and test in CI
- [ ] Check if `./gradlew build` is run
- [ ] Check if `./gradlew test` is run separately
- [ ] Check if Spotless is run
- [ ] Check if JaCoCo verification is run
- [ ] Check if Pitest is run
- [ ] Check if engine TDD gate is run
- [ ] Check if security scan is run
- [ ] Document why security scan is not a PR gate
- [ ] Check if security scan requires `NVD_API_KEY`
- [ ] Document behavior when NVD key is missing
- [ ] Check if test reports are published as artifacts
- [ ] Check if coverage reports are published
- [ ] Check if mutation reports are published
- [ ] Check if SBOM is published
- [ ] Check if dependency reports are published
- [ ] Check if Docker scan reports are published

### Safety in CI
- [ ] Check for secret scanning
- [ ] Check for CodeQL
- [ ] Check for static analysis (Checkstyle, PMD, SpotBugs)
- [ ] Check for Java compiler warnings as errors
- [ ] Check for architecture tests
- [ ] Check for UI tests
- [ ] Check for end-to-end tests
- [ ] Check for testnet smoke tests
- [ ] Verify CI cannot accidentally send real orders
- [ ] List guards preventing live execution in CI
- [ ] Verify fake credentials are used
- [ ] Verify exchange APIs are mocked
- [ ] Check if external API calls exist in unit/integration tests
- [ ] Check if CI is flaky due to external APIs

### CI performance and reliability
- [ ] Check job timeouts
- [ ] Check concurrency cancellation for stale runs
- [ ] Check branch protection rules
- [ ] List required status checks
- [ ] Check if merge is allowed with failed security scan
- [ ] Check if merge is allowed with skipped engine gate
- [ ] Measure average CI duration
- [ ] Identify slowest jobs
- [ ] Identify flaky jobs
- [ ] Check for retry-failed-jobs mechanism
- [ ] Assess if retry hides instability

### Artifact registry
- [ ] Check where release artifacts are created
- [ ] Check if GitHub Release exists
- [ ] Check if JAR artifacts are created
- [ ] Check if Docker images are created
- [ ] Identify container registry
- [ ] Document who has push access
- [ ] Check how registry authentication works
- [ ] List image tags created (SHA, semver, branch, latest)
- [ ] Verify ability to map image to specific commit
- [ ] Check if immutable tags prevent overwrite
- [ ] Check retention policy (how many old images kept)
- [ ] Verify ability to roll back to previous image
- [ ] Check for image signature
- [ ] Check for vulnerability scan before publish
- [ ] Check if critical CVE blocks publishing
- [ ] Check if SBOM is published with image
- [ ] Check if build provenance is published

### Release artifacts
- [ ] List artifacts needed for offline recovery
- [ ] Locate migration scripts storage
- [ ] Locate configuration templates storage
- [ ] Check for release manifest
- [ ] Verify manifest includes versions of all processes
- [ ] Check if monitor and engine can get different versions on deploy
- [ ] Check how partial release is prevented
- [ ] Assess need for a single release bundle

## Phase output
- [ ] Compile Docker Report (image/service, base image, user, architecture, size, volumes, ports, hardening gaps)
- [ ] Compile CI/CD Pipeline Map (job, trigger, runner, commands, secrets, artifact, deploy side effect, status)
- [ ] Compile Deployment Topology (4 diagrams: local dev, Mac mini staging, Docker Compose, minimal single-node MVP)
