"""Tests for quality_backlog.renderer."""
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from quality_backlog.models import FindingGroup, SonarIssue
from quality_backlog.renderer import marker_for, render_issue_body, render_issue_title, render_report


class TestQualityBacklogRenderer(unittest.TestCase):

    def test_issue_body_contains_fingerprint_marker(self):
        group = FindingGroup(
            fingerprint="abc123",
            component="monitor-app",
            category="bug",
            theme="Avoid null dereference",
            rule="java:S2259",
            priority="medium",
            severity="MAJOR",
            issue_type="BUG",
            issues=[
                SonarIssue(
                    key="sonar-key",
                    rule="java:S2259",
                    rule_name="Avoid null dereference",
                    issue_type="BUG",
                    severity="MAJOR",
                    component="monitor-app",
                    path="monitor-app/src/main/java/App.java",
                    line=42,
                    message="Possible null dereference",
                    status="OPEN",
                    url="http://sonar/issue",
                ),
            ],
        )

        body = render_issue_body(group)

        self.assertIn(marker_for("abc123"), body)
        self.assertIn("monitor-app/src/main/java/App.java:42", body)
        self.assertIn("Possible null dereference", body)

    def test_title_is_capped(self):
        group = FindingGroup(
            fingerprint="abc123",
            component="monitor-app",
            category="maintainability",
            theme="x" * 120,
            rule="java:S100",
            priority="low",
            severity="MINOR",
            issue_type="CODE_SMELL",
        )

        self.assertLessEqual(len(render_issue_title(group)), 80)

    def test_report_includes_mode_and_counts(self):
        report = render_report([], [], [], "dry-run")

        self.assertIn("Mode: `dry-run`", report)
        self.assertIn("Groups found: 0", report)


if __name__ == "__main__":
    unittest.main()

