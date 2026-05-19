"""Tests for pr_review.deduplicator module."""
import sys
import os
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from pr_review.models import Concern
from pr_review.deduplicator import (
    has_summary_comment,
    already_posted_fingerprints,
    filter_new_concerns,
)
from pr_review.renderer import concern_fingerprint, _SUMMARY_MARKER


def _concern(file: str = "A.java", category: str = "BUG", message: str = "NPE") -> Concern:
    return Concern(severity="HIGH", file=file, line_hint=10,
                   category=category, message=message, recommendation="fix")


class TestDeduplicator(unittest.TestCase):

    def test_detects_summary_marker(self):
        comments = [{"body": f"{_SUMMARY_MARKER}\n## Review"}]
        self.assertTrue(has_summary_comment(comments))

    def test_no_summary_marker(self):
        comments = [{"body": "Just a regular comment"}]
        self.assertFalse(has_summary_comment(comments))

    def test_empty_comments(self):
        self.assertFalse(has_summary_comment([]))

    def test_extracts_fingerprints_from_comments(self):
        c = _concern()
        fp = concern_fingerprint(c)
        comments = [{"body": f"<!-- ai-pr-review-fingerprint: {fp} -->\nSome concern"}]
        fps = already_posted_fingerprints(comments)
        self.assertIn(fp, fps)

    def test_no_fingerprints_in_plain_comments(self):
        comments = [{"body": "Just a plain comment without fingerprint"}]
        fps = already_posted_fingerprints(comments)
        self.assertEqual(len(fps), 0)

    def test_filter_removes_already_posted(self):
        c = _concern()
        fp = concern_fingerprint(c)
        posted = frozenset([fp])
        result = filter_new_concerns([c], posted)
        self.assertEqual(result, [])

    def test_filter_keeps_new_concerns(self):
        c1 = _concern(file="A.java", message="Bug A")
        c2 = _concern(file="B.java", message="Bug B")
        fp1 = concern_fingerprint(c1)
        posted = frozenset([fp1])
        result = filter_new_concerns([c1, c2], posted)
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0].file, "B.java")

    def test_filter_empty_posted_keeps_all(self):
        concerns = [_concern(message="A"), _concern(message="B")]
        result = filter_new_concerns(concerns, frozenset())
        self.assertEqual(len(result), 2)

    def test_fingerprint_is_stable(self):
        c = _concern()
        fp1 = concern_fingerprint(c)
        fp2 = concern_fingerprint(c)
        self.assertEqual(fp1, fp2)

    def test_different_concerns_different_fingerprints(self):
        c1 = _concern(file="A.java")
        c2 = _concern(file="B.java")
        self.assertNotEqual(concern_fingerprint(c1), concern_fingerprint(c2))


if __name__ == "__main__":
    unittest.main()
