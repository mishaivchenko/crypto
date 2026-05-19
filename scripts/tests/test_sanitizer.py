"""Tests for the sanitizer module."""
import sys
import os
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "error_monitor"))

from sanitizer import sanitize


class TestSanitizer(unittest.TestCase):

    def test_masks_bearer_token(self):
        line = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.sig"
        result = sanitize(line)
        self.assertNotIn("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9", result)
        self.assertIn("***", result)
        self.assertIn("Bearer", result)

    def test_masks_api_key_value(self):
        line = "Connecting with api_key=supersecretkey123 to exchange"
        result = sanitize(line)
        self.assertNotIn("supersecretkey123", result)
        self.assertIn("***", result)
        # The field name should still be present
        self.assertIn("api_key", result)

    def test_masks_telegram_bot_token(self):
        # Telegram bot token format: 8-10 digit ID colon 35 alphanum/underscore/dash chars
        token_secret = "ABCDefghIJKLmnopQRSTuvwxyz123456789"  # exactly 35 chars
        token = f"123456789:{token_secret}"
        line = f"Bot token: {token}"
        result = sanitize(line)
        self.assertNotIn(token, result)
        self.assertIn("***", result)

    def test_masks_github_ghp_token(self):
        line = f"GITHUB_TOKEN=ghp_{'A' * 36} was used"
        result = sanitize(line)
        self.assertNotIn("ghp_" + "A" * 36, result)
        self.assertIn("ghp_***", result)

    def test_masks_url_credentials(self):
        line = "Connecting to https://admin:s3cr3t@db.internal.example.com/path"
        result = sanitize(line)
        self.assertNotIn("admin:s3cr3t", result)
        self.assertIn("[REDACTED]://", result)

    def test_masks_password_field(self):
        line = "Login failed for user=alice password=hunter2 at /api/login"
        result = sanitize(line)
        self.assertNotIn("hunter2", result)
        self.assertIn("***", result)
        self.assertIn("password", result)

    def test_preserves_stack_traces(self):
        stack = (
            "java.lang.NullPointerException: null\n"
            "\tat com.example.MyService.process(MyService.java:42)\n"
            "\tat com.example.Main.main(Main.java:10)"
        )
        result = sanitize(stack)
        self.assertIn("NullPointerException", result)
        self.assertIn("com.example.MyService", result)
        self.assertIn("MyService.java:42", result)
        self.assertIn("Main.java:10", result)

    def test_preserves_log_level_keywords(self):
        line = "2024-01-02T10:00:00Z ERROR [main] Something went wrong WARN also here INFO note"
        result = sanitize(line)
        self.assertIn("ERROR", result)
        self.assertIn("WARN", result)
        self.assertIn("INFO", result)


if __name__ == "__main__":
    unittest.main()
