"""Tests for pr_review.decision_policy module."""
import sys
import os
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from pr_review.models import Concern, ReviewResult
from pr_review.decision_policy import enforce


def _concern(severity: str) -> Concern:
    return Concern(severity=severity, file="Foo.java", line_hint=1,
                   category="BUG", message="msg", recommendation="fix")


def _result(concerns: tuple[Concern, ...], confidence: float = 0.8) -> ReviewResult:
    return ReviewResult(
        review_decision="COMMENT",
        confidence=confidence,
        summary="s",
        risk_level="MEDIUM",
        architecture_concerns=(),
        correctness_concerns=concerns,
        concurrency_concerns=(),
        trading_risk_concerns=(),
        observability_concerns=(),
        test_concerns=(),
        positive_notes=(),
    )


class TestDecisionPolicy(unittest.TestCase):

    def test_approve_when_no_concerns(self):
        self.assertEqual(enforce(_result(())), "APPROVE")

    def test_comment_for_medium_concern(self):
        self.assertEqual(enforce(_result((_concern("MEDIUM"),))), "COMMENT")

    def test_comment_for_low_concern(self):
        self.assertEqual(enforce(_result((_concern("LOW"),))), "COMMENT")

    def test_request_changes_for_high_with_sufficient_confidence(self):
        self.assertEqual(enforce(_result((_concern("HIGH"),), confidence=0.80)), "REQUEST_CHANGES")

    def test_request_changes_for_critical(self):
        self.assertEqual(enforce(_result((_concern("CRITICAL"),), confidence=0.90)), "REQUEST_CHANGES")

    def test_comment_for_high_below_confidence_threshold(self):
        self.assertEqual(enforce(_result((_concern("HIGH"),), confidence=0.70)), "COMMENT")

    def test_request_changes_at_exact_confidence_threshold(self):
        self.assertEqual(enforce(_result((_concern("HIGH"),), confidence=0.75)), "REQUEST_CHANGES")

    def test_comment_just_below_confidence_threshold(self):
        self.assertEqual(enforce(_result((_concern("HIGH"),), confidence=0.74)), "COMMENT")


if __name__ == "__main__":
    unittest.main()
