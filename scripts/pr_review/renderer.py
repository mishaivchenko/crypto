"""Renders ReviewResult into GitHub comment markdown."""
from __future__ import annotations

import hashlib

try:
    from pr_review.models import Concern, ReviewResult
except ImportError:
    from models import Concern, ReviewResult  # type: ignore[no-redef]

_SUMMARY_MARKER = "<!-- ai-pr-review-summary -->"
_MAX_INLINE_TOTAL = 10
_MAX_PER_CATEGORY = 3
_MAX_PER_FILE = 2

_SEV_EMOJI = {"CRITICAL": "🔴", "HIGH": "🟠", "MEDIUM": "🟡", "LOW": "🔵"}
_DECISION_EMOJI = {"APPROVE": "✅", "COMMENT": "💬", "REQUEST_CHANGES": "❌"}
_RISK_EMOJI = {"CRITICAL": "🔴", "HIGH": "🟠", "MEDIUM": "🟡", "LOW": "🟢"}


def concern_fingerprint(concern: Concern) -> str:
    """Stable fingerprint for a concern — used to deduplicate inline comments."""
    key = f"{concern.file}|{concern.category}|{concern.message[:80]}"
    return hashlib.sha256(key.encode()).hexdigest()[:12]


def render_summary(result: ReviewResult, enforced_decision: str, truncated: bool) -> str:
    """Render the top-level summary comment body."""
    dec_emoji = _DECISION_EMOJI.get(enforced_decision, "💬")
    risk_emoji = _RISK_EMOJI.get(result.risk_level, "⚪")

    lines = [
        _SUMMARY_MARKER,
        f"## {dec_emoji} AI PR Review — {enforced_decision}",
        f"**Risk level:** {risk_emoji} {result.risk_level}  "
        f"**Confidence:** {result.confidence:.0%}",
        "",
        f"**Summary:** {result.summary}",
    ]

    if truncated:
        lines.append("\n> ⚠️ Diff was truncated — review covers a partial view of the PR.")

    all_concerns = result.all_concerns()
    if all_concerns:
        # Top concerns only — sorted by severity
        _SEV_ORDER = {"CRITICAL": 0, "HIGH": 1, "MEDIUM": 2, "LOW": 3}
        top = sorted(all_concerns, key=lambda c: _SEV_ORDER.get(c.severity, 9))[:6]
        lines.append("\n### Top concerns")
        for c in top:
            emoji = _SEV_EMOJI.get(c.severity, "⚪")
            file_ref = f"`{c.file}`" if c.file else "_unknown file_"
            lines.append(f"- {emoji} **{c.severity}** [{c.category}] {file_ref}: {c.message}")

    if result.positive_notes:
        lines.append("\n### ✅ Positive notes")
        for note in result.positive_notes:
            lines.append(f"- {note}")

    lines.append("\n---")
    lines.append("*Reviewed by DeepSeek-V3 · [deepseek-chat] · [ai-pr-review]*")
    return "\n".join(lines)


def select_inline_concerns(result: ReviewResult) -> list[Concern]:
    """Select concerns eligible for inline comments, respecting spam limits."""
    all_concerns = list(result.all_concerns())
    _SEV_ORDER = {"CRITICAL": 0, "HIGH": 1, "MEDIUM": 2, "LOW": 3}
    sorted_concerns = sorted(all_concerns, key=lambda c: _SEV_ORDER.get(c.severity, 9))

    selected: list[Concern] = []
    per_category: dict[str, int] = {}
    per_file: dict[str, int] = {}

    for c in sorted_concerns:
        if len(selected) >= _MAX_INLINE_TOTAL:
            break
        if not c.file or not c.line_hint:
            continue
        if per_category.get(c.category, 0) >= _MAX_PER_CATEGORY:
            continue
        file_count = per_file.get(c.file, 0)
        if file_count >= _MAX_PER_FILE and c.severity not in ("HIGH", "CRITICAL"):
            continue
        selected.append(c)
        per_category[c.category] = per_category.get(c.category, 0) + 1
        per_file[c.file] = file_count + 1

    return selected


def render_inline_comment(concern: Concern) -> str:
    """Render a single inline comment body with machine-readable fingerprint."""
    fp = concern_fingerprint(concern)
    emoji = _SEV_EMOJI.get(concern.severity, "⚪")
    lines = [
        f"<!-- ai-pr-review-fingerprint: {fp} -->",
        f"{emoji} **{concern.severity}** [{concern.category}]",
        "",
        concern.message,
    ]
    if concern.recommendation:
        lines.append(f"\n**Recommendation:** {concern.recommendation}")
    lines.append("\n*DeepSeek-V3 AI review*")
    return "\n".join(lines)
