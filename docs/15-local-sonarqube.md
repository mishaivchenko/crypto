# Local SonarQube Server

This project analyzes code against a local SonarQube Server running on the self-hosted Mac mini runner. It does not use SonarCloud.

## Mac mini setup

All Mac mini actions should be run from this dev machine over SSH:

```bash
ssh mac-mini
```

On the Mac mini, clone or update the repository, then create the local environment file:

```bash
cd /Volumes/DevDisk/dev/projects/crypto
cp deploy/sonarqube/.env.example deploy/sonarqube/.env
```

Edit `deploy/sonarqube/.env` and replace `SONARQUBE_POSTGRES_PASSWORD` with a strong local password. Do not commit `.env`.

If Docker Desktop is installed but `docker` is missing from non-interactive shells, add it for the current session:

```bash
export PATH="/usr/local/bin:/Applications/Docker.app/Contents/Resources/bin:$PATH"
```

Validate the compose file:

```bash
docker compose --env-file deploy/sonarqube/.env -f deploy/sonarqube/docker-compose.yml config
```

Start SonarQube only after the `.env` file is ready:

```bash
docker compose --env-file deploy/sonarqube/.env -f deploy/sonarqube/docker-compose.yml up -d
```

SonarQube is bound to `127.0.0.1:9000` on the Mac mini. From the Mac mini, open `http://127.0.0.1:9000`, sign in with the initial `admin` / `admin` credentials, and change the admin password immediately.

## GitHub token

Create a SonarQube token after the first login:

1. Open `http://127.0.0.1:9000`.
2. Go to **My Account > Security**.
3. Generate a user token for CI analysis.
4. Add it to this GitHub repository as an Actions secret named `SONAR_TOKEN`.

No SonarQube token or database password should be committed to the repository.

## Local analysis

Before pushing feature work, run the local verification task:

```bash
./gradlew localVerify --no-daemon
```

It runs the blocking quality checks, the full Gradle build, and JaCoCo XML coverage reports. It does not run SonarQube analysis, so it does not require `SONAR_TOKEN`.

To make this run automatically before every `git push`, enable the repository hook path once:

```bash
git config core.hooksPath .githooks
```

The tracked `.githooks/pre-push` hook runs `./gradlew localVerify --no-daemon`. For an emergency push only, bypass it with:

```bash
SKIP_LOCAL_VERIFY=1 git push
```

With SonarQube running and a local token available:

```bash
export SONAR_HOST_URL=http://127.0.0.1:9000
export SONAR_TOKEN=<local-token>
./gradlew test jacocoTestReports sonar --no-daemon
```

`SONAR_HOST_URL` defaults to `http://127.0.0.1:9000` when omitted.

## CI behavior

The `SonarQube` GitHub Actions job runs on `[self-hosted, mac-mini, staging]` after the existing build and code-quality jobs. It installs monitor UI tooling, runs the backend tests plus JaCoCo XML reports, then uploads analysis to the local SonarQube Server through the Gradle `sonar` task.
