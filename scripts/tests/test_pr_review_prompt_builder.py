"""Tests for pr_review.prompt_builder."""
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from pr_review.models import CheckResult, CiSummary, PullRequestContext, QualityMetric
from pr_review.prompt_builder import build


class TestPromptBuilder(unittest.TestCase):

    def test_includes_ci_context_as_github_actions_evidence(self):
        ctx = PullRequestContext(
            pr_number=187,
            repo="owner/repo",
            diff="diff --git a/build.gradle b/build.gradle\n+change\n",
            changed_files=("build.gradle",),
            diff_truncated=False,
            ci_context="Job results:\n- Build & Test: success\n- SonarQube: success",
        )

        system_prompt, user_prompt = build(ctx)

        self.assertIn("Treat CI CONTEXT as observed evidence from GitHub Actions", system_prompt)
        self.assertIn("GitHub Actions job results, logs, and analyzer output", user_prompt)
        self.assertIn("- SonarQube: success", user_prompt)

    def test_includes_authoritative_ci_summary_json(self):
        ctx = PullRequestContext(
            pr_number=189,
            repo="owner/repo",
            diff="diff --git a/scripts/foo.py b/scripts/foo.py\n+change\n",
            changed_files=("scripts/foo.py",),
            diff_truncated=False,
            ci_context="",
            ci_summary=CiSummary(
                run_id="123",
                run_url="https://github.example/run/123",
                conclusion="success",
                checks=(CheckResult("SonarQube", "completed", "success", 120),),
                metrics=(QualityMetric("pmd.violations", "PMD замечания", 0, "", True),),
            ),
        )

        system_prompt, user_prompt = build(ctx)

        self.assertIn("CI SUMMARY JSON as the authoritative", system_prompt)
        self.assertIn("Never report sanitized placeholders", system_prompt)
        self.assertIn("--- CI SUMMARY JSON", user_prompt)
        self.assertIn('"name":"SonarQube"', user_prompt)


if __name__ == "__main__":
    unittest.main()
