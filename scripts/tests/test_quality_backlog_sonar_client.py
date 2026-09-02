"""Tests for quality_backlog.sonar_client."""
import os
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from quality_backlog.sonar_client import SonarClient


class TestQualityBacklogSonarClient(unittest.TestCase):

    def test_fetch_issues_stops_on_empty_page_when_total_is_inconsistent(self):
        client = SonarClient("http://sonar", "token", "crypto")
        client._request = mock.Mock(
            side_effect=[
                {
                    "total": 1000,
                    "issues": [
                        {
                            "key": "issue-1",
                            "rule": "java:S2259",
                            "type": "BUG",
                            "severity": "MAJOR",
                            "component": "crypto:monitor-app/src/App.java",
                            "line": 42,
                            "message": "Possible null dereference",
                            "status": "OPEN",
                        },
                    ],
                },
                {"total": 1000, "issues": []},
            ],
        )

        issues = client.fetch_issues()

        self.assertEqual(1, len(issues))
        self.assertEqual(2, client._request.call_count)

    def test_fetch_hotspots_stops_on_empty_page_when_total_is_inconsistent(self):
        client = SonarClient("http://sonar", "token", "crypto")
        client._request = mock.Mock(
            side_effect=[
                {
                    "paging": {"total": 1000},
                    "hotspots": [
                        {
                            "key": "hotspot-1",
                            "ruleKey": "java:S2076",
                            "vulnerabilityProbability": "HIGH",
                            "component": "crypto:monitor-app/src/App.java",
                            "line": 42,
                            "message": "Review this use of dynamic code",
                            "status": "TO_REVIEW",
                        },
                    ],
                },
                {"paging": {"total": 1000}, "hotspots": []},
            ],
        )

        issues = client.fetch_hotspots()

        self.assertEqual(1, len(issues))
        self.assertEqual(2, client._request.call_count)


if __name__ == "__main__":
    unittest.main()
