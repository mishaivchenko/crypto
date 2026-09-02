"""Renders ReviewResult into GitHub comment markdown."""
from __future__ import annotations

import hashlib

from pr_review.ci_summary import hidden_snapshot
from pr_review.models import CiSummary, Concern, QualityMetric, ReviewResult

_SUMMARY_MARKER = "<!-- ai-pr-review-summary -->"
_MAX_INLINE_TOTAL = 10
_MAX_PER_CATEGORY = 3
_MAX_PER_FILE = 2

_SEV_EMOJI = {"CRITICAL": "🔴", "HIGH": "🟠", "MEDIUM": "🟡", "LOW": "🔵"}
_RISK_EMOJI = {"CRITICAL": "🔴", "HIGH": "🟠", "MEDIUM": "🟡", "LOW": "🟢"}

_DECISION_BANNER = {
    "APPROVE":         ("✅", "ОДОБРЕНО — КОЛЛЕКТИВ ДОВОЛЕН"),
    "COMMENT":         ("💬", "НА РАССМОТРЕНИИ — ПАРТИЯ ИМЕЕТ ЗАМЕЧАНИЯ"),
    "REQUEST_CHANGES": ("❌", "ОТКЛОНЕНО — ИСПРАВИТЬ ДО СЛИЯНИЯ, ТОВАРИЩ"),
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


def render_summary(
    result: ReviewResult,
    enforced_decision: str,
    truncated: bool,
    ci_summary: CiSummary | None = None,
    previous_ci_summary: CiSummary | None = None,
) -> str:
    """Render the top-level summary comment body."""
    dec_emoji, dec_label = _DECISION_BANNER.get(enforced_decision, ("💬", enforced_decision))
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
        "| Параметр | Значение |",
        "|---|---|",
        f"| **Уровень риска** | {risk_emoji} {result.risk_level} |",
        f"| **Уверенность** | {result.confidence:.0%} |",
        f"| **Замечаний найдено** | {total} |",
        "",
        f"> 📜 **Сводка товарища инспектора:** {result.summary}",
    ]

    if truncated:
        lines.append("\n> ⚠️ Дифф был обрезан — проверка охватывает только часть изменений. Полная картина недоступна даже Партии.")

    if ci_summary:
        lines.extend(_render_ci_summary(ci_summary, previous_ci_summary))

    # Group concerns by category
    by_category: dict[str, list[Concern]] = {}
    for c in all_concerns:
        by_category.setdefault(c.category, []).append(c)

    if all_concerns:
        lines.append("\n### 🔍 Протокол инспекции")
        lines.append("")
        # Show top concerns sorted by severity
        top = sorted(all_concerns, key=lambda c: _SEV_ORDER.get(c.severity, 9))[:8]
        for c in top:
            emoji = _SEV_EMOJI.get(c.severity, "⚪")
            file_ref = f"`{c.file}`" if c.file else "_неизвестный файл_"
            lines.append(f"- {emoji} **{c.severity}** `{c.category}` {file_ref} — {c.message}")

    if result.positive_notes:
        lines.append("\n### 🏅 Похвала от Партии")
        for note in result.positive_notes:
            lines.append(f"- ✊ {note}")

    lines.append("\n---")
    lines.append(
        "_Проверено товарищем DeepSeek-V3 · модель `deepseek-chat` · "
        "Пролетарии всех стран, соединяйтесь! 🚩_"
    )
    if ci_summary:
        lines.append(hidden_snapshot(ci_summary))
    return "\n".join(lines)


def _render_ci_summary(summary: CiSummary, previous: CiSummary | None) -> list[str]:
    lines = [
        "",
        "### ✅ CI/CD факт-чек",
        "",
        "| Проверка | Статус | Время |",
        "|---|---:|---:|",
    ]
    for check in summary.checks:
        lines.append(
            f"| {check.name} | {_format_check_conclusion(check.conclusion)} | "
            f"{_format_duration(check.duration_seconds)} |"
        )
    if summary.run_url:
        lines.append(f"\n[Открыть CI run]({summary.run_url})")

    if summary.metrics:
        previous_by_key = {
            metric.key: metric
            for metric in previous.metrics
        } if previous else {}
        lines.extend([
            "",
            "### 📊 Качество и дельта",
            "",
            "| Отчет | Сейчас | Изменение | Оценка |",
            "|---|---:|---:|---|",
        ])
        for metric in summary.metrics:
            previous_metric = previous_by_key.get(metric.key)
            direction, verdict = _metric_delta(metric, previous_metric)
            lines.append(
                f"| {metric.label} | {_format_metric_value(metric)} | "
                f"{direction} | {verdict} |"
            )
    return lines


def _format_check_conclusion(conclusion: str) -> str:
    normalized = conclusion.lower()
    if normalized == "success":
        return "✅ pass"
    if normalized == "failure":
        return "❌ fail"
    if normalized == "cancelled":
        return "⚪ cancelled"
    if normalized == "skipped":
        return "⏭️ skipped"
    return f"⚪ {conclusion or 'unknown'}"


def _format_duration(seconds: int) -> str:
    if seconds <= 0:
        return "—"
    minutes, remainder = divmod(seconds, 60)
    if minutes:
        return f"{minutes}m {remainder}s"
    return f"{remainder}s"


def _format_metric_value(metric: QualityMetric) -> str:
    if metric.unit == "%":
        return f"{metric.value:.1f}%"
    if metric.value.is_integer():
        return str(int(metric.value))
    return f"{metric.value:.2f}{metric.unit}"


def _metric_delta(metric: QualityMetric, previous: QualityMetric | None) -> tuple[str, str]:
    if previous is None:
        return "🆕 первый замер", "baseline"
    delta = metric.value - previous.value
    if abs(delta) < 0.005:
        return "→ ровно", "без изменений"
    arrow = "↑ выросло" if delta > 0 else "↓ упало"
    signed = f"{delta:+.1f}{metric.unit}" if metric.unit == "%" else f"{delta:+g}{metric.unit}"
    improved = delta < 0 if metric.lower_is_better else delta > 0
    verdict = "лучше" if improved else "хуже"
    return f"{arrow} ({signed})", verdict


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
        f"Товарищ инспектор обнаружил: {concern.message}",
    ]
    if concern.recommendation:
        lines.append(f"\n**Предписание Партии:** {concern.recommendation}")
    lines.append("\n_DeepSeek-V3 · Пролетарии всех стран, соединяйтесь! 🚩_")
    return "\n".join(lines)
