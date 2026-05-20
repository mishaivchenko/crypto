"""Quality gate — decides whether the review result is worth posting."""
from __future__ import annotations

from pr_review.models import ReviewResult

_MIN_CONFIDENCE = 0.60


def passes(result: ReviewResult) -> tuple[bool, str]:
    """Return (True, "ok") if the review should be posted, (False, reason) otherwise.

    APPROVE with zero concerns always posts — a clean bill of health is useful feedback.
    Only truly low-confidence results are silently skipped.
    """
    if result.confidence < _MIN_CONFIDENCE:
        return False, f"confidence {result.confidence:.2f} below threshold {_MIN_CONFIDENCE}"

    return True, "ok"
