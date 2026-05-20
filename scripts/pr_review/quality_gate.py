"""Quality gate — decides whether the review result is worth posting."""
from __future__ import annotations

from pr_review.models import ReviewResult

_MIN_CONFIDENCE = 0.55
_ACTIONABLE_SEVERITIES = frozenset({"MEDIUM", "HIGH", "CRITICAL"})


def passes(result: ReviewResult) -> tuple[bool, str]:
    """Return (True, "ok") if the review should be posted, (False, reason) otherwise."""
    if result.confidence < _MIN_CONFIDENCE:
        return False, f"confidence {result.confidence:.2f} below threshold {_MIN_CONFIDENCE}"

    all_concerns = result.all_concerns()
    actionable = [c for c in all_concerns if c.severity in _ACTIONABLE_SEVERITIES]
    if not actionable:
        # Still post if reviewDecision is not APPROVE (model may have low-severity concerns only)
        if result.review_decision == "APPROVE":
            return False, "no actionable concerns and decision is APPROVE"

    return True, "ok"
