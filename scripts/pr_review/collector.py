"""Collects PR diff, changed files, and optional CI context via GitHub REST API."""
from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request

from pr_review.models import PullRequestContext
from sanitizer import sanitize as _sanitize

_TIMEOUT = 30
_MAX_DIFF_BYTES = 48_000   # ~12K tokens; enough for most PRs
_MAX_FILES = 40            # beyond this we switch to summary mode

# File patterns to skip — pure noise for an AI review
_SKIP_SUFFIXES = (
    ".lock", ".lockfile", ".sum", ".png", ".jpg", ".svg", ".ico", ".woff", ".woff2",
    ".min.js", ".min.css", ".map",
)
_SKIP_NAMES = (
    "gradlew", "gradlew.bat", "gradle-wrapper.jar",
    "package-lock.json", "yarn.lock", "pnpm-lock.yaml",
)


def _api_get(path: str, token: str) -> dict | list | None:
    url = f"https://api.github.com{path}"
    req = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=_TIMEOUT) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        body = e.read().decode(errors="replace")
        print(f"[collector] WARNING: GET {path} → HTTP {e.code}: {body[:200]}")
        return None
    except Exception as exc:
        print(f"[collector] WARNING: GET {path} failed: {exc}")
        return None


def _get_diff(repo: str, pr_number: int, token: str) -> str | None:
    url = f"https://api.github.com/repos/{repo}/pulls/{pr_number}"
    req = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github.v3.diff",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=_TIMEOUT) as resp:
            return resp.read().decode(errors="replace")
    except urllib.error.HTTPError as e:
        body = e.read().decode(errors="replace")
        print(f"[collector] WARNING: diff fetch → HTTP {e.code}: {body[:200]}")
        return None
    except Exception as exc:
        print(f"[collector] WARNING: diff fetch failed: {exc}")
        return None


def _get_changed_files(repo: str, pr_number: int, token: str) -> list[str]:
    data = _api_get(f"/repos/{repo}/pulls/{pr_number}/files?per_page=100", token)
    if not isinstance(data, list):
        return []
    return [f["filename"] for f in data if isinstance(f, dict) and "filename" in f]


def _should_skip(filename: str) -> bool:
    return (
        any(filename.endswith(s) for s in _SKIP_SUFFIXES)
        or any(filename.endswith(n) for n in _SKIP_NAMES)
    )


def _filter_diff(raw_diff: str) -> str:
    lines = raw_diff.splitlines(keepends=True)
    result: list[str] = []
    skip_current = False
    for line in lines:
        if line.startswith("diff --git"):
            filename = line.split(" b/", 1)[-1].strip()
            skip_current = _should_skip(filename)
        if not skip_current:
            result.append(line)
    return "".join(result)


def collect(repo: str, pr_number: int, token: str, ci_context: str = "") -> PullRequestContext:
    """Fetch and prepare PR context for AI review."""
    raw_diff = _get_diff(repo, pr_number, token) or ""
    changed_files = _get_changed_files(repo, pr_number, token)

    filtered = _filter_diff(raw_diff)
    sanitized = _sanitize(filtered)

    truncated = False
    if len(sanitized) > _MAX_DIFF_BYTES:
        sanitized = sanitized[:_MAX_DIFF_BYTES] + "\n\n[... diff truncated ...]"
        truncated = True
        print(f"[collector] Diff truncated to {_MAX_DIFF_BYTES} bytes")

    meaningful_files = [
        f for f in changed_files
        if not _should_skip(f)
    ]

    ci_sanitized = _sanitize(ci_context) if ci_context else ""

    print(f"[collector] PR #{pr_number}: {len(meaningful_files)} meaningful files, "
          f"{len(sanitized)} diff chars, truncated={truncated}")

    return PullRequestContext(
        pr_number=pr_number,
        repo=repo,
        diff=sanitized,
        changed_files=tuple(meaningful_files),
        diff_truncated=truncated,
        ci_context=ci_sanitized,
    )
