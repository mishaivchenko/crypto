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
_PAGE_SIZE = 100


class GitHubError(RuntimeError):
    """Raised when GitHub issue operations cannot continue safely."""


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
        print(f"[quality-backlog] WARNING: {method} {path} -> HTTP {exc.code}")
        return None
    except Exception as exc:
        print(f"[quality-backlog] WARNING: {method} {path} failed: {exc}")
        return None


def _request_all_pages(path: str) -> list[dict] | None:
    items: list[dict] = []
    page = 1
    while True:
        separator = "&" if "?" in path else "?"
        result = _request("GET", f"{path}{separator}per_page={_PAGE_SIZE}&page={page}")
        if not isinstance(result, list):
            return None
        items.extend(result)
        if len(result) < _PAGE_SIZE:
            return items
        page += 1


def ensure_labels(labels: list[str], repo: str) -> bool:
    existing_raw = _request_all_pages(f"/repos/{repo}/labels")
    if existing_raw is None:
        print("[quality-backlog] ERROR: failed to list GitHub labels")
        return False
    existing = {label["name"] for label in existing_raw}
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
        result = _request(
            "POST",
            f"/repos/{repo}/labels",
            {
                "name": label,
                "color": colors.get(label, "ededed"),
            },
        )
        if result is None:
            print(f"[quality-backlog] ERROR: failed to create GitHub label '{label}'")
            return False
    return True


def find_existing_issue(repo: str, fingerprint: str) -> Optional[int]:
    labels = urllib.parse.quote("quality-backlog")
    issues = _request_all_pages(f"/repos/{repo}/issues?state=open&labels={labels}")
    if issues is None:
        raise GitHubError("failed to list existing quality backlog issues")
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
