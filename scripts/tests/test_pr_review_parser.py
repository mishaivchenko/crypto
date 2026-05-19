"""Tests for pr_review.parser module."""
import sys
import os
import json
import unittest

_SCRIPTS = os.path.join(os.path.dirname(__file__), "..")
sys.path.insert(0, _SCRIPTS)

from pr_review.parser import parse
from pr_review.models import ReviewResult


def _valid_payload(**overrides) -> str:
    base = {
        "reviewDecision": "COMMENT",
        "confidence": 0.75,
        "summary": "Minor issues found.",
        "riskLevel": "MEDIUM",
        "architectureConcerns": [],
        "correctnessConcerns": [],
        "concurrencyConcerns": [],
        "tradingRiskConcerns": [],
        "observabilityConcerns": [],
        "testConcerns": [],
        "positiveNotes": ["Good test coverage."],
    }
    base.update(overrides)
    return json.dumps(base)


class TestPrReviewParser(unittest.TestCase):

    def test_parses_valid_response(self):
        result = parse(_valid_payload())
        self.assertIsNotNone(result)
        self.assertIsInstance(result, ReviewResult)
        self.assertEqual(result.review_decision, "COMMENT")
        self.assertAlmostEqual(result.confidence, 0.75)
        self.assertEqual(result.risk_level, "MEDIUM")

    def test_strips_markdown_fences(self):
        body = "```json\n" + _valid_payload() + "\n```"
        result = parse(body)
        self.assertIsNotNone(result)

    def test_returns_none_for_empty_input(self):
        self.assertIsNone(parse(""))
        self.assertIsNone(parse(None))

    def test_returns_none_for_invalid_json(self):
        self.assertIsNone(parse("not json at all"))

    def test_returns_none_for_missing_required_fields(self):
        payload = json.dumps({"reviewDecision": "COMMENT"})
        self.assertIsNone(parse(payload))

    def test_normalizes_unknown_decision(self):
        result = parse(_valid_payload(reviewDecision="WEIRD"))
        self.assertIsNotNone(result)
        self.assertEqual(result.review_decision, "COMMENT")

    def test_normalizes_unknown_risk_level(self):
        result = parse(_valid_payload(riskLevel="EXTREME"))
        self.assertIsNotNone(result)
        self.assertEqual(result.risk_level, "LOW")

    def test_parses_concerns_correctly(self):
        concerns = [{"severity": "HIGH", "file": "Foo.java", "lineHint": 42,
                     "category": "BUG", "message": "NPE risk", "recommendation": "Add null check"}]
        result = parse(_valid_payload(correctnessConcerns=concerns))
        self.assertEqual(len(result.correctness_concerns), 1)
        c = result.correctness_concerns[0]
        self.assertEqual(c.severity, "HIGH")
        self.assertEqual(c.file, "Foo.java")
        self.assertEqual(c.line_hint, 42)

    def test_skips_invalid_concern_items(self):
        result = parse(_valid_payload(architectureConcerns=["not a dict", None, 123]))
        self.assertEqual(len(result.architecture_concerns), 0)

    def test_handles_missing_positive_notes(self):
        payload = json.loads(_valid_payload())
        del payload["positiveNotes"]
        result = parse(json.dumps(payload))
        self.assertIsNotNone(result)
        self.assertEqual(result.positive_notes, ())

    def test_all_concerns_aggregates_all_categories(self):
        concern = {"severity": "MEDIUM", "file": "A.java", "lineHint": 1,
                   "category": "UNKNOWN", "message": "msg", "recommendation": "fix"}
        result = parse(_valid_payload(
            architectureConcerns=[concern],
            correctnessConcerns=[concern],
        ))
        self.assertEqual(len(result.all_concerns()), 2)


class TestPrReviewParserDecisions(unittest.TestCase):

    def test_approve_decision_preserved(self):
        result = parse(_valid_payload(reviewDecision="APPROVE"))
        self.assertEqual(result.review_decision, "APPROVE")

    def test_request_changes_preserved(self):
        result = parse(_valid_payload(reviewDecision="REQUEST_CHANGES"))
        self.assertEqual(result.review_decision, "REQUEST_CHANGES")


if __name__ == "__main__":
    unittest.main()
