"""Deterministic grouping and prioritization for quality backlog findings."""
from __future__ import annotations

import hashlib
from collections import defaultdict

from quality_backlog.models import FindingGroup, SonarIssue, SonarMetric

_SEVERITY_ORDER = {
    "BLOCKER": 5,
    "CRITICAL": 4,
    "HIGH": 4,
    "MAJOR": 3,
    "MEDIUM": 3,
    "MINOR": 2,
    "LOW": 2,
    "INFO": 1,
    "UNKNOWN": 0,
}

_METRIC_LABELS = {
    "coverage": ("coverage", "Project coverage below target", "medium"),
    "duplicated_lines_density": ("maintainability", "Duplicated code density above target", "medium"),
    "reliability_rating": ("bug", "Reliability rating needs attention", "high"),
    "security_rating": ("security", "Security rating needs attention", "high"),
    "sqale_rating": ("maintainability", "Maintainability rating needs attention", "medium"),
}


def build_groups(issues: list[SonarIssue], metrics: list[SonarMetric]) -> list[FindingGroup]:
    grouped: dict[tuple[str, str, str], list[SonarIssue]] = defaultdict(list)
    for issue in issues:
        category = _category(issue)
        grouped[(issue.component, category, issue.rule)].append(issue)

    groups: list[FindingGroup] = []
    for (component, category, rule), group_issues in grouped.items():
        worst = max(group_issues, key=lambda issue: _SEVERITY_ORDER.get(issue.severity, 0))
        theme = worst.rule_name or worst.message or rule
        priority = _priority(category, worst.severity, len(group_issues))
        groups.append(
            FindingGroup(
                fingerprint=_fingerprint(component, category, rule),
                component=component,
                category=category,
                theme=theme,
                rule=rule,
                priority=priority,
                severity=worst.severity,
                issue_type=worst.issue_type,
                issues=sorted(group_issues, key=lambda issue: (issue.path, issue.line or 0, issue.key)),
            ),
        )

    groups.extend(_metric_groups(metrics))
    return sorted(groups, key=_sort_key)


def select_groups(groups: list[FindingGroup], limit: int) -> list[FindingGroup]:
    return groups[: max(limit, 0)]


def labels_for(group: FindingGroup) -> list[str]:
    labels = ["quality-backlog", "source:sonarqube", f"priority:{group.priority}"]
    if group.category == "security":
        labels.extend(["security", "tech-debt"])
    elif group.category == "bug":
        labels.append("bug")
    elif group.category == "coverage":
        labels.extend(["coverage", "tech-debt"])
    else:
        labels.append("tech-debt")
    return list(dict.fromkeys(labels))


def _category(issue: SonarIssue) -> str:
    if issue.issue_type in {"VULNERABILITY", "SECURITY_HOTSPOT"}:
        return "security"
    if issue.issue_type == "BUG":
        return "bug"
    return "maintainability"


def _priority(category: str, severity: str, count: int) -> str:
    if category == "security" or severity in {"BLOCKER", "CRITICAL", "HIGH"}:
        return "high"
    if category == "bug" or severity in {"MAJOR", "MEDIUM"} or count >= 5:
        return "medium"
    return "low"


def _metric_groups(metrics: list[SonarMetric]) -> list[FindingGroup]:
    groups: list[FindingGroup] = []
    by_key = {metric.key: metric for metric in metrics}
    metric_targets = {
        "coverage": lambda value: value < 80.0,
        "duplicated_lines_density": lambda value: value > 3.0,
        "reliability_rating": lambda value: value > 1.0,
        "security_rating": lambda value: value > 1.0,
        "sqale_rating": lambda value: value > 1.0,
    }
    for key, should_create in metric_targets.items():
        metric = by_key.get(key)
        if metric is None or not should_create(metric.value):
            continue
        category, theme, priority = _METRIC_LABELS[key]
        groups.append(
            FindingGroup(
                fingerprint=_fingerprint("project", category, f"metric:{key}"),
                component="project",
                category=category,
                theme=theme,
                rule=f"metric:{key}",
                priority=priority,
                severity="METRIC",
                issue_type="METRIC",
                metric=metric,
            ),
        )
    return groups


def _sort_key(group: FindingGroup) -> tuple[int, int, int, str]:
    priority_score = {"high": 0, "medium": 1, "low": 2}.get(group.priority, 3)
    severity_score = -_SEVERITY_ORDER.get(group.severity, 0)
    return (priority_score, severity_score, -group.count, group.theme)


def _fingerprint(component: str, category: str, rule: str) -> str:
    raw = f"{component}:{category}:{rule}"
    return hashlib.sha256(raw.encode()).hexdigest()[:16]

