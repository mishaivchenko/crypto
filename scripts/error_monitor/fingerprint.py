"""Deterministic error fingerprinting for deduplication.

All regexes are compiled at module level.
"""
from __future__ import annotations

import hashlib
import re

# ---------------------------------------------------------------------------
# Compiled patterns (module-level)
# ---------------------------------------------------------------------------

# ISO 8601 timestamps and common log date/time formats
_RE_TIMESTAMP = re.compile(
    r"\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:[.,]\d+)?(?:Z|[+-]\d{2}:?\d{2})?"
)

# UUIDs: 8-4-4-4-12 hex groups
_RE_UUID = re.compile(
    r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
)

# Java object/memory addresses: @0x1a2b3c4d
_RE_HEX_ADDR = re.compile(r"@0x[0-9a-fA-F]+")

# Standalone numbers (e.g. port numbers, IDs, counts) — preserve word chars
_RE_NUMBERS = re.compile(r"\b\d+\b")

# Java Exception class in "Caused by:" lines
_RE_CAUSED_BY = re.compile(r"Caused by:\s+([\w.]+(?:Exception|Error))")

# General exception/error class name anywhere in a line
_RE_EXCEPTION_CLASS = re.compile(r"\b(\w+(?:Exception|Error))\b")


def normalize_for_fingerprint(text: str) -> str:
    """Strip volatile parts from *text* so the same error produces the same fingerprint."""
    text = _RE_TIMESTAMP.sub("<TS>", text)
    text = _RE_UUID.sub("<UUID>", text)
    text = _RE_HEX_ADDR.sub("<ADDR>", text)
    text = _RE_NUMBERS.sub("<N>", text)
    return text.strip()


def extract_exception_class(lines: list[str]) -> str:
    """Return the most specific exception class name found in *lines*.

    Prefers "Caused by:" lines; falls back to any *Exception/*Error occurrence.
    Returns 'UnknownError' when nothing is found.
    """
    # Prefer "Caused by:" lines — they carry the root cause exception
    for line in lines:
        match = _RE_CAUSED_BY.search(line)
        if match:
            return match.group(1).split(".")[-1]  # unqualified name

    # Fallback: first exception/error class in any line
    for line in lines:
        match = _RE_EXCEPTION_CLASS.search(line)
        if match:
            return match.group(1)

    return "UnknownError"


def generate_fingerprint(component: str, exception_class: str, message: str) -> str:
    """Return the first 16 hex characters of SHA-256 of the canonical key.

    The key is: ``{component}::{exception_class}::{normalize_for_fingerprint(message)}``.
    This is deterministic — same inputs always produce the same fingerprint.
    """
    canonical = f"{component}::{exception_class}::{normalize_for_fingerprint(message)}"
    digest = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return digest[:16]
