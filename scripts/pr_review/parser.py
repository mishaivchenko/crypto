"""Parse and validate DeepSeek's JSON response into a ReviewResult."""
from __future__ import annotations

import json

from pr_review.models import Concern, ReviewResult

_VALID_DECISIONS = frozenset({"APPROVE", "COMMENT", "REQUEST_CHANGES"})
_VALID_SEVERITIES = frozenset({"LOW", "MEDIUM", "HIGH", "CRITICAL"})
_VALID_RISK_LEVELS = frozenset({"LOW", "MEDIUM", "HIGH", "CRITICAL"})
_REQUIRED_FIELDS = frozenset({
    "reviewDecision", "confidence", "summary", "riskLevel",
    "architectureConcerns", "correctnessConcerns", "concurrencyConcerns",
    "tradingRiskConcerns", "observabilityConcerns", "testConcerns",
})
# Fields required only when the response is complete (not repaired/truncated)
_REQUIRED_FIELDS_STRICT = _REQUIRED_FIELDS
# Minimal fields required even from a truncated/repaired response
_REQUIRED_FIELDS_MINIMAL = frozenset({"reviewDecision", "confidence", "summary", "riskLevel"})
# Concern fields that default to [] when missing in a repaired response
_CONCERN_FIELDS = frozenset({
    "architectureConcerns", "correctnessConcerns", "concurrencyConcerns",
    "tradingRiskConcerns", "observabilityConcerns", "testConcerns",
})


def _parse_concerns(raw: object) -> tuple[Concern, ...]:
    if not isinstance(raw, list):
        return ()
    result = []
    for item in raw:
        if not isinstance(item, dict):
            continue
        sev = str(item.get("severity", "LOW")).upper()
        if sev not in _VALID_SEVERITIES:
            sev = "LOW"
        try:
            line = int(item.get("lineHint", 0) or 0)
        except (TypeError, ValueError):
            line = 0
        result.append(Concern(
            severity=sev,
            file=str(item.get("file", "")),
            line_hint=line,
            category=str(item.get("category", "UNKNOWN")),
            message=str(item.get("message", "")),
            recommendation=str(item.get("recommendation", "")),
        ))
    return tuple(result)


def _repair_truncated_json(text: str) -> str | None:
    """Try to close a truncated JSON object by balancing braces/brackets.

    When the model hits max_tokens the JSON stops mid-stream.  We find the
    outermost ``{`` and walk forward, tracking nesting depth, then append the
    closing tokens needed to produce valid JSON.  The repaired object will have
    truncated strings/arrays but will at least parse, allowing the required
    top-level fields to be read.
    """
    start = text.find("{")
    if start == -1:
        return None

    # Walk from `start` counting unmatched openers.
    stack: list[str] = []
    in_string = False
    escape = False
    for i, ch in enumerate(text[start:]):
        if escape:
            escape = False
            continue
        if ch == "\\" and in_string:
            escape = True
            continue
        if ch == '"':
            in_string = not in_string
            continue
        if in_string:
            continue
        if ch in "{[":
            stack.append("}" if ch == "{" else "]")
        elif ch in "}]":
            if stack and stack[-1] == ch:
                stack.pop()

    if not stack:
        return None  # balanced — no repair needed, normal decode should work

    # Close any open string, then append closing tokens in reverse order.
    suffix = ('"' if in_string else "") + "".join(reversed(stack))
    return text[start:] + suffix


def parse(raw: str) -> ReviewResult | None:
    """Parse raw DeepSeek response into a ReviewResult. Returns None on any parse failure."""
    if not raw:
        return None

    text = raw.strip()
    if text.startswith("```"):
        lines = text.splitlines()
        lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        text = "\n".join(lines).strip()

    repaired = False
    try:
        data = json.loads(text)
    except (json.JSONDecodeError, ValueError):
        # Fallback 1: extract the first {...} block if the model prepended/appended prose
        start = text.find("{")
        end = text.rfind("}")
        parsed = False
        if start != -1 and end > start:
            try:
                data = json.loads(text[start:end + 1])
                parsed = True
            except (json.JSONDecodeError, ValueError):
                pass

        if not parsed:
            # Fallback 2: truncated response (max_tokens cut mid-stream) — repair by closing
            # open braces/brackets so json.loads can at least read top-level fields.
            fixed = _repair_truncated_json(text)
            if fixed:
                try:
                    data = json.loads(fixed)
                    parsed = True
                    repaired = True
                    print("[parser] Repaired truncated JSON response")
                except (json.JSONDecodeError, ValueError):
                    pass

        if not parsed:
            print(f"[parser] JSON decode failed (incl. repair). First 200 chars: {text[:200]!r}")
            return None

    if not isinstance(data, dict):
        return None

    # Repaired responses may be missing concern arrays that were truncated — fill with []
    if repaired:
        for field in _CONCERN_FIELDS:
            data.setdefault(field, [])
        required = _REQUIRED_FIELDS_MINIMAL
    else:
        required = _REQUIRED_FIELDS_STRICT

    missing = required - data.keys()
    if missing:
        print(f"[parser] Missing required fields: {missing}")
        return None

    decision = str(data.get("reviewDecision", "COMMENT")).upper()
    if decision not in _VALID_DECISIONS:
        decision = "COMMENT"

    risk = str(data.get("riskLevel", "LOW")).upper()
    if risk not in _VALID_RISK_LEVELS:
        risk = "LOW"

    try:
        confidence = float(data.get("confidence", 0.0))
    except (TypeError, ValueError):
        confidence = 0.0

    positive = data.get("positiveNotes", [])
    if not isinstance(positive, list):
        positive = []

    return ReviewResult(
        review_decision=decision,
        confidence=confidence,
        summary=str(data.get("summary", "")),
        risk_level=risk,
        architecture_concerns=_parse_concerns(data.get("architectureConcerns")),
        correctness_concerns=_parse_concerns(data.get("correctnessConcerns")),
        concurrency_concerns=_parse_concerns(data.get("concurrencyConcerns")),
        trading_risk_concerns=_parse_concerns(data.get("tradingRiskConcerns")),
        observability_concerns=_parse_concerns(data.get("observabilityConcerns")),
        test_concerns=_parse_concerns(data.get("testConcerns")),
        positive_notes=tuple(str(n) for n in positive),
    )
