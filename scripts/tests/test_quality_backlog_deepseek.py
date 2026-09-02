"""Tests for quality_backlog.deepseek."""
import os
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from quality_backlog.deepseek import summarize
from quality_backlog.models import FindingGroup


class _FakeResponse:
    def __init__(self, body):
        self._body = body.encode("utf-8")

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        return False

    def read(self):
        return self._body


class TestQualityBacklogDeepSeek(unittest.TestCase):

    def test_disabled_summary_does_not_call_api(self):
        group = FindingGroup(
            fingerprint="abc123",
            component="monitor-app",
            category="bug",
            theme="Avoid null dereference",
            rule="java:S2259",
            priority="medium",
            severity="MAJOR",
            issue_type="BUG",
        )
        with mock.patch("quality_backlog.deepseek.urllib.request.urlopen") as urlopen:
            self.assertIsNone(summarize(group, enabled=False))
            urlopen.assert_not_called()

    @mock.patch.dict(os.environ, {"DEEPSEEK_API_KEY": "deepseek-token"}, clear=True)
    def test_api_failure_falls_back_to_none(self):
        group = FindingGroup(
            fingerprint="abc123",
            component="monitor-app",
            category="bug",
            theme="Avoid null dereference",
            rule="java:S2259",
            priority="medium",
            severity="MAJOR",
            issue_type="BUG",
        )
        with mock.patch("quality_backlog.deepseek.urllib.request.urlopen", side_effect=OSError("boom")):
            self.assertIsNone(summarize(group, enabled=True))

    @mock.patch.dict(os.environ, {"DEEPSEEK_API_KEY": "deepseek-token"}, clear=True)
    def test_valid_api_response_returns_summary(self):
        group = FindingGroup(
            fingerprint="abc123",
            component="monitor-app",
            category="bug",
            theme="Avoid null dereference",
            rule="java:S2259",
            priority="medium",
            severity="MAJOR",
            issue_type="BUG",
        )
        body = (
            '{"choices":[{"message":{"content":'
            '"{\\"summary\\":\\"Fix null path\\",'
            '\\"impact\\":\\"Avoid runtime failure\\",'
            '\\"recommendedFix\\":\\"Add guard clause\\"}"}}]}'
        )
        with mock.patch(
            "quality_backlog.deepseek.urllib.request.urlopen",
            return_value=_FakeResponse(body),
        ):
            self.assertEqual(
                {
                    "summary": "Fix null path",
                    "impact": "Avoid runtime failure",
                    "recommendedFix": "Add guard clause",
                },
                summarize(group, enabled=True),
            )


if __name__ == "__main__":
    unittest.main()
