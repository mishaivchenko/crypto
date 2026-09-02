"""Tests for quality_backlog.triage."""
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from quality_backlog.models import SonarIssue, SonarMetric
from quality_backlog.triage import build_groups, labels_for, select_groups


def _issue(key, rule, issue_type, severity, path):
    return SonarIssue(
        key=key,
        rule=rule,
        rule_name="Avoid null dereference",
        issue_type=issue_type,
        severity=severity,
        component=path.split("/", 1)[0],
        path=path,
        line=12,
        message="Possible null dereference",
        status="OPEN",
        url="http://sonar/issue",
    )


class TestQualityBacklogTriage(unittest.TestCase):

    def test_groups_by_component_category_and_rule(self):
        groups = build_groups(
            [
                _issue("a", "java:S2259", "BUG", "MAJOR", "monitor-app/src/A.java"),
                _issue("b", "java:S2259", "BUG", "MAJOR", "monitor-app/src/B.java"),
            ],
            [],
        )

        self.assertEqual(1, len(groups))
        self.assertEqual(2, groups[0].count)
        self.assertEqual("bug", groups[0].category)
        self.assertEqual("medium", groups[0].priority)

    def test_security_findings_sort_before_maintainability(self):
        groups = build_groups(
            [
                _issue("a", "java:S100", "CODE_SMELL", "MINOR", "monitor-app/src/A.java"),
                _issue("b", "java:S2076", "VULNERABILITY", "CRITICAL", "monitor-app/src/B.java"),
            ],
            [],
        )

        self.assertEqual("security", groups[0].category)
        self.assertIn("security", labels_for(groups[0]))

    def test_metric_group_created_for_low_coverage(self):
        groups = build_groups([], [SonarMetric(key="coverage", value=43.5, raw_value="43.5")])

        self.assertEqual(1, len(groups))
        self.assertEqual("coverage", groups[0].category)
        self.assertEqual("metric:coverage", groups[0].rule)

    def test_select_groups_applies_limit(self):
        groups = build_groups(
            [
                _issue("a", "rule:a", "CODE_SMELL", "MINOR", "monitor-app/src/A.java"),
                _issue("b", "rule:b", "CODE_SMELL", "MINOR", "engine-app/src/B.java"),
            ],
            [],
        )

        self.assertEqual(1, len(select_groups(groups, 1)))


if __name__ == "__main__":
    unittest.main()
