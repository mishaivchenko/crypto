"""Tests for pr_review.quality_gate module."""
import sys
import os
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from pr_review.models import Concern, ReviewResult
from pr_review.quality_gate import passes


def _concern(severity: str = "HIGH") -> Concern:
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
        risk_level="HIGH",
        architecture_concerns=(),
        correctness_concerns=concerns,
        concurrency_concerns=(),
        trading_risk_concerns=(),
        observability_concerns=(),
        test_concerns=(),
        positive_notes=(),
    )


class TestPrReviewQualityGate(unittest.TestCase):

    def test_passes_with_high_concern_and_sufficient_confidence(self):
        ok, reason = passes(_result(concerns=(_concern("HIGH"),)))
        self.assertTrue(ok)
        self.assertEqual(reason, "ok")

    def test_passes_with_critical_concern(self):
        ok, reason = passes(_result(concerns=(_concern("CRITICAL"),)))
        self.assertTrue(ok)
        self.assertEqual(reason, "ok")

    def test_rejects_low_confidence(self):
        ok, reason = passes(_result(confidence=0.65, concerns=(_concern("HIGH"),)))
        self.assertFalse(ok)
        self.assertIn("0.65", reason)

    def test_rejects_no_high_concerns(self):
        # Only LOW/MEDIUM — not worth posting
        ok, reason = passes(_result(confidence=0.80, concerns=(_concern("MEDIUM"),)))
        self.assertFalse(ok)
        self.assertIn("no HIGH or CRITICAL", reason)

    def test_rejects_only_low_concerns(self):
        ok, reason = passes(_result(confidence=0.80, concerns=(_concern("LOW"),)))
        self.assertFalse(ok)
        self.assertIn("no HIGH or CRITICAL", reason)

    def test_rejects_no_concerns_at_all_when_not_approve(self):
        ok, reason = passes(_result(decision="COMMENT", confidence=0.90, concerns=()))
        self.assertFalse(ok)
        self.assertIn("no HIGH or CRITICAL", reason)

    def test_passes_approve_with_no_concerns(self):
        ok, reason = passes(_result(decision="APPROVE", confidence=0.90, concerns=()))
        self.assertTrue(ok)
        self.assertEqual(reason, "ok")

    def test_rejects_approve_below_confidence(self):
        ok, _ = passes(_result(decision="APPROVE", confidence=0.60, concerns=()))
        self.assertFalse(ok)

    def test_passes_at_exact_confidence_threshold(self):
        ok, _ = passes(_result(confidence=0.70, concerns=(_concern("HIGH"),)))
        self.assertTrue(ok)

    def test_rejects_just_below_threshold(self):
        ok, _ = passes(_result(confidence=0.699, concerns=(_concern("HIGH"),)))
        self.assertFalse(ok)


if __name__ == "__main__":
    unittest.main()
