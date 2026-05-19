"""Tests for the analyzer module."""
import sys
import os
import json
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "error_monitor"))

from analyzer import parse_analysis_result


_VALID_RESULT = {
    "shouldCreateIssue": True,
    "severity": "HIGH",
    "confidence": 0.9,
    "category": "NETWORK_ERROR",
    "component": "engine-app",
    "fingerprint": "abc123def456",
    "title": "Engine cannot connect to exchange WebSocket",
    "summary": "Persistent WebSocket connection failures detected in engine-app.",
    "impact": "Order execution is blocked; no trades can be placed.",
    "evidence": ["ERROR WebSocket closed unexpectedly", "SocketException: Connection reset"],
    "suspectedCause": "Network instability or exchange-side rate limiting.",
    "recommendedFix": "Check firewall rules and implement exponential backoff.",
    "labels": ["area:websocket", "priority:high"],
}


class TestAnalyzer(unittest.TestCase):

    def test_valid_json_returns_dict_with_correct_fields(self):
        raw = json.dumps(_VALID_RESULT)
        result = parse_analysis_result(raw)
        self.assertIsNotNone(result)
        self.assertIsInstance(result, dict)
        self.assertTrue(result["shouldCreateIssue"])
        self.assertEqual(result["severity"], "HIGH")
        self.assertAlmostEqual(result["confidence"], 0.9)
        self.assertEqual(result["fingerprint"], "abc123def456")
        self.assertIn("ERROR WebSocket closed unexpectedly", result["evidence"])

    def test_invalid_json_returns_none(self):
        raw = "this is not json at all {broken"
        result = parse_analysis_result(raw)
        self.assertIsNone(result)

    def test_json_missing_required_fields_returns_none(self):
        # Remove the required 'fingerprint' field
        incomplete = dict(_VALID_RESULT)
        del incomplete["fingerprint"]
        raw = json.dumps(incomplete)
        result = parse_analysis_result(raw)
        self.assertIsNone(result)

    def test_json_wrapped_in_markdown_fences_parses_correctly(self):
        fenced = "```json\n" + json.dumps(_VALID_RESULT) + "\n```"
        result = parse_analysis_result(fenced)
        self.assertIsNotNone(result)
        self.assertEqual(result["title"], "Engine cannot connect to exchange WebSocket")
        self.assertTrue(result["shouldCreateIssue"])


if __name__ == "__main__":
    unittest.main()
