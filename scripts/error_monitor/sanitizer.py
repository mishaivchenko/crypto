"""Centralized log sanitizer — masks credentials before any log is sent externally.

All regexes are compiled once at module load for efficiency.
"""
from __future__ import annotations

import re

# ---------------------------------------------------------------------------
# Compiled patterns (module-level — never inside functions)
# ---------------------------------------------------------------------------

# Bearer / Authorization header values
_RE_BEARER = re.compile(
    r"((?:Bearer|Authorization)\s+)[A-Za-z0-9\-._~+/]+=*",
    re.IGNORECASE,
)

# Generic key=value or key:value credential fields
# Require = or : separator to avoid matching Java field names like "apiKey"
_RE_API_KEY = re.compile(
    r"(api[_-]?key\s*[:=]\s*)[^\s,&\"']+",
    re.IGNORECASE,
)
_RE_SECRET_KEY = re.compile(
    r"(secret[_-]?key\s*[:=]\s*)[^\s,&\"']+",
    re.IGNORECASE,
)
_RE_ACCESS_TOKEN = re.compile(
    r"(access[_-]?token\s*[:=]\s*)[^\s,&\"']+",
    re.IGNORECASE,
)
_RE_AUTH_TOKEN = re.compile(
    r"(auth[_-]?token\s*[:=]\s*)[^\s,&\"']+",
    re.IGNORECASE,
)
_RE_PASSWORD = re.compile(
    r"(pass(?:word|wd)?\s*[:=]\s*)[^\s,&\"']+",
    re.IGNORECASE,
)

# Telegram bot token: 8-10 digits : 35 alphanum/underscore/dash chars
_RE_TELEGRAM_TOKEN = re.compile(r"\b\d{8,10}:[A-Za-z0-9_\-]{35}\b")

# GitHub tokens
_RE_GHP_TOKEN = re.compile(r"ghp_[A-Za-z0-9]{36}")
_RE_GITHUB_PAT = re.compile(r"github_pat_[A-Za-z0-9_]{82}")

# URLs with embedded credentials: https://user:pass@host
_RE_URL_CREDS = re.compile(r"https?://[^@\s]+:[^@\s]+@")

# Long hex strings (>32 chars) only in a key=value or key: value context
# This avoids masking stack trace hex addresses that appear standalone
_RE_HEX_VALUE = re.compile(
    r"((?:key|token|secret|hash|digest|signature|credential)\s*[:=]\s*)[0-9a-fA-F]{33,}",
    re.IGNORECASE,
)


def sanitize(text: str) -> str:
    """Return a copy of *text* with all secrets masked.

    Does NOT alter stack trace class names, line numbers, or log timestamps.
    """
    text = _RE_BEARER.sub(r"\1***", text)
    text = _RE_API_KEY.sub(r"\1***", text)
    text = _RE_SECRET_KEY.sub(r"\1***", text)
    text = _RE_ACCESS_TOKEN.sub(r"\1***", text)
    text = _RE_AUTH_TOKEN.sub(r"\1***", text)
    text = _RE_PASSWORD.sub(r"\1***", text)
    text = _RE_TELEGRAM_TOKEN.sub("***", text)
    text = _RE_GHP_TOKEN.sub("ghp_***", text)
    text = _RE_GITHUB_PAT.sub("github_pat_***", text)
    text = _RE_URL_CREDS.sub("[REDACTED]://", text)
    text = _RE_HEX_VALUE.sub(r"\1[REDACTED]", text)
    return text
