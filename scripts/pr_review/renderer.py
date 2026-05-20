"""Renders ReviewResult into GitHub comment markdown."""
from __future__ import annotations

import hashlib

from pr_review.models import Concern, ReviewResult

_SUMMARY_MARKER = "<!-- ai-pr-review-summary -->"
_MAX_INLINE_TOTAL = 10
_MAX_PER_CATEGORY = 3
_MAX_PER_FILE = 2

_SEV_EMOJI = {"CRITICAL": "🔴", "HIGH": "🟠", "MEDIUM": "🟡", "LOW": "🔵"}
_RISK_EMOJI = {"CRITICAL": "🔴", "HIGH": "🟠", "MEDIUM": "🟡", "LOW": "🟢"}
_SEV_ORDER = {"CRITICAL": 0, "HIGH": 1, "MEDIUM": 2, "LOW": 3}

_DECISION_BANNER = {
    "APPROVE":         ("✅", "APPROVED — NO CONCERNS RAISED"),
    "COMMENT":         ("💬", "REVIEWED — SEE NOTES BELOW"),
    "REQUEST_CHANGES": ("❌", "CHANGES REQUESTED"),
}

_DEEPSEEK_LOGO = "https://raw.githubusercontent.com/deepseek-ai/DeepSeek-V2/main/figures/logo.svg"

# Category display names
_CATEGORY_LABELS = {
    "architectureConcerns":    ("🏛️", "Architecture"),
    "correctnessConcerns":     ("🐛", "Correctness"),
    "concurrencyConcerns":     ("⚡", "Concurrency"),
    "tradingRiskConcerns":     ("💰", "Trading Risk"),
    "observabilityConcerns":   ("📊", "Observability"),
    "testConcerns":            ("🧪", "Tests"),
}


def concern_fingerprint(concern: Concern) -> str:
    key = f"{concern.file}|{concern.category}|{concern.message[:80]}"
    return hashlib.sha256(key.encode()).hexdigest()[:12]


def render_summary(result: ReviewResult, enforced_decision: str, truncated: bool) -> str:
    dec_emoji, dec_label = _DECISION_BANNER.get(enforced_decision, ("💬", enforced_decision))
    risk_emoji = _RISK_EMOJI.get(result.risk_level, "⚪")
    all_concerns = result.all_concerns()

    lines = [_SUMMARY_MARKER]

    # ── Header ────────────────────────────────────────────────────────────────
    lines += [
        f"<img src=\"{_DEEPSEEK_LOGO}\" height=\"20\" alt=\"DeepSeek\"> &nbsp; "
        f"**DeepSeek Code Review** &nbsp;|&nbsp; {dec_emoji} **{dec_label}**",
        "",
        "---",
        "",
    ]

    # ── Scorecard ─────────────────────────────────────────────────────────────
    lines += [
        "| Metric | Value |",
        "|--------|-------|",
        f"| Risk | {risk_emoji} **{result.risk_level}** |",
        f"| Confidence | `{result.confidence:.0%}` |",
        f"| Concerns | `{len(all_concerns)}` |",
        "",
    ]

    # ── Summary ───────────────────────────────────────────────────────────────
    lines += [
        f"> {result.summary}",
        "",
    ]

    if truncated:
        lines += [
            "> [!WARNING]",
            "> Diff was truncated — review covers a partial view of this PR.",
            "",
        ]

    # ── Concerns by category ──────────────────────────────────────────────────
    concern_groups = [
        ("architectureConcerns",  result.architecture_concerns),
        ("correctnessConcerns",   result.correctness_concerns),
        ("concurrencyConcerns",   result.concurrency_concerns),
        ("tradingRiskConcerns",   result.trading_risk_concerns),
        ("observabilityConcerns", result.observability_concerns),
        ("testConcerns",          result.test_concerns),
    ]

    has_any_concerns = False
    for key, concerns in concern_groups:
        if not concerns:
            continue
        has_any_concerns = True
        icon, label = _CATEGORY_LABELS.get(key, ("⚠️", key))
        sorted_c = sorted(concerns, key=lambda c: _SEV_ORDER.get(c.severity, 9))
        lines.append(f"#### {icon} {label}")
        lines.append("")
        for c in sorted_c:
            sev_emoji = _SEV_EMOJI.get(c.severity, "⚪")
            file_ref = f"`{c.file}`" if c.file else ""
            line_ref = f" line {c.line_hint}" if c.line_hint else ""
            lines.append(f"<details>")
            lines.append(f"<summary>{sev_emoji} <strong>{c.severity}</strong> — {c.message[:120]}</summary>")
            lines.append("")
            if file_ref:
                lines.append(f"**File:** {file_ref}{line_ref}  ")
            lines.append(f"**Category:** `{c.category}`  ")
            lines.append(f"**Details:** {c.message}")
            if c.recommendation:
                lines.append(f"  ")
                lines.append(f"**Recommendation:** {c.recommendation}")
            lines.append("</details>")
            lines.append("")

    if not has_any_concerns:
        lines += ["**No concerns found.** 🎉", ""]

    # ── Positive notes ────────────────────────────────────────────────────────
    if result.positive_notes:
        lines.append("#### ✅ What's good")
        lines.append("")
        for note in result.positive_notes:
            lines.append(f"- {note}")
        lines.append("")

    # ── Footer ────────────────────────────────────────────────────────────────
    lines += [
        "---",
        "<sub>🤖 Reviewed by <a href=\"https://www.deepseek.com\">DeepSeek-V3</a> · "
        "<code>deepseek-chat</code> · Температура 0.1 · "
        "Пролетарии всех стран, соединяйтесь! 🚩</sub>",
    ]

    return "\n".join(lines)


def select_inline_concerns(result: ReviewResult) -> list[Concern]:
    all_concerns = list(result.all_concerns())
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
    fp = concern_fingerprint(concern)
    sev_emoji = _SEV_EMOJI.get(concern.severity, "⚪")
    lines = [
        f"<!-- ai-pr-review-fingerprint: {fp} -->",
        f"{sev_emoji} **{concern.severity}** · `{concern.category}`",
        "",
        concern.message,
    ]
    if concern.recommendation:
        lines += ["", f"> **Recommendation:** {concern.recommendation}"]
    lines += ["", "<sub>DeepSeek-V3 · Пролетарии всех стран, соединяйтесь! 🚩</sub>"]
    return "\n".join(lines)
