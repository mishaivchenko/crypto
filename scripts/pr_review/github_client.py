"""GitHub REST API client for PR review operations — no gh CLI, stdlib only."""
from __future__ import annotations

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


def _request(method: str, path: str, body: dict | None = None, accept: str | None = None) -> dict | list | None:
    url = f"{_API_BASE}{path}"
    data = json.dumps(body).encode() if body is not None else None
    headers = {
        "Authorization": f"Bearer {_token()}",
        "Accept": accept or "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "Content-Type": "application/json",
    }
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=_TIMEOUT) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        body_text = e.read().decode(errors="replace")
        print(f"[pr_github] WARNING: {method} {path} → HTTP {e.code}: {body_text[:300]}")
        return None
    except Exception as exc:
        print(f"[pr_github] WARNING: {method} {path} failed: {exc}")
        return None


def get_pr_comments(repo: str, pr_number: int) -> list[dict]:
    """Fetch all issue (non-review) comments on a PR."""
    result = _request("GET", f"/repos/{repo}/issues/{pr_number}/comments?per_page=100")
    return result if isinstance(result, list) else []


def get_review_comments(repo: str, pr_number: int) -> list[dict]:
    """Fetch all review (inline) comments on a PR."""
    result = _request("GET", f"/repos/{repo}/pulls/{pr_number}/comments?per_page=100")
    return result if isinstance(result, list) else []


def post_pr_comment(repo: str, pr_number: int, body: str) -> Optional[int]:
    """Post a general comment. Returns comment id on success."""
    result = _request("POST", f"/repos/{repo}/issues/{pr_number}/comments", {"body": body})
    if result and isinstance(result, dict) and "id" in result:
        return int(result["id"])
    print(f"[pr_github] WARNING: failed to post PR comment")
    return None


def update_pr_comment(repo: str, comment_id: int, body: str) -> bool:
    """Update an existing general comment."""
    result = _request("PATCH", f"/repos/{repo}/issues/comments/{comment_id}", {"body": body})
    return result is not None


def post_review_with_comments(
    repo: str,
    pr_number: int,
    commit_sha: str,
    decision: str,
    body: str,
    inline_comments: list[dict],
) -> bool:
    """Submit a full PR review with optional inline comments.

    inline_comments: list of {"path": str, "line": int, "body": str}
    decision: "APPROVE" | "COMMENT" | "REQUEST_CHANGES"
    """
    payload: dict = {
        "commit_id": commit_sha,
        "body": body,
        "event": decision,
        "comments": inline_comments,
    }
    result = _request("POST", f"/repos/{repo}/pulls/{pr_number}/reviews", payload)
    return result is not None


def get_pr_head_sha(repo: str, pr_number: int) -> Optional[str]:
    """Return the head commit SHA of the PR."""
    result = _request("GET", f"/repos/{repo}/pulls/{pr_number}")
    if result and isinstance(result, dict):
        return result.get("head", {}).get("sha")
    return None
