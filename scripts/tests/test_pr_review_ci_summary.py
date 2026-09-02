"""Tests for pr_review.ci_summary."""
import json
import os
import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from pr_review.ci_summary import (
    build_summary,
    dumps_summary,
    extract_latest_from_comments,
    hidden_snapshot,
    load_summary,
)
from pr_review.models import CheckResult, CiSummary, QualityMetric


class TestCiSummary(unittest.TestCase):

    def test_load_round_trips_summary(self):
        summary = CiSummary(
            run_id="123",
            run_url="https://github.example/run/123",
            conclusion="success",
            checks=(CheckResult("Build & Test", "completed", "success", 240),),
            metrics=(QualityMetric("spotbugs.bugs", "SpotBugs bugs", 0, "", True),),
        )

        self.assertEqual(summary, load_summary(dumps_summary(summary)))

    def test_extract_latest_from_comments_reads_hidden_snapshot(self):
        older = CiSummary("1", "", "success", metrics=(QualityMetric("pmd.violations", "PMD", 2, "", True),))
        newer = CiSummary("2", "", "success", metrics=(QualityMetric("pmd.violations", "PMD", 1, "", True),))
        comments = [
            {"body": hidden_snapshot(older)},
            {"body": "plain comment"},
            {"body": hidden_snapshot(newer)},
        ]

        self.assertEqual(newer, extract_latest_from_comments(comments))

    def test_build_summary_collects_checks_and_quality_metrics(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            run_json = root / "run.json"
            run_json.write_text(
                json.dumps(
                    {
                        "jobs": [
                            {
                                "name": "Build & Test",
                                "status": "completed",
                                "conclusion": "success",
                                "startedAt": "2026-09-03T09:00:00Z",
                                "completedAt": "2026-09-03T09:04:00Z",
                            }
                        ]
                    }
                ),
                encoding="utf-8",
            )
            pmd = root / "quality-reports" / "app" / "build" / "reports" / "pmd" / "main.xml"
            pmd.parent.mkdir(parents=True)
            pmd.write_text("<pmd><file><violation/><violation/></file></pmd>", encoding="utf-8")
            spotbugs = root / "quality-reports" / "app" / "build" / "reports" / "spotbugs" / "main.xml"
            spotbugs.parent.mkdir(parents=True)
            spotbugs.write_text("<BugCollection><BugInstance/></BugCollection>", encoding="utf-8")
            jacoco = root / "build" / "reports" / "jacoco" / "test" / "jacocoTestReport.xml"
            jacoco.parent.mkdir(parents=True)
            jacoco.write_text(
                (
                    "<report>"
                    "<counter type='LINE' missed='1' covered='3'/>"
                    "<counter type='BRANCH' missed='2' covered='2'/>"
                    "</report>"
                ),
                encoding="utf-8",
            )
            sonar = root / "sonarqube-log" / "build" / "quality-backlog" / "quality-backlog.json"
            sonar.parent.mkdir(parents=True)
            sonar.write_text(
                json.dumps(
                    {
                        "groups": [
                            {"category": "bug", "count": 2},
                            {"category": "security", "count": 1},
                            {"category": "maintainability", "count": 3},
                        ]
                    }
                ),
                encoding="utf-8",
            )

            summary = build_summary(run_json, root, "123", "https://github.example/run/123", "success")

        metric_values = {metric.key: metric.value for metric in summary.metrics}
        self.assertEqual("Build & Test", summary.checks[0].name)
        self.assertEqual(240, summary.checks[0].duration_seconds)
        self.assertEqual(2, metric_values["pmd.violations"])
        self.assertEqual(1, metric_values["spotbugs.bugs"])
        self.assertEqual(75.0, metric_values["jacoco.line"])
        self.assertEqual(50.0, metric_values["jacoco.branch"])
        self.assertEqual(6, metric_values["sonar.findings"])
        self.assertEqual(2, metric_values["sonar.bugs"])
        self.assertEqual(1, metric_values["sonar.security"])
        self.assertEqual(3, metric_values["sonar.maintainability"])


if __name__ == "__main__":
    unittest.main()
