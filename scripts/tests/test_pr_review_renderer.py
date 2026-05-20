"""Tests for pr_review.renderer module."""
import sys
import os
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from pr_review.models import Concern, ReviewResult
from pr_review.renderer import (
    render_summary,
    render_inline_comment,
    select_inline_concerns,
    concern_fingerprint,
    _SUMMARY_MARKER,
)


def _concern(severity: str = "HIGH", file: str = "Foo.java", line_hint: int = 10,
             category: str = "BUG", message: str = "NPE risk") -> Concern:
    return Concern(severity=severity, file=file, line_hint=line_hint,
                   category=category, message=message, recommendation="Add null check")


def _result(concerns: tuple[Concern, ...] = (), decision: str = "COMMENT",
            confidence: float = 0.8) -> ReviewResult:
    return ReviewResult(
        review_decision=decision,
        confidence=confidence,
        summary="Minor issues.",
        risk_level="MEDIUM",
        architecture_concerns=(),
        correctness_concerns=concerns,
        concurrency_concerns=(),
        trading_risk_concerns=(),
        observability_concerns=(),
        test_concerns=(),
        positive_notes=("Good job on tests.",),
    )


class TestRenderSummary(unittest.TestCase):

    def test_contains_summary_marker(self):
        body = render_summary(_result(), "COMMENT", False)
        self.assertIn(_SUMMARY_MARKER, body)

    def test_contains_decision(self):
        body = render_summary(_result(), "REQUEST_CHANGES", False)
        self.assertIn("ПРИЕДУ", body)

    def test_contains_confidence(self):
        body = render_summary(_result(confidence=0.82), "COMMENT", False)
        self.assertIn("82%", body)

    def test_shows_truncation_warning_when_truncated(self):
        body = render_summary(_result(), "COMMENT", True)
        self.assertIn("обрезан", body.lower())

    def test_no_truncation_warning_when_not_truncated(self):
        body = render_summary(_result(), "COMMENT", False)
        self.assertNotIn("обрезан", body.lower())

    def test_lists_top_concerns(self):
        concerns = (_concern("HIGH", message="Critical NPE"),)
        body = render_summary(_result(concerns=concerns), "COMMENT", False)
        self.assertIn("Critical NPE", body)

    def test_shows_positive_notes(self):
        body = render_summary(_result(), "APPROVE", False)
        self.assertIn("Good job on tests.", body)

    def test_approve_emoji_present(self):
        body = render_summary(_result(decision="APPROVE"), "APPROVE", False)
        self.assertIn("🀄", body)

    def test_request_changes_emoji_present(self):
        body = render_summary(_result(), "REQUEST_CHANGES", False)
        self.assertIn("😤", body)


class TestRenderInlineComment(unittest.TestCase):

    def test_contains_fingerprint_marker(self):
        c = _concern()
        body = render_inline_comment(c)
        self.assertIn("<!-- ai-pr-review-fingerprint:", body)

    def test_contains_severity(self):
        c = _concern(severity="CRITICAL")
        body = render_inline_comment(c)
        self.assertIn("CRITICAL", body)

    def test_contains_recommendation(self):
        c = _concern()
        body = render_inline_comment(c)
        self.assertIn("Add null check", body)

    def test_fingerprint_in_body_matches_concern_fingerprint(self):
        c = _concern()
        expected_fp = concern_fingerprint(c)
        body = render_inline_comment(c)
        self.assertIn(expected_fp, body)


class TestSelectInlineConcerns(unittest.TestCase):

    def test_returns_empty_for_no_concerns(self):
        result = select_inline_concerns(_result())
        self.assertEqual(result, [])

    def test_excludes_concerns_without_line_hint(self):
        c = _concern(line_hint=0)
        r = ReviewResult(
            review_decision="COMMENT", confidence=0.8, summary="s", risk_level="MEDIUM",
            architecture_concerns=(), correctness_concerns=(c,), concurrency_concerns=(),
            trading_risk_concerns=(), observability_concerns=(), test_concerns=(),
            positive_notes=(),
        )
        result = select_inline_concerns(r)
        self.assertEqual(result, [])

    def test_limits_total_to_max(self):
        concerns = tuple(_concern(file=f"File{i}.java", line_hint=i + 1, message=f"msg{i}")
                         for i in range(20))
        r = ReviewResult(
            review_decision="COMMENT", confidence=0.8, summary="s", risk_level="HIGH",
            architecture_concerns=(), correctness_concerns=concerns, concurrency_concerns=(),
            trading_risk_concerns=(), observability_concerns=(), test_concerns=(),
            positive_notes=(),
        )
        result = select_inline_concerns(r)
        self.assertLessEqual(len(result), 10)

    def test_prioritizes_high_severity(self):
        low = _concern(severity="LOW", file="A.java", message="low1")
        high = _concern(severity="HIGH", file="B.java", message="high1")
        r = ReviewResult(
            review_decision="COMMENT", confidence=0.8, summary="s", risk_level="HIGH",
            architecture_concerns=(), correctness_concerns=(low, high), concurrency_concerns=(),
            trading_risk_concerns=(), observability_concerns=(), test_concerns=(),
            positive_notes=(),
        )
        result = select_inline_concerns(r)
        self.assertEqual(result[0].severity, "HIGH")


if __name__ == "__main__":
    unittest.main()
