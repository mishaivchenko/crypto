"""Deduplicates AI review comments — avoids re-posting on every push."""
from __future__ import annotations

from pr_review.renderer import _SUMMARY_MARKER, concern_fingerprint
from pr_review.models import Concern


def has_summary_comment(existing_comments: list[dict]) -> bool:
    """Return True if any existing comment contains the summary marker."""
    for comment in existing_comments:
        body = comment.get("body", "") or ""
        if _SUMMARY_MARKER in body:
            return True
    return False


def already_posted_fingerprints(existing_comments: list[dict]) -> frozenset[str]:
    """Return the set of concern fingerprints already present in bot comments."""
    fps: set[str] = set()
    for comment in existing_comments:
        body = comment.get("body", "") or ""
        for line in body.splitlines():
            line = line.strip()
            if line.startswith("<!-- ai-pr-review-fingerprint:"):
                try:
                    fp = line.split(":", 1)[1].strip().rstrip(" -->").strip()
                    if fp:
                        fps.add(fp)
                except IndexError:
                    pass
    return frozenset(fps)


def filter_new_concerns(
    concerns: list[Concern],
    posted_fps: frozenset[str],
) -> list[Concern]:
    """Return only concerns whose fingerprint hasn't been posted yet."""
    return [c for c in concerns if concern_fingerprint(c) not in posted_fps]
