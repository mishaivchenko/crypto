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

_DECISION_BANNER = {
    "APPROVE":         ("🀄", "ХА! ЛЕСЛИ ЧАО ДОВОЛЕН. МЕРЖИ. НЕ ТРАТЬ МОЁ ВРЕМЯ."),
    "COMMENT":         ("🤌", "ЛЕСЛИ ЧАО СМОТРИТ. У ЛЕСЛИ ЧАО ВОПРОСЫ. ТЫ ДОЛЖЕН ОБЪЯСНИТЬ."),
    "REQUEST_CHANGES": ("😤", "АА-АА-АА!!! ЭТО ЧТО ТАКОЕ?! ИСПРАВЬ НЕМЕДЛЕННО ИЛИ Я ПРИЕДУ ЛИЧНО!!!"),
}

_DEEPSEEK_LOGO = "https://raw.githubusercontent.com/deepseek-ai/DeepSeek-V2/main/figures/logo.svg"

_CATEGORY_LABEL = {
    "ARCHITECTURE":  "🏛️ Архитектура",
    "CORRECTNESS":   "🐛 Корректность",
    "CONCURRENCY":   "⚡ Конкурентность",
    "TRADING_RISK":  "💰 Торговые риски",
    "OBSERVABILITY": "👁️ Наблюдаемость",
    "TESTS":         "🧪 Тесты",
}


def concern_fingerprint(concern: Concern) -> str:
    """Stable fingerprint for a concern — used to deduplicate inline comments."""
    key = f"{concern.file}|{concern.category}|{concern.message[:80]}"
    return hashlib.sha256(key.encode()).hexdigest()[:12]


def render_summary(result: ReviewResult, enforced_decision: str, truncated: bool) -> str:
    """Render the top-level summary comment body."""
    dec_emoji, dec_label = _DECISION_BANNER.get(enforced_decision, ("🤌", enforced_decision))
    risk_emoji = _RISK_EMOJI.get(result.risk_level, "⚪")

    all_concerns = result.all_concerns()
    total = len(all_concerns)
    _SEV_ORDER = {"CRITICAL": 0, "HIGH": 1, "MEDIUM": 2, "LOW": 3}

    lines = [
        _SUMMARY_MARKER,
        f'<img src="{_DEEPSEEK_LOGO}" alt="DeepSeek" height="24" align="right"/>',
        "",
        f"## {dec_emoji} {dec_label}",
        "",
        "> *Это я — Лесли Чао. Лучший ревьюер в мире. Мой хозяин попросил проверить. Я проверил.*",
        "",
        "| | |",
        "|---|---|",
        f"| **Уровень риска** | {risk_emoji} {result.risk_level} |",
        f"| **Уверенность Лесли Чао** | {result.confidence:.0%} |",
        f"| **Нарушений обнаружено** | {total} |",
        "",
        f"> 🎤 **Лесли Чао говорит:** {result.summary}",
    ]

    if truncated:
        lines.append("\n> ⚠️ Дифф обрезан. Слишком большой. Кто делает такие огромные PR?! Лесли Чао проверил только часть. В следующий раз — меньше коммитов!")

    if all_concerns:
        lines.append("\n### 😤 Что нашёл Лесли Чао (и он недоволен)")
        lines.append("")
        top = sorted(all_concerns, key=lambda c: _SEV_ORDER.get(c.severity, 9))[:8]
        for c in top:
            emoji = _SEV_EMOJI.get(c.severity, "⚪")
            file_ref = f"`{c.file}`" if c.file else "_ты даже файл нормально не назвал_"
            lines.append(f"- {emoji} **{c.severity}** `{c.category}` {file_ref} — {c.message}")

    if result.positive_notes:
        lines.append("\n### 🀄 Лесли Чао хвалит (редкое событие, цени)")
        for note in result.positive_notes:
            lines.append(f"- 👍 {note}")

    lines.append("\n---")
    lines.append(
        "_Лесли Чао подписывает · модель `deepseek-chat` · "
        "Мой хозяин смотрит на тебя 👁️ · Чао! 🀄_"
    )
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
        f"{emoji} **{concern.severity}** `{concern.category}`",
        "",
        f"😤 **Лесли Чао обнаружил:** {concern.message}",
    ]
    if concern.recommendation:
        lines.append(f"\n**🎤 Лесли Чао говорит:** {concern.recommendation}")
    lines.append("\n_Лесли Чао подписывает · Мой хозяин смотрит 👁️ · Чао! 🀄_")
    return "\n".join(lines)
