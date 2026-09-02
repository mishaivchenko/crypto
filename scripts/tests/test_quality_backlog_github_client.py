"""Tests for quality_backlog.github_client."""
import os
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from quality_backlog.github_client import find_existing_issue


class TestQualityBacklogGithubClient(unittest.TestCase):

    def test_find_existing_issue_by_fingerprint_marker(self):
        with mock.patch(
            "quality_backlog.github_client._request",
            return_value=[
                {"number": 10, "body": "no marker"},
                {"number": 11, "body": "<!-- quality-backlog-fingerprint: abc123 -->"},
            ],
        ):
            self.assertEqual(11, find_existing_issue("owner/repo", "abc123"))

    def test_find_existing_issue_returns_none_for_missing_marker(self):
        with mock.patch(
            "quality_backlog.github_client._request",
            return_value=[{"number": 10, "body": "no marker"}],
        ):
            self.assertIsNone(find_existing_issue("owner/repo", "abc123"))


if __name__ == "__main__":
    unittest.main()

