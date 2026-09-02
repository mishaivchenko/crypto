#!/usr/bin/env python3
"""Create dry-run reports or GitHub issues from local SonarQube findings."""
from __future__ import annotations

import argparse
import os
import pathlib
import sys

_SCRIPTS_DIR = pathlib.Path(__file__).resolve().parents[1]
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

from quality_backlog import deepseek, github_client, renderer, triage
from quality_backlog.sonar_client import SonarClient, SonarError

_DEFAULT_HOST = "http://127.0.0.1:9000"
_DEFAULT_PROJECT_KEY = "crypto"
_DEFAULT_OUTPUT_DIR = pathlib.Path("build/quality-backlog")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--mode",
        choices=("dry-run", "create-issues"),
        default="dry-run",
        help="dry-run writes reports only; create-issues also opens/updates GitHub issues",
    )
    parser.add_argument("--project-key", default=os.environ.get("SONAR_PROJECT_KEY", _DEFAULT_PROJECT_KEY))
    parser.add_argument("--host-url", default=os.environ.get("SONAR_HOST_URL", _DEFAULT_HOST))
    parser.add_argument("--limit", type=int, default=int(os.environ.get("QUALITY_BACKLOG_LIMIT", "10")))
    parser.add_argument("--output-dir", type=pathlib.Path, default=_DEFAULT_OUTPUT_DIR)
    parser.add_argument("--include-ai-summary", action=argparse.BooleanOptionalAction, default=True)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    sonar_token = os.environ.get("SONAR_TOKEN", "").strip()
    if not sonar_token:
        print("[quality-backlog] ERROR: SONAR_TOKEN is required to read local SonarQube findings")
        return 1

    if args.mode == "create-issues":
        repo = os.environ.get("GITHUB_REPOSITORY", "").strip()
        gh_token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN", "")
        if not repo:
            print("[quality-backlog] ERROR: GITHUB_REPOSITORY is required in create-issues mode")
            return 1
        if not gh_token.strip():
            print("[quality-backlog] ERROR: GH_TOKEN or GITHUB_TOKEN is required in create-issues mode")
            return 1

    client = SonarClient(args.host_url, sonar_token, args.project_key)
    try:
        status = client.health()
        if status != "UP":
            print(f"[quality-backlog] ERROR: SonarQube status is {status}, expected UP")
            return 1
        issues = client.fetch_issues()
        try:
            hotspots = client.fetch_hotspots()
        except SonarError as exc:
            print(f"[quality-backlog] WARNING: security hotspots unavailable: {exc}")
            hotspots = []
        metrics = client.fetch_metrics()
    except SonarError as exc:
        print(f"[quality-backlog] ERROR: {exc}")
        return 1

    groups = triage.build_groups(issues + hotspots, metrics)
    selected = triage.select_groups(groups, args.limit)
    for group in selected:
        group.ai_summary = deepseek.summarize(group, args.include_ai_summary)

    args.output_dir.mkdir(parents=True, exist_ok=True)
    markdown_path = args.output_dir / "quality-backlog.md"
    json_path = args.output_dir / "quality-backlog.json"
    markdown_path.write_text(renderer.render_report(groups, selected, metrics, args.mode), encoding="utf-8")
    json_path.write_text(renderer.render_json(groups, selected, args.mode) + "\n", encoding="utf-8")

    print(f"[quality-backlog] Wrote {markdown_path}")
    print(f"[quality-backlog] Wrote {json_path}")
    print(f"[quality-backlog] Found {len(groups)} group(s); selected {len(selected)}")

    if args.mode == "dry-run":
        print("[quality-backlog] Dry-run complete; no GitHub issues were changed")
        return 0

    repo = os.environ.get("GITHUB_REPOSITORY", "").strip()
    all_labels = sorted({label for group in selected for label in triage.labels_for(group)})
    github_client.ensure_labels(all_labels, repo)

    created = 0
    updated = 0
    for group in selected:
        existing = github_client.find_existing_issue(repo, group.fingerprint)
        if existing:
            if github_client.add_comment(repo, existing, renderer.render_recurrence_comment(group)):
                updated += 1
                print(f"[quality-backlog] Updated existing issue #{existing} for {group.fingerprint}")
            continue
        issue_number = github_client.create_issue(
            repo=repo,
            title=renderer.render_issue_title(group),
            body=renderer.render_issue_body(group),
            labels=triage.labels_for(group),
        )
        if issue_number:
            created += 1
            print(f"[quality-backlog] Created issue #{issue_number} for {group.fingerprint}")

    print(f"[quality-backlog] Create mode complete: created={created}, updated={updated}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
