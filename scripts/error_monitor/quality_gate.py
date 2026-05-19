"""Quality gate — decides whether an AI analysis result is worth acting on.

All criteria must pass for an issue to be created.
"""
from __future__ import annotations

_ACTIONABLE_SEVERITIES = frozenset({"MEDIUM", "HIGH", "CRITICAL"})
_MIN_CONFIDENCE = 0.4


def passes(result: dict) -> tuple[bool, str]:
    """Evaluate *result* against the quality gate criteria.

    Returns ``(True, "ok")`` if all criteria are met.
    Returns ``(False, "<reason>")`` on the first failing criterion.
    """
    if not result.get("shouldCreateIssue"):
        return False, "shouldCreateIssue is false"

    severity = result.get("severity", "")
    if severity not in _ACTIONABLE_SEVERITIES:
        return False, f"severity '{severity}' is not actionable (need MEDIUM/HIGH/CRITICAL)"

    confidence = result.get("confidence", 0.0)
    try:
        confidence = float(confidence)
    except (TypeError, ValueError):
        return False, "confidence is not a valid number"

    if confidence < _MIN_CONFIDENCE:
        return False, f"confidence {confidence:.2f} is below threshold {_MIN_CONFIDENCE}"

    fingerprint = result.get("fingerprint", "")
    if not fingerprint or not str(fingerprint).strip():
        return False, "fingerprint is empty"

    evidence = result.get("evidence", [])
    if not evidence or not isinstance(evidence, list) or len(evidence) == 0:
        return False, "evidence list is empty"

    recommended_fix = result.get("recommendedFix", "")
    if not recommended_fix or not str(recommended_fix).strip():
        return False, "recommendedFix is empty"

    return True, "ok"
