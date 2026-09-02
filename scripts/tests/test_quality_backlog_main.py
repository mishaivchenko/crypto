"""Tests for quality_backlog.main."""
import os
import pathlib
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from quality_backlog.main import main


class TestQualityBacklogMain(unittest.TestCase):

    @mock.patch.dict(os.environ, {"SONAR_TOKEN": "sonar-token"}, clear=True)
    def test_create_mode_requires_github_repository(self):
        self.assertEqual(1, main(["--mode", "create-issues"]))

    @mock.patch.dict(os.environ, {"SONAR_TOKEN": "sonar-token", "GITHUB_REPOSITORY": "owner/repo"}, clear=True)
    def test_create_mode_requires_github_token(self):
        self.assertEqual(1, main(["--mode", "create-issues"]))

    @mock.patch.dict(
        os.environ,
        {
            "SONAR_TOKEN": "sonar-token",
            "GITHUB_REPOSITORY": "owner/repo",
            "GH_TOKEN": "github-token",
        },
        clear=True,
    )
    @mock.patch("quality_backlog.main.github_client.create_issue", return_value=None)
    @mock.patch("quality_backlog.main.github_client.find_existing_issue", return_value=None)
    @mock.patch("quality_backlog.main.github_client.ensure_labels")
    @mock.patch("quality_backlog.main.deepseek.summarize", return_value=None)
    @mock.patch("quality_backlog.main.SonarClient")
    def test_create_mode_returns_failure_when_issue_creation_fails(
        self,
        sonar_client_class,
        _summarize,
        _ensure_labels,
        _find_existing_issue,
        _create_issue,
    ):
        sonar_client = sonar_client_class.return_value
        sonar_client.health.return_value = "UP"
        sonar_client.fetch_issues.return_value = [
            mock.Mock(
                key="sonar-key",
                rule="java:S2259",
                rule_name="Avoid null dereference",
                issue_type="BUG",
                severity="MAJOR",
                component="monitor-app",
                path="monitor-app/src/App.java",
                line=42,
                message="Possible null dereference",
                status="OPEN",
                url="http://sonar/issue",
            ),
        ]
        sonar_client.fetch_hotspots.return_value = []
        sonar_client.fetch_metrics.return_value = []

        self.assertEqual(1, main(["--mode", "create-issues", "--limit", "1"]))

    @mock.patch.dict(os.environ, {"SONAR_TOKEN": "sonar-token"}, clear=True)
    @mock.patch("quality_backlog.main.deepseek.summarize", return_value=None)
    @mock.patch("quality_backlog.main.SonarClient")
    def test_dry_run_writes_reports_without_github_token(self, sonar_client_class, _summarize):
        sonar_client = sonar_client_class.return_value
        sonar_client.health.return_value = "UP"
        sonar_client.fetch_issues.return_value = [
            mock.Mock(
                key="sonar-key",
                rule="java:S2259",
                rule_name="Avoid null dereference",
                issue_type="BUG",
                severity="MAJOR",
                component="monitor-app",
                path="monitor-app/src/App.java",
                line=42,
                message="Possible null dereference",
                status="OPEN",
                url="http://sonar/issue",
            ),
        ]
        sonar_client.fetch_hotspots.return_value = []
        sonar_client.fetch_metrics.return_value = []

        with tempfile.TemporaryDirectory() as tmpdir:
            code = main(["--mode", "dry-run", "--limit", "1", "--output-dir", tmpdir])
            self.assertTrue((pathlib.Path(tmpdir) / "quality-backlog.md").exists())
            self.assertTrue((pathlib.Path(tmpdir) / "quality-backlog.json").exists())

        self.assertEqual(0, code)


if __name__ == "__main__":
    unittest.main()
