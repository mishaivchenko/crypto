"""Tests for telegram_notifier module."""
import sys
import os
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "error_monitor"))

from telegram_notifier import _chunk, send_report

_MAX = 4000


class TestChunk(unittest.TestCase):

    def test_short_text_returns_single_chunk(self):
        text = "hello\nworld"
        chunks = _chunk(text)
        self.assertEqual(chunks, [text])

    def test_splits_at_boundary(self):
        # Two lines that together exceed _MAX, each individually fits
        line = "x" * 2500 + "\n"
        text = line * 2  # 5002 chars total — must produce 2 chunks
        chunks = _chunk(text)
        self.assertGreater(len(chunks), 1)
        for chunk in chunks:
            self.assertLessEqual(len(chunk), _MAX)

    def test_exact_boundary_is_single_chunk(self):
        text = "a" * _MAX
        chunks = _chunk(text)
        self.assertEqual(len(chunks), 1)

    def test_single_line_longer_than_max_is_truncated(self):
        text = "x" * (_MAX + 500)
        chunks = _chunk(text)
        self.assertEqual(len(chunks), 1)
        self.assertLessEqual(len(chunks[0]), _MAX)
        self.assertTrue(chunks[0].endswith("...") or chunks[0].endswith("...\n"))

    def test_all_chunks_within_limit(self):
        lines = [("line" * 100 + "\n") for _ in range(50)]  # each line ~404 chars
        text = "".join(lines)
        chunks = _chunk(text)
        for chunk in chunks:
            self.assertLessEqual(len(chunk), _MAX)

    def test_preserves_content_for_normal_text(self):
        text = "line1\nline2\nline3"
        chunks = _chunk(text)
        self.assertEqual("".join(chunks), text)


class TestSendReport(unittest.TestCase):

    def test_skips_when_no_token(self):
        # No env vars set — must not raise
        env_backup = {k: os.environ.pop(k, None) for k in ["TELEGRAM_BOT_TOKEN", "TELEGRAM_NOTIFICATION_CHAT_ID"]}
        try:
            send_report(
                stats={"issues_created": 0, "issues_updated": 0, "error_blocks_found": 0,
                       "logs_scanned": 0, "api_calls": 0, "api_errors": 0, "parse_errors": 0},
                issues_created=[], issues_updated=[], skipped=[], repo="owner/repo",
            )
        finally:
            for k, v in env_backup.items():
                if v is not None:
                    os.environ[k] = v

    def test_skips_when_no_chat_id(self):
        env_backup = {k: os.environ.pop(k, None) for k in ["TELEGRAM_BOT_TOKEN", "TELEGRAM_NOTIFICATION_CHAT_ID"]}
        try:
            os.environ["TELEGRAM_BOT_TOKEN"] = "fake-token"
            send_report(
                stats={"issues_created": 0, "issues_updated": 0, "error_blocks_found": 0,
                       "logs_scanned": 0, "api_calls": 0, "api_errors": 0, "parse_errors": 0},
                issues_created=[], issues_updated=[], skipped=[], repo="owner/repo",
            )
        finally:
            for k, v in env_backup.items():
                if v is not None:
                    os.environ[k] = v
            os.environ.pop("TELEGRAM_BOT_TOKEN", None)


if __name__ == "__main__":
    unittest.main()
