"""Tests for pr_review.quality_gate module."""
import sys
import os
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from pr_review.models import Concern, ReviewResult
from pr_review.quality_gate import passes


def _concern(severity: str = "MEDIUM") -> Concern:
    return Concern(severity=severity, file="Foo.java", line_hint=1,
                   category="BUG", message="msg", recommendation="fix")


def _result(
    decision: str = "COMMENT",
    confidence: float = 0.75,
    concerns: tuple[Concern, ...] = (),
) -> ReviewResult:
    return ReviewResult(
        review_decision=decision,
        confidence=confidence,
        summary="summary",
        risk_level="MEDIUM",
        architecture_concerns=(),
        correctness_concerns=concerns,
        concurrency_concerns=(),
        trading_risk_concerns=(),
        observability_concerns=(),
        test_concerns=(),
        positive_notes=(),
    )


class TestPrReviewQualityGate(unittest.TestCase):

    def test_passes_with_medium_concern_and_sufficient_confidence(self):
        ok, reason = passes(_result(concerns=(_concern("MEDIUM"),)))
        self.assertTrue(ok)
        self.assertEqual(reason, "ok")

    def test_rejects_low_confidence(self):
        ok, reason = passes(_result(confidence=0.50, concerns=(_concern("HIGH"),)))
        self.assertFalse(ok)
        self.assertIn("0.50", reason)

    def test_passes_approve_with_no_concerns(self):
        # APPROVE always posts — коллектив должен знать что всё хорошо
        ok, _ = passes(_result(decision="APPROVE", confidence=0.9, concerns=()))
        self.assertTrue(ok)

    def test_passes_with_only_low_concerns(self):
        ok, _ = passes(_result(decision="COMMENT", confidence=0.65, concerns=(_concern("LOW"),)))
        self.assertTrue(ok)

    def test_passes_at_exact_confidence_threshold(self):
        ok, _ = passes(_result(confidence=0.55, concerns=(_concern("HIGH"),)))
        self.assertTrue(ok)

    def test_rejects_just_below_threshold(self):
        ok, _ = passes(_result(confidence=0.549, concerns=(_concern("HIGH"),)))
        self.assertFalse(ok)


if __name__ == "__main__":
    unittest.main()
