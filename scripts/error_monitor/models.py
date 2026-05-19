"""Frozen dataclass DTOs for the error monitor pipeline."""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class ErrorBlock:
    service: str
    lines: tuple[str, ...]
    occurrence_count: int = 1


@dataclass(frozen=True)
class AnalysisResult:
    should_create_issue: bool
    severity: str          # LOW | MEDIUM | HIGH | CRITICAL
    confidence: float
    category: str          # WEBSOCKET_FAILURE | EXCHANGE_API_FAILURE | STARTUP_FAILURE |
                           # CONFIGURATION_ERROR | DATABASE_ERROR | NETWORK_ERROR | UNKNOWN
    component: str         # engine-app | monitor-app | telegram-bot-app | platform-core | unknown
    fingerprint: str
    title: str
    summary: str
    impact: str
    evidence: tuple[str, ...]
    suspected_cause: str
    recommended_fix: str
    labels: tuple[str, ...]
