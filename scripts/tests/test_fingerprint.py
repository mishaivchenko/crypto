"""Tests for the fingerprint module."""
import sys
import os
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "error_monitor"))

from fingerprint import (
    generate_fingerprint,
    extract_exception_class,
    normalize_for_fingerprint,
)


class TestFingerprint(unittest.TestCase):

    def test_same_error_different_timestamps_same_fingerprint(self):
        message = "Connection refused to jdbc:sqlite:/data/app.db at 2024-01-01T10:00:00Z"
        message2 = "Connection refused to jdbc:sqlite:/data/app.db at 2024-06-15T22:30:00Z"
        fp1 = generate_fingerprint("monitor-app", "SocketException", message)
        fp2 = generate_fingerprint("monitor-app", "SocketException", message2)
        self.assertEqual(fp1, fp2)

    def test_different_components_different_fingerprint(self):
        message = "Connection refused"
        fp1 = generate_fingerprint("engine-app", "IOException", message)
        fp2 = generate_fingerprint("monitor-app", "IOException", message)
        self.assertNotEqual(fp1, fp2)

    def test_fingerprint_is_deterministic(self):
        fp1 = generate_fingerprint("engine-app", "NullPointerException", "null pointer at line 42")
        fp2 = generate_fingerprint("engine-app", "NullPointerException", "null pointer at line 42")
        self.assertEqual(fp1, fp2)
        self.assertEqual(len(fp1), 16)

    def test_normalize_removes_timestamps(self):
        text = "Error at 2024-01-01T10:00:00Z in service"
        normalized = normalize_for_fingerprint(text)
        self.assertNotIn("2024-01-01", normalized)
        self.assertNotIn("10:00:00", normalized)

    def test_normalize_removes_uuids(self):
        text = "Request id=550e8400-e29b-41d4-a716-446655440000 failed"
        normalized = normalize_for_fingerprint(text)
        self.assertNotIn("550e8400-e29b-41d4-a716-446655440000", normalized)

    def test_extract_exception_class_finds_exception(self):
        lines = [
            "2024-01-01T10:00:00Z ERROR [main] Unhandled exception",
            "Caused by: com.example.OrderSubmissionException: order rejected by venue",
            "\tat com.example.EngineService.submit(EngineService.java:88)",
        ]
        exc = extract_exception_class(lines)
        self.assertEqual(exc, "OrderSubmissionException")

    def test_extract_exception_class_returns_unknown_when_not_found(self):
        lines = [
            "2024-01-01T10:00:00Z ERROR [main] Something failed",
            "No stack trace available",
        ]
        exc = extract_exception_class(lines)
        self.assertEqual(exc, "UnknownError")


if __name__ == "__main__":
    unittest.main()
