"""Quality gate — decides whether the review result is worth posting."""
from __future__ import annotations

from pr_review.models import ReviewResult

_MIN_CONFIDENCE = 0.70
_ACTIONABLE_SEVERITIES = frozenset({"HIGH", "CRITICAL"})


def passes(result: ReviewResult) -> tuple[bool, str]:
    """Return (True, "ok") if the review should be posted, (False, reason) otherwise."""
    if result.confidence < _MIN_CONFIDENCE:
        return False, f"confidence {result.confidence:.2f} below threshold {_MIN_CONFIDENCE}"

    # Always post APPROVE — approval is itself useful signal even with minor concerns.
    if result.review_decision == "APPROVE":
        return True, "ok"

    high_concerns = [c for c in result.all_concerns() if c.severity in _ACTIONABLE_SEVERITIES]
    if not high_concerns:
        return False, "no HIGH or CRITICAL concerns — nothing actionable to post"

    return True, "ok"
