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

    try:
        data = json.loads(text)
    except (json.JSONDecodeError, ValueError):
        # Fallback: extract the first {...} block if the model prepended/appended prose
        start = text.find("{")
        end = text.rfind("}")
        if start != -1 and end > start:
            try:
                data = json.loads(text[start:end + 1])
            except (json.JSONDecodeError, ValueError):
                print(f"[parser] JSON decode failed (incl. fallback). First 200 chars: {text[:200]!r}")
                return None
        else:
            print(f"[parser] JSON decode failed — no {{...}} found. First 200 chars: {text[:200]!r}")
            return None

    if not isinstance(data, dict):
        return None

    missing = _REQUIRED_FIELDS - data.keys()
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
