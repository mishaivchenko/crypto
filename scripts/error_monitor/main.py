"""Entry point for the SRE error monitor.

Flow:
  1. Validate required env vars
  2. Ensure GitHub labels exist
  3. Collect Docker logs for the last 24 hours
  4. For each service: extract → sanitize → analyse → quality gate → dedup → issue
  5. Print observability summary
"""
from __future__ import annotations

import os
import sys

# Allow imports from sibling modules regardless of working directory
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from analyzer import call_deepseek, parse_analysis_result
from collector import collect_logs
from extractor import extract_error_blocks
from fingerprint import extract_exception_class, generate_fingerprint
from github_client import (
    add_occurrence_comment,
    create_issue,
    ensure_labels,
    find_existing_issue,
)
from quality_gate import passes
from renderer import render_issue_body
from sanitizer import sanitize

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

REQUIRED_LABELS = [
    "ai-log-analysis",
    "source:mac-mini",
    "component:engine",
    "component:monitor",
    "component:telegram-bot",
    "area:websocket",
    "area:exchange-api",
    "area:docker",
    "priority:medium",
    "priority:high",
    "priority:critical",
]

_SERVICE_TO_COMPONENT = {
    "monitor": "monitor-app",
    "engine": "engine-app",
    "telegram-bot": "telegram-bot-app",
}


def _build_payload(service: str, blocks: list) -> str:
    """Build a sanitized text payload to send to DeepSeek."""
    parts = [f"=== Service: {service} ===\n"]
    for i, block in enumerate(blocks, start=1):
        header = f"--- Error block {i} (occurrences: {block.occurrence_count}) ---"
        content = "\n".join(block.lines)
        parts.append(f"{header}\n{sanitize(content)}")
    return "\n\n".join(parts)


def _process_service(
    service: str,
    raw_logs: str,
    api_key: str,
    repo: str,
    stats: dict,
) -> None:
    """Run the full analysis pipeline for a single service."""
    stats["logs_scanned"] += 1
    log_chars = len(raw_logs)
    print(f"\n[main] Processing service='{service}' log_chars={log_chars}")

    blocks = extract_error_blocks(service, raw_logs)
    stats["error_blocks_found"] += len(blocks)

    if not blocks:
        print(f"[main] No error blocks found for service='{service}' — skipping")
        return

    payload = _build_payload(service, blocks)
    payload_size = len(payload)
    stats["total_payload_chars"] += payload_size
    print(f"[main] Payload size for '{service}': {payload_size} chars across {len(blocks)} block(s)")

    # Call DeepSeek
    try:
        raw_response = call_deepseek(payload, api_key)
        stats["api_calls"] += 1
        print(f"[main] DeepSeek responded for service='{service}'")
    except Exception as exc:  # noqa: BLE001
        print(f"[main] ERROR: DeepSeek API call failed for service='{service}': {exc}")
        stats["api_errors"] += 1
        return

    # Parse JSON
    result = parse_analysis_result(raw_response)
    if result is None:
        print(f"[main] WARNING: could not parse DeepSeek response for service='{service}'")
        stats["parse_errors"] += 1
        return

    # Quality gate
    ok, reason = passes(result)
    if not ok:
        print(f"[main] Quality gate rejected service='{service}': {reason}")
        stats["skipped"] += 1
        return

    # Use fingerprint from AI result; if empty/missing, generate one locally
    fingerprint = (result.get("fingerprint") or "").strip()
    if not fingerprint:
        component = _SERVICE_TO_COMPONENT.get(service, "unknown")
        all_lines = [line for block in blocks for line in block.lines]
        exc_class = extract_exception_class(list(all_lines))
        first_error = next(
            (line for block in blocks for line in block.lines if "ERROR" in line or "Exception" in line),
            service,
        )
        fingerprint = generate_fingerprint(component, exc_class, first_error)
        result = {**result, "fingerprint": fingerprint}

    # Dedup check
    existing_issue = find_existing_issue(fingerprint, repo)

    title = result.get("title", f"[{service}] Error detected")
    severity = result.get("severity", "UNKNOWN")
    evidence = result.get("evidence", [])
    labels_from_ai = result.get("labels", [])

    # Build final labels list
    base_labels = ["ai-log-analysis", "source:mac-mini"]
    final_labels = list(dict.fromkeys(base_labels + labels_from_ai))  # deduplicate, preserve order

    if existing_issue:
        print(f"[main] Updating existing issue #{existing_issue} for service='{service}' fingerprint={fingerprint!r}")
        add_occurrence_comment(
            issue_number=existing_issue,
            service=service,
            severity=severity,
            evidence=evidence,
            repo=repo,
        )
        stats["issues_updated"] += 1
    else:
        body = render_issue_body(result, service)
        issue_number = create_issue(
            title=title,
            body=body,
            labels=final_labels,
            repo=repo,
        )
        if issue_number:
            print(f"[main] Created issue #{issue_number} for service='{service}' fingerprint={fingerprint!r}")
            stats["issues_created"] += 1
        else:
            print(f"[main] WARNING: failed to create issue for service='{service}'")
            stats["create_errors"] += 1


def main() -> None:
    api_key = os.environ.get("DEEPSEEK_API_KEY", "").strip()
    if not api_key:
        print("[main] ERROR: DEEPSEEK_API_KEY environment variable is not set")
        sys.exit(1)

    repo = os.environ.get("GITHUB_REPOSITORY", "").strip()
    if not repo:
        print("[main] ERROR: GITHUB_REPOSITORY environment variable is not set")
        sys.exit(1)

    print(f"[main] Starting error monitor for repo='{repo}'")

    # Ensure all required labels exist (non-fatal if some fail)
    print("[main] Ensuring GitHub labels exist...")
    ensure_labels(REQUIRED_LABELS, repo)

    # Collect logs
    print("[main] Collecting Docker logs (last 24h)...")
    all_logs = collect_logs(since_hours=24)

    # Observability stats
    stats: dict = {
        "logs_scanned": 0,
        "error_blocks_found": 0,
        "total_payload_chars": 0,
        "api_calls": 0,
        "api_errors": 0,
        "parse_errors": 0,
        "skipped": 0,
        "issues_created": 0,
        "issues_updated": 0,
        "create_errors": 0,
    }

    for service, raw_logs in all_logs.items():
        try:
            _process_service(service, raw_logs, api_key, repo, stats)
        except Exception as exc:  # noqa: BLE001
            print(f"[main] UNEXPECTED ERROR processing service='{service}': {exc}")
            stats["api_errors"] += 1

    # Summary
    print("\n" + "=" * 60)
    print("[main] === Observability Summary ===")
    print(f"  Logs scanned:        {stats['logs_scanned']}")
    print(f"  Error blocks found:  {stats['error_blocks_found']}")
    print(f"  Total payload chars: {stats['total_payload_chars']}")
    print(f"  API calls made:      {stats['api_calls']}")
    print(f"  API errors:          {stats['api_errors']}")
    print(f"  Parse errors:        {stats['parse_errors']}")
    print(f"  Skipped (gate/noise):{stats['skipped']}")
    print(f"  Issues created:      {stats['issues_created']}")
    print(f"  Issues updated:      {stats['issues_updated']}")
    print(f"  Create errors:       {stats['create_errors']}")
    print("=" * 60)


if __name__ == "__main__":
    main()
