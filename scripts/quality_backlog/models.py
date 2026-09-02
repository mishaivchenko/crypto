"""Data models for quality backlog triage."""
from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True)
class SonarIssue:
    key: str
    rule: str
    rule_name: str
    issue_type: str
    severity: str
    component: str
    path: str
    line: int | None
    message: str
    status: str
    url: str


@dataclass(frozen=True)
class SonarMetric:
    key: str
    value: float
    raw_value: str


@dataclass
class FindingGroup:
    fingerprint: str
    component: str
    category: str
    theme: str
    rule: str
    priority: str
    severity: str
    issue_type: str
    issues: list[SonarIssue] = field(default_factory=list)
    metric: SonarMetric | None = None
    ai_summary: dict[str, str] | None = None

    @property
    def count(self) -> int:
        return len(self.issues) if self.issues else 1

