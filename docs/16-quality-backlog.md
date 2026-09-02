# Quality Backlog Triage

Quality backlog triage turns local SonarQube findings into a controlled GitHub issue backlog. It does not create one issue per raw Sonar finding. Findings are grouped by component, category, and rule/theme so the resulting issues are useful engineering tasks.

## Inputs

- Local SonarQube project: `crypto`
- Required secret/env: `SONAR_TOKEN`
- Optional secret/env: `DEEPSEEK_API_KEY`
- GitHub issue creation: `GH_TOKEN` or `GITHUB_TOKEN`

The script reads SonarQube from `SONAR_HOST_URL`, defaulting to `http://127.0.0.1:9000`.

## Local dry-run on Mac mini

Run this on the Mac mini, where local SonarQube is reachable:

```bash
cd /Volumes/DevDisk/dev/projects/crypto
export SONAR_HOST_URL=http://127.0.0.1:9000
export SONAR_TOKEN=<local-sonarqube-token>
python3 scripts/quality_backlog/main.py --mode dry-run --limit 10
```

Reports are written to:

- `build/quality-backlog/quality-backlog.md`
- `build/quality-backlog/quality-backlog.json`

Dry-run never creates or updates GitHub issues.

## Create GitHub issues

Only use create mode after checking the dry-run report:

```bash
export GITHUB_REPOSITORY=mishaivchenko/crypto
export GH_TOKEN=<github-token-with-issues-write>
python3 scripts/quality_backlog/main.py --mode create-issues --limit 10
```

Each issue gets a hidden fingerprint marker. If the same group appears again, the script comments on the existing issue instead of creating a duplicate.

## GitHub Actions

Use the manual **Quality Backlog** workflow:

- `mode=dry-run` generates artifacts only.
- `mode=create-issues` creates or updates up to `limit` grouped issues.
- `include_ai_summary=true` lets DeepSeek improve summaries when `DEEPSEEK_API_KEY` is configured.

The workflow runs on `[self-hosted, mac-mini, staging]` because it needs access to the local SonarQube Server.

## Important behavior

Green CI means the current gates passed. It does not mean the historical codebase is clean. This triage job is the backlog path for old SonarQube findings, coverage gaps, duplication, and maintainability debt.
