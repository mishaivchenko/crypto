"""Tests for quality_backlog.main."""
import os
import sys
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


if __name__ == "__main__":
    unittest.main()
