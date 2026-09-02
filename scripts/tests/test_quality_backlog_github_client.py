"""Tests for quality_backlog.github_client."""
import io
import os
import sys
import unittest
import urllib.error
from unittest import mock

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from quality_backlog.github_client import (
    GitHubError,
    _request,
    add_comment,
    create_issue,
    ensure_labels,
    find_existing_issue,
)


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

    def test_find_existing_issue_scans_later_pages(self):
        first_page = [{"number": index, "body": "no marker"} for index in range(100)]
        second_page = [{"number": 101, "body": "<!-- quality-backlog-fingerprint: abc123 -->"}]
        with mock.patch(
            "quality_backlog.github_client._request",
            side_effect=[first_page, second_page],
        ):
            self.assertEqual(101, find_existing_issue("owner/repo", "abc123"))

    def test_find_existing_issue_stops_after_exact_full_page_then_empty_page(self):
        first_page = [{"number": index, "body": "no marker"} for index in range(100)]
        with mock.patch(
            "quality_backlog.github_client._request",
            side_effect=[first_page, []],
        ) as request:
            self.assertIsNone(find_existing_issue("owner/repo", "abc123"))
            self.assertEqual(2, request.call_count)

    def test_find_existing_issue_raises_when_issue_listing_fails(self):
        with mock.patch("quality_backlog.github_client._request", return_value=None):
            with self.assertRaises(GitHubError):
                find_existing_issue("owner/repo", "abc123")

    def test_ensure_labels_returns_false_when_create_fails(self):
        with mock.patch("quality_backlog.github_client._request", side_effect=[[], None]):
            self.assertFalse(ensure_labels(["quality-backlog"], "owner/repo"))

    def test_create_issue_returns_issue_number(self):
        with mock.patch("quality_backlog.github_client._request", return_value={"number": 42}):
            self.assertEqual(
                42,
                create_issue(
                    "owner/repo",
                    "Fix quality finding",
                    "Body",
                    ["quality-backlog", "source:sonarqube"],
                ),
            )

    def test_add_comment_returns_true_on_success(self):
        with mock.patch("quality_backlog.github_client._request", return_value={"id": 123}):
            self.assertTrue(add_comment("owner/repo", 42, "Still present"))

    def test_http_error_log_omits_response_body(self):
        error = urllib.error.HTTPError(
            url="https://api.github.com/repos/owner/repo/issues",
            code=500,
            msg="server error",
            hdrs=None,
            fp=io.BytesIO(b"token=super-secret-value"),
        )
        with mock.patch.dict(os.environ, {"GH_TOKEN": "gh-token"}):
            with mock.patch("urllib.request.urlopen", side_effect=error):
                with mock.patch("builtins.print") as print_mock:
                    self.assertIsNone(_request("GET", "/repos/owner/repo/issues"))
        error.close()
        printed = "\n".join(call.args[0] for call in print_mock.call_args_list)
        self.assertIn("HTTP 500", printed)
        self.assertNotIn("super-secret-value", printed)


if __name__ == "__main__":
    unittest.main()
