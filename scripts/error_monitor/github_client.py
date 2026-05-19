"""GitHub operations via REST API using urllib (no gh CLI, no third-party deps).

All calls use GITHUB_TOKEN from the GH_TOKEN environment variable.
"""
from __future__ import annotations

import datetime
import json
import os
import urllib.error
import urllib.request
from typing import Optional

_API_BASE = "https://api.github.com"
_TIMEOUT = 30


def _token() -> str:
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN", "")
    if not token:
        raise EnvironmentError("GH_TOKEN or GITHUB_TOKEN environment variable is not set")
    return token


def _request(method: str, path: str, body: dict | None = None) -> dict | list | None:
    url = f"{_API_BASE}{path}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {_token()}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=_TIMEOUT) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        body_text = e.read().decode(errors="replace")
        print(f"[github_client] WARNING: {method} {path} → HTTP {e.code}: {body_text[:200]}")
        return None
    except Exception as exc:
        print(f"[github_client] WARNING: {method} {path} failed: {exc}")
        return None


def find_existing_issue(fingerprint: str, repo: str) -> Optional[int]:
    """Search open issues labelled 'ai-log-analysis' for a matching fingerprint."""
    path = f"/repos/{repo}/issues?state=open&labels=ai-log-analysis&per_page=100"
    issues = _request("GET", path)
    if not isinstance(issues, list):
        return None
    marker = f"ai-log-fingerprint: {fingerprint}"
    for issue in issues:
        if marker in (issue.get("body") or ""):
            return int(issue["number"])
    return None


def add_occurrence_comment(
    issue_number: int,
    service: str,
    severity: str,
    evidence: list[str],
    repo: str,
) -> None:
    """Post a short recurrence comment to an existing issue."""
    today = datetime.date.today().isoformat()
    evidence_lines = "\n".join(f"- {e}" for e in evidence[:3])
    body = (
        f"**Recurrence detected** — {today}\n\n"
        f"Service: `{service}`  \n"
        f"Severity: **{severity}**\n\n"
        f"Evidence:\n{evidence_lines}"
    )
    result = _request("POST", f"/repos/{repo}/issues/{issue_number}/comments", {"body": body})
    if result is None:
        print(f"[github_client] WARNING: failed to add comment to issue #{issue_number}")


def create_issue(
    title: str,
    body: str,
    labels: list[str],
    repo: str,
) -> Optional[int]:
    """Create a new GitHub issue. Returns issue number on success, None on failure."""
    result = _request("POST", f"/repos/{repo}/issues", {
        "title": title,
        "body": body,
        "labels": labels,
    })
    if result and isinstance(result, dict) and "number" in result:
        return int(result["number"])
    return None


def ensure_labels(labels: list[str], repo: str) -> None:
    """Ensure all labels exist in the repo. Creates missing ones, ignores failures."""
    existing_raw = _request("GET", f"/repos/{repo}/labels?per_page=100")
    existing = {l["name"] for l in existing_raw} if isinstance(existing_raw, list) else set()

    colors = {
        "ai-log-analysis": "0075ca",
        "source:mac-mini": "e4e669",
        "component:engine": "d93f0b",
        "component:monitor": "d93f0b",
        "component:telegram-bot": "d93f0b",
        "area:websocket": "bfd4f2",
        "area:exchange-api": "bfd4f2",
        "area:docker": "bfd4f2",
        "priority:medium": "fbca04",
        "priority:high": "e11d48",
        "priority:critical": "b91c1c",
    }

    for label in labels:
        if label in existing:
            continue
        _request("POST", f"/repos/{repo}/labels", {
            "name": label,
            "color": colors.get(label, "ededed"),
        })
