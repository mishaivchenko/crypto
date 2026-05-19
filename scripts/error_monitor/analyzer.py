"""DeepSeek API integration for log analysis.

Uses only Python stdlib (urllib.request) — no third-party dependencies.
"""
from __future__ import annotations

import json
import urllib.request
import urllib.error

_DEEPSEEK_API_URL = "https://api.deepseek.com/chat/completions"
_MODEL = "deepseek-chat"
_MAX_TOKENS = 1000
_TEMPERATURE = 0.1

_SYSTEM_PROMPT = """\
You are an expert SRE analyzing Docker container logs from a Java Spring Boot trading system.
Analyze the provided log excerpt and return ONLY valid JSON — no markdown, no prose, no explanation.

Return exactly this JSON schema:
{
  "shouldCreateIssue": <bool>,
  "severity": "<LOW|MEDIUM|HIGH|CRITICAL>",
  "confidence": <0.0-1.0>,
  "category": "<WEBSOCKET_FAILURE|EXCHANGE_API_FAILURE|STARTUP_FAILURE|CONFIGURATION_ERROR|DATABASE_ERROR|NETWORK_ERROR|UNKNOWN>",
  "component": "<engine-app|monitor-app|telegram-bot-app|platform-core|unknown>",
  "fingerprint": "<stable-hash-or-short-id>",
  "title": "<short title max 80 chars>",
  "summary": "<concise paragraph describing the issue>",
  "impact": "<what business or operational impact this has>",
  "evidence": ["<log line or snippet 1>", "<log line or snippet 2>"],
  "suspectedCause": "<root cause hypothesis>",
  "recommendedFix": "<concrete remediation steps>",
  "labels": ["<label1>", "<label2>"]
}

Rules:
- Set shouldCreateIssue=false for routine INFO/DEBUG noise, expected startup messages, or low-impact warnings.
- Set shouldCreateIssue=true only for actionable errors requiring investigation.
- confidence must reflect how certain you are this is a real issue worth tracking.
- fingerprint must be a short stable identifier derived from the error type and component (not timestamps or line numbers).
- evidence must contain actual log lines from the input, not paraphrases.
- Return ONLY the JSON object, nothing else.
"""

_REQUIRED_FIELDS = frozenset({
    "shouldCreateIssue",
    "severity",
    "confidence",
    "fingerprint",
    "title",
    "evidence",
    "recommendedFix",
})


def call_deepseek(payload: str, api_key: str) -> str:
    """Send *payload* to DeepSeek and return the raw response text.

    Raises urllib.error.URLError / OSError on network failure.
    """
    request_body = json.dumps({
        "model": _MODEL,
        "max_tokens": _MAX_TOKENS,
        "temperature": _TEMPERATURE,
        "messages": [
            {"role": "system", "content": _SYSTEM_PROMPT},
            {"role": "user", "content": payload},
        ],
    }).encode("utf-8")

    req = urllib.request.Request(
        _DEEPSEEK_API_URL,
        data=request_body,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )

    with urllib.request.urlopen(req, timeout=60) as response:
        raw = response.read().decode("utf-8")

    data = json.loads(raw)
    return data["choices"][0]["message"]["content"]


def parse_analysis_result(raw: str) -> dict | None:
    """Parse DeepSeek's response into a dict, or return None on any failure.

    Handles optional ```json ... ``` markdown fences.
    """
    if raw is None:
        return None

    text = raw.strip()

    # Strip markdown code fences if present
    if text.startswith("```"):
        lines = text.splitlines()
        # Remove opening fence (```json or ```)
        lines = lines[1:]
        # Remove closing fence
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        text = "\n".join(lines).strip()

    try:
        parsed = json.loads(text)
    except (json.JSONDecodeError, ValueError):
        return None

    if not isinstance(parsed, dict):
        return None

    # Validate all required fields are present
    missing = _REQUIRED_FIELDS - parsed.keys()
    if missing:
        return None

    # Validate evidence is a non-empty list
    evidence = parsed.get("evidence")
    if not isinstance(evidence, list):
        return None

    return parsed
