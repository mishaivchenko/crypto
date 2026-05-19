"""Tests for the extractor module."""
import sys
import os
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "error_monitor"))

from extractor import extract_error_blocks, MAX_BLOCKS


class TestExtractor(unittest.TestCase):

    def test_extracts_error_lines(self):
        logs = (
            "2024-01-01T10:00:00Z INFO  [main] Application started\n"
            "2024-01-01T10:00:01Z ERROR [main] Failed to connect to database\n"
            "2024-01-01T10:00:02Z INFO  [main] Retrying...\n"
        )
        blocks = extract_error_blocks("monitor", logs)
        self.assertGreater(len(blocks), 0)
        all_lines = "\n".join("\n".join(b.lines) for b in blocks)
        self.assertIn("Failed to connect to database", all_lines)

    def test_empty_logs_no_blocks(self):
        blocks = extract_error_blocks("engine", "")
        self.assertEqual(blocks, [])

    def test_info_only_logs_no_blocks(self):
        logs = (
            "2024-01-01T10:00:00Z INFO  [main] Starting up\n"
            "2024-01-01T10:00:01Z INFO  [main] Connected to DB\n"
            "2024-01-01T10:00:02Z DEBUG [main] Processing item 1\n"
        )
        blocks = extract_error_blocks("monitor", logs)
        self.assertEqual(blocks, [])

    def test_respects_max_blocks_limit(self):
        # Generate more than MAX_BLOCKS distinct errors
        lines = []
        for i in range(MAX_BLOCKS + 5):
            # Spread errors far apart so they form separate blocks
            lines.append(f"2024-01-01T10:{i:02d}:00Z INFO  [main] Normal log line {i}")
            for _ in range(60):  # enough gap to separate blocks
                lines.append(f"2024-01-01T10:{i:02d}:01Z INFO  [main] Filler line")
            lines.append(f"2024-01-01T10:{i:02d}:59Z ERROR [main] Distinct error number {i} occurred here")
        logs = "\n".join(lines)
        blocks = extract_error_blocks("engine", logs)
        self.assertLessEqual(len(blocks), MAX_BLOCKS)

    def test_includes_context_lines_around_error(self):
        before_lines = [f"2024-01-01T10:00:{i:02d}Z INFO  [main] Context line before {i}" for i in range(5)]
        error_line = "2024-01-01T10:00:10Z ERROR [main] Critical failure"
        after_lines = [f"2024-01-01T10:00:{i+11:02d}Z INFO  [main] Context line after {i}" for i in range(5)]
        logs = "\n".join(before_lines + [error_line] + after_lines)
        blocks = extract_error_blocks("monitor", logs)
        self.assertGreater(len(blocks), 0)
        all_lines = "\n".join("\n".join(b.lines) for b in blocks)
        # Should contain at least some context lines, not just the error
        self.assertIn("Context line before", all_lines)
        self.assertIn("Context line after", all_lines)


if __name__ == "__main__":
    unittest.main()
