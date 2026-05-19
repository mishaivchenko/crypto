"""Tests for the quality_gate module."""
import sys
import os
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "error_monitor"))

from quality_gate import passes


def _base_result(**overrides) -> dict:
    """Return a valid base result dict with optional overrides."""
    base = {
        "shouldCreateIssue": True,
        "severity": "HIGH",
        "confidence": 0.85,
        "fingerprint": "abc123def456",
        "evidence": ["ERROR: something failed"],
        "recommendedFix": "Restart the service and check logs.",
    }
    base.update(overrides)
    return base


class TestQualityGate(unittest.TestCase):

    def test_passes_for_high_severity_high_confidence(self):
        ok, reason = passes(_base_result(severity="HIGH", confidence=0.85))
        self.assertTrue(ok)
        self.assertEqual(reason, "ok")

    def test_rejects_should_create_issue_false(self):
        ok, reason = passes(_base_result(shouldCreateIssue=False))
        self.assertFalse(ok)
        self.assertIn("shouldCreateIssue", reason)

    def test_rejects_low_severity(self):
        ok, reason = passes(_base_result(severity="LOW"))
        self.assertFalse(ok)
        self.assertIn("LOW", reason)

    def test_rejects_confidence_below_threshold(self):
        ok, reason = passes(_base_result(confidence=0.2))
        self.assertFalse(ok)
        self.assertIn("0.20", reason)

    def test_passes_confidence_at_new_threshold(self):
        ok, reason = passes(_base_result(confidence=0.4))
        self.assertTrue(ok)
        self.assertEqual(reason, "ok")

    def test_rejects_empty_fingerprint(self):
        ok, reason = passes(_base_result(fingerprint=""))
        self.assertFalse(ok)
        self.assertIn("fingerprint", reason)

    def test_rejects_empty_evidence_list(self):
        ok, reason = passes(_base_result(evidence=[]))
        self.assertFalse(ok)
        self.assertIn("evidence", reason)

    def test_rejects_empty_recommended_fix(self):
        ok, reason = passes(_base_result(recommendedFix=""))
        self.assertFalse(ok)
        self.assertIn("recommendedFix", reason)

    def test_passes_medium_severity(self):
        ok, reason = passes(_base_result(severity="MEDIUM", confidence=0.7))
        self.assertTrue(ok)
        self.assertEqual(reason, "ok")

    def test_passes_critical_severity(self):
        ok, reason = passes(_base_result(severity="CRITICAL", confidence=0.95))
        self.assertTrue(ok)
        self.assertEqual(reason, "ok")


if __name__ == "__main__":
    unittest.main()
