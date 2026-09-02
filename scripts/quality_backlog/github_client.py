"""GitHub issue client for quality backlog automation."""
from __future__ import annotations

import json
import os
import urllib.error
import urllib.parse
import urllib.request
from typing import Optional

from quality_backlog.renderer import marker_for

_API_BASE = "https://api.github.com"
_TIMEOUT = 30


def _token() -> str:
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN", "")
    if not token:
        raise EnvironmentError("GH_TOKEN or GITHUB_TOKEN environment variable is not set")
    return token


def _request(method: str, path: str, body: dict | None = None) -> dict | list | None:
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        f"{_API_BASE}{path}",
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
    except urllib.error.HTTPError as exc:
        body_text = exc.read().decode(errors="replace")
        print(f"[quality-backlog] WARNING: {method} {path} -> HTTP {exc.code}: {body_text[:300]}")
        return None
    except Exception as exc:
        print(f"[quality-backlog] WARNING: {method} {path} failed: {exc}")
        return None


def ensure_labels(labels: list[str], repo: str) -> None:
    existing_raw = _request("GET", f"/repos/{repo}/labels?per_page=100")
    existing = {label["name"] for label in existing_raw} if isinstance(existing_raw, list) else set()
    colors = {
        "quality-backlog": "5319e7",
        "source:sonarqube": "1d76db",
        "tech-debt": "cfd3d7",
        "bug": "d73a4a",
        "security": "b60205",
        "coverage": "0e8a16",
        "priority:high": "e11d48",
        "priority:medium": "fbca04",
        "priority:low": "c5def5",
    }
    for label in labels:
        if label in existing:
            continue
        _request(
            "POST",
            f"/repos/{repo}/labels",
            {
                "name": label,
                "color": colors.get(label, "ededed"),
            },
        )


def find_existing_issue(repo: str, fingerprint: str) -> Optional[int]:
    labels = urllib.parse.quote("quality-backlog")
    issues = _request("GET", f"/repos/{repo}/issues?state=open&labels={labels}&per_page=100")
    if not isinstance(issues, list):
        return None
    marker = marker_for(fingerprint)
    for issue in issues:
        if marker in (issue.get("body") or ""):
            return int(issue["number"])
    return None


def create_issue(repo: str, title: str, body: str, labels: list[str]) -> Optional[int]:
    result = _request(
        "POST",
        f"/repos/{repo}/issues",
        {
            "title": title,
            "body": body,
            "labels": labels,
        },
    )
    if isinstance(result, dict) and "number" in result:
        return int(result["number"])
    return None


def add_comment(repo: str, issue_number: int, body: str) -> bool:
    result = _request("POST", f"/repos/{repo}/issues/{issue_number}/comments", {"body": body})
    return result is not None

