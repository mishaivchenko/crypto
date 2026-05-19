"""GitHub operations via the `gh` CLI subprocess.

All calls use subprocess + gh CLI — no PyGithub or httpx dependencies.
"""
from __future__ import annotations

import datetime
import json
import os
import re
import shutil
import subprocess
from typing import Optional

_GH_TIMEOUT = 30  # seconds per gh invocation

# Regex to extract issue number from the URL printed by `gh issue create`
_RE_ISSUE_URL = re.compile(r"/issues/(\d+)\s*$")

# GitHub Actions self-hosted runner on macOS launches without a login shell,
# so /opt/homebrew/bin is not in PATH. Check absolute paths directly.
_GH_FALLBACK_PATHS = [
    "/opt/homebrew/bin/gh",
    "/usr/local/bin/gh",
    "/usr/bin/gh",
]


def _find_executable(name: str, fallbacks: list[str]) -> str | None:
    """Find an executable by name in PATH, then by absolute fallback paths."""
    found = shutil.which(name)
    if found:
        return found
    for path in fallbacks:
        if os.path.isfile(path) and os.access(path, os.X_OK):
            return path
    return None


def _gh_binary() -> str:
    result = _find_executable("gh", _GH_FALLBACK_PATHS)
    if result:
        return result
    raise FileNotFoundError(
        "gh CLI not found in PATH or fallbacks: " + ", ".join(_GH_FALLBACK_PATHS)
    )


def _run_gh(args: list[str], input_text: str | None = None) -> subprocess.CompletedProcess:
    """Run a `gh` CLI command and return the CompletedProcess."""
    return subprocess.run(
        [_gh_binary()] + args,
        capture_output=True,
        text=True,
        input=input_text,
        timeout=_GH_TIMEOUT,
    )


def find_existing_issue(fingerprint: str, repo: str) -> Optional[int]:
    """Search open issues with label 'ai-log-analysis' for a matching fingerprint.

    Returns the issue number if found, or None.
    """
    result = _run_gh([
        "issue", "list",
        "--repo", repo,
        "--state", "open",
        "--label", "ai-log-analysis",
        "--json", "number,body",
        "--limit", "100",
    ])
    if result.returncode != 0:
        print(f"[github_client] WARNING: gh issue list failed: {result.stderr.strip()}")
        return None

    try:
        issues = json.loads(result.stdout or "[]")
    except json.JSONDecodeError:
        return None

    marker = f"ai-log-fingerprint: {fingerprint}"
    for issue in issues:
        body = issue.get("body") or ""
        if marker in body:
            return int(issue["number"])

    return None


def add_occurrence_comment(
    issue_number: int,
    service: str,
    severity: str,
    evidence: list[str],
    repo: str,
) -> None:
    """Post a short occurrence comment to an existing issue."""
    today = datetime.date.today().isoformat()
    evidence_lines = "\n".join(f"- {e}" for e in evidence[:3])
    comment_body = (
        f"**Recurrence detected** — {today}\n\n"
        f"Service: `{service}`  \n"
        f"Severity: **{severity}**\n\n"
        f"Evidence:\n{evidence_lines}"
    )
    result = _run_gh([
        "issue", "comment", str(issue_number),
        "--repo", repo,
        "--body", comment_body,
    ])
    if result.returncode != 0:
        print(
            f"[github_client] WARNING: failed to add comment to issue #{issue_number}: "
            f"{result.stderr.strip()}"
        )


def create_issue(
    title: str,
    body: str,
    labels: list[str],
    repo: str,
) -> Optional[int]:
    """Create a new GitHub issue and return its number, or None on failure."""
    args = [
        "issue", "create",
        "--repo", repo,
        "--title", title,
        "--body", body,
    ]
    for label in labels:
        args += ["--label", label]

    result = _run_gh(args)
    if result.returncode != 0:
        print(f"[github_client] WARNING: gh issue create failed: {result.stderr.strip()}")
        return None

    # gh prints the issue URL on success, e.g.: https://github.com/org/repo/issues/42
    output = (result.stdout or "").strip()
    match = _RE_ISSUE_URL.search(output)
    if match:
        return int(match.group(1))

    print(f"[github_client] WARNING: could not parse issue number from output: {output!r}")
    return None


def ensure_labels(labels: list[str], repo: str) -> None:
    """Ensure all *labels* exist in the repo. Silently ignores individual failures."""
    for label in labels:
        result = _run_gh([
            "label", "create", label,
            "--repo", repo,
            "--force",
        ])
        if result.returncode != 0:
            # Non-fatal — label may already exist or user may lack permission
            pass
