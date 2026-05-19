"""Tests for pr_review.collector — diff filtering and sanitization (no network calls)."""
import sys
import os
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from pr_review.collector import _filter_diff, _MAX_DIFF_BYTES, _MAX_FILES


class TestDiffFilter(unittest.TestCase):

    def _make_diff(self, filename: str, content: str = "+changed line") -> str:
        return f"diff --git a/{filename} b/{filename}\n--- a/{filename}\n+++ b/{filename}\n@@ -1 +1 @@\n{content}\n"

    def test_keeps_java_files(self):
        diff = self._make_diff("src/main/java/Foo.java")
        result = _filter_diff(diff)
        self.assertIn("Foo.java", result)

    def test_removes_lock_files(self):
        diff = self._make_diff("gradle.lockfile") + self._make_diff("package-lock.json")
        result = _filter_diff(diff)
        self.assertNotIn("gradle.lockfile", result)
        self.assertNotIn("package-lock.json", result)

    def test_removes_minified_js(self):
        diff = self._make_diff("static/app.min.js")
        result = _filter_diff(diff)
        self.assertNotIn("app.min.js", result)

    def test_removes_image_files(self):
        diff = self._make_diff("docs/diagram.png")
        result = _filter_diff(diff)
        self.assertNotIn("diagram.png", result)

    def test_keeps_mixed_diff_partially(self):
        java_diff = self._make_diff("Foo.java", "+new line")
        lock_diff = self._make_diff("package.lock", "+updated")
        combined = java_diff + lock_diff
        result = _filter_diff(combined)
        self.assertIn("Foo.java", result)
        self.assertNotIn("package.lock", result)

    def test_keeps_yml_files(self):
        diff = self._make_diff(".github/workflows/ci.yml")
        result = _filter_diff(diff)
        self.assertIn("ci.yml", result)

    def test_removes_gradlew_bat(self):
        diff = self._make_diff("gradlew.bat")
        result = _filter_diff(diff)
        self.assertNotIn("gradlew.bat", result)


class TestConstants(unittest.TestCase):

    def test_max_diff_bytes_is_reasonable(self):
        self.assertGreaterEqual(_MAX_DIFF_BYTES, 10_000)
        self.assertLessEqual(_MAX_DIFF_BYTES, 100_000)

    def test_max_files_is_reasonable(self):
        self.assertGreaterEqual(_MAX_FILES, 10)
        self.assertLessEqual(_MAX_FILES, 100)


if __name__ == "__main__":
    unittest.main()
