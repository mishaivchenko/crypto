"""Enforce review decision rules independent of model output."""
from __future__ import annotations

try:
    from pr_review.models import ReviewResult
except ImportError:
    from models import ReviewResult  # type: ignore[no-redef]

_HIGH_SEVERITY = frozenset({"HIGH", "CRITICAL"})
_REQUEST_CHANGES_CONFIDENCE_THRESHOLD = 0.75


def enforce(result: ReviewResult) -> str:
    """Return the enforced review decision string.

    Rules (override model if necessary):
    - REQUEST_CHANGES requires >= 1 HIGH/CRITICAL concern AND confidence >= 0.75
    - APPROVE only if zero actionable concerns at any severity
    - Otherwise COMMENT
    """
    all_concerns = result.all_concerns()
    has_high = any(c.severity in _HIGH_SEVERITY for c in all_concerns)
    has_any = len(all_concerns) > 0

    if has_high and result.confidence >= _REQUEST_CHANGES_CONFIDENCE_THRESHOLD:
        return "REQUEST_CHANGES"
    if not has_any:
        return "APPROVE"
    return "COMMENT"
