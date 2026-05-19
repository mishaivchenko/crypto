"""Frozen DTOs for the PR review pipeline."""
from __future__ import annotations

from dataclasses import dataclass, field


@dataclass(frozen=True)
class Concern:
    severity: str      # LOW | MEDIUM | HIGH | CRITICAL
    file: str
    line_hint: int
    category: str
    message: str
    recommendation: str


@dataclass(frozen=True)
class ReviewResult:
    review_decision: str   # APPROVE | COMMENT | REQUEST_CHANGES
    confidence: float
    summary: str
    risk_level: str        # LOW | MEDIUM | HIGH | CRITICAL
    architecture_concerns: tuple[Concern, ...]
    correctness_concerns: tuple[Concern, ...]
    concurrency_concerns: tuple[Concern, ...]
    trading_risk_concerns: tuple[Concern, ...]
    observability_concerns: tuple[Concern, ...]
    test_concerns: tuple[Concern, ...]
    positive_notes: tuple[str, ...]

    def all_concerns(self) -> tuple[Concern, ...]:
        return (
            self.architecture_concerns
            + self.correctness_concerns
            + self.concurrency_concerns
            + self.trading_risk_concerns
            + self.observability_concerns
            + self.test_concerns
        )


@dataclass(frozen=True)
class PullRequestContext:
    pr_number: int
    repo: str
    diff: str                        # sanitized, possibly truncated
    changed_files: tuple[str, ...]
    diff_truncated: bool
    ci_context: str                  # optional: build/test output snippet
