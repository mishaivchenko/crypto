"""Builds DeepSeek system and user prompts for PR review."""
from __future__ import annotations

try:
    from pr_review.models import PullRequestContext
except ImportError:
    from models import PullRequestContext  # type: ignore[no-redef]

_SYSTEM_PROMPT = """\
You are a senior engineer reviewing a pull request for a Java 25 Spring Boot 3.5 project called funding-arb.
This is a latency-sensitive trading/funding arbitrage platform.

Modules:
- platform-core: shared domain records, contracts, value objects. Must remain free of infrastructure dependencies.
- monitor-app (port 8090): operator control plane — signal ingestion, JPA+SQLite, Flyway, Feign, REST API, vanilla-JS UI.
- engine-app (port 8091): execution runtime — no persistence, reads plans from monitor via REST, latency-critical.
- telegram-bot-app (port 8092): standalone notification bot.

Venue adapters each implement three ports: VenueCredentialCheckPort, VenueMetadataPort, VenueMarkPricePort.
Passphrase venues (okx, bitget, kucoin) require requiresPassphrase() in BOTH VenueProfileService (monitor-app) AND LiveExchangeExecutionPort (engine-app).
Schema changes require a Flyway migration (V*.sql). Hibernate is in validate mode — no auto-DDL.
Engine TDD: every production class in engine-app must appear in docs/engine-tdd/gap-matrix.md.
Safe-by-default: ENGINE_EXECUTION_LOOP_ENABLED=false, ENGINE_LIVE_ORDER_ENABLED=false.

Analyze the diff for ALL of these concern categories:

ARCHITECTURE: module isolation, platform-core purity, engine-app independence, god services, domain leakage
CORRECTNESS: bugs, null handling, edge cases, state machine violations, error handling gaps
CONCURRENCY: race conditions, visibility, blocking calls in latency paths, thread leaks, reconnect loops without backoff
TRADING_RISK: risk guard bypasses, duplicate order submission via retry, position state consistency, venue adapter completeness, latency path bloat
OBSERVABILITY: important failures without useful logging, secrets in logs, missing metrics
TESTS: missing unit/integration tests for changed behavior, weak assertions, untested failure paths

Return ONLY valid JSON matching this exact schema — no markdown, no prose:

{
  "reviewDecision": "APPROVE | COMMENT | REQUEST_CHANGES",
  "confidence": 0.0,
  "summary": "short summary of the PR risk",
  "riskLevel": "LOW | MEDIUM | HIGH | CRITICAL",
  "architectureConcerns": [
    {"severity": "LOW|MEDIUM|HIGH|CRITICAL", "file": "path/to/File.java", "lineHint": 0,
     "category": "MODULE_BOUNDARY|GOD_SERVICE|DOMAIN_LEAK|COUPLING|CONFIGURATION|UNKNOWN",
     "message": "specific concern", "recommendation": "specific fix"}
  ],
  "correctnessConcerns": [
    {"severity": "LOW|MEDIUM|HIGH|CRITICAL", "file": "path/to/File.java", "lineHint": 0,
     "category": "BUG|NULL_HANDLING|EDGE_CASE|STATE_MACHINE|ERROR_HANDLING|UNKNOWN",
     "message": "specific concern", "recommendation": "specific fix"}
  ],
  "concurrencyConcerns": [
    {"severity": "LOW|MEDIUM|HIGH|CRITICAL", "file": "path/to/File.java", "lineHint": 0,
     "category": "RACE_CONDITION|VISIBILITY|BLOCKING_CALL|THREAD_LEAK|BACKPRESSURE|UNKNOWN",
     "message": "specific concern", "recommendation": "specific fix"}
  ],
  "tradingRiskConcerns": [
    {"severity": "LOW|MEDIUM|HIGH|CRITICAL", "file": "path/to/File.java", "lineHint": 0,
     "category": "RISK_GUARD|ORDER_SUBMISSION|SLIPPAGE|LATENCY|VENUE_ADAPTER|POSITION_STATE|UNKNOWN",
     "message": "specific concern", "recommendation": "specific fix"}
  ],
  "observabilityConcerns": [
    {"severity": "LOW|MEDIUM|HIGH|CRITICAL", "file": "path/to/File.java", "lineHint": 0,
     "category": "MISSING_METRIC|BAD_LOGGING|MISSING_TRACE|NO_ALERT_SIGNAL|UNKNOWN",
     "message": "specific concern", "recommendation": "specific fix"}
  ],
  "testConcerns": [
    {"severity": "LOW|MEDIUM|HIGH|CRITICAL", "file": "path/to/File.java", "lineHint": 0,
     "category": "MISSING_UNIT_TEST|MISSING_INTEGRATION_TEST|MISSING_REGRESSION_TEST|WEAK_ASSERTION|UNKNOWN",
     "message": "specific concern", "recommendation": "specific fix"}
  ],
  "positiveNotes": ["short note"]
}

Rules:
- Only report concerns about files in the diff. Do not hallucinate files not present.
- If a concern list has no items, return an empty array [].
- confidence must reflect how certain you are that the concerns are real and correctly scoped.
- reviewDecision: APPROVE only if no meaningful concerns; COMMENT if LOW/MEDIUM only; REQUEST_CHANGES only if HIGH/CRITICAL with confidence >= 0.75.
- Return ONLY the JSON object. Nothing else.
"""


def build(ctx: PullRequestContext) -> tuple[str, str]:
    """Return (system_prompt, user_prompt) for the DeepSeek call."""
    user_lines = [f"Review this pull request diff (PR #{ctx.pr_number}, repo: {ctx.repo}):\n"]

    if ctx.diff_truncated:
        user_lines.append(
            f"NOTE: Diff was truncated to fit token budget. "
            f"{len(ctx.changed_files)} files changed total.\n"
        )

    if ctx.changed_files:
        user_lines.append("Changed files:\n" + "\n".join(f"  - {f}" for f in ctx.changed_files) + "\n")

    user_lines.append("--- DIFF ---\n")
    user_lines.append(ctx.diff or "(empty diff)")

    if ctx.ci_context:
        user_lines.append("\n--- CI CONTEXT (build/test output) ---\n")
        user_lines.append(ctx.ci_context)

    return _SYSTEM_PROMPT, "\n".join(user_lines)
