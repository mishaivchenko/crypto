"""Entry point for the AI PR review pipeline.

Flow:
  1. Validate env vars
  2. Collect PR diff + changed files (sanitized)
  3. Build DeepSeek prompt
  4. Call DeepSeek
  5. Parse response
  6. Quality gate
  7. Enforce decision policy
  8. Deduplicate against existing comments
  9. Post summary + inline comments
"""
from __future__ import annotations

import os
import sys
_DIR = os.path.dirname(os.path.abspath(__file__))
_SCRIPTS_DIR = os.path.dirname(_DIR)
_EM_DIR = os.path.join(_DIR, "..", "error_monitor")
# _SCRIPTS_DIR must be on sys.path so "pr_review" is importable as a package.
# _EM_DIR provides error_monitor/sanitizer (used by collector).
# _DIR is added last (lowest priority) so bare names like "collector" can't
# shadow error_monitor modules; all imports below use the pr_review. prefix.
for _p in (_EM_DIR, _SCRIPTS_DIR):
    if _p not in sys.path:
        sys.path.insert(0, _p)

import pr_review.client as client
import pr_review.collector as collector
import pr_review.decision_policy as decision_policy
import pr_review.deduplicator as deduplicator
import pr_review.github_client as github_client
import pr_review.parser as parser
import pr_review.prompt_builder as prompt_builder
import pr_review.quality_gate as quality_gate
import pr_review.renderer as renderer
from pr_review.renderer import _SUMMARY_MARKER


def main() -> None:
    api_key = os.environ.get("DEEPSEEK_API_KEY", "").strip()
    if not api_key:
        print("[pr-review] ERROR: DEEPSEEK_API_KEY is not set")
        sys.exit(1)

    repo = os.environ.get("GITHUB_REPOSITORY", "").strip()
    if not repo:
        print("[pr-review] ERROR: GITHUB_REPOSITORY is not set")
        sys.exit(1)

    pr_number_str = os.environ.get("PR_NUMBER", "").strip()
    if not pr_number_str or not pr_number_str.isdigit():
        print("[pr-review] ERROR: PR_NUMBER is not set or not a number")
        sys.exit(1)
    pr_number = int(pr_number_str)

    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN", "")
    if not token:
        print("[pr-review] ERROR: GH_TOKEN or GITHUB_TOKEN is not set")
        sys.exit(1)

    ci_context = os.environ.get("CI_CONTEXT", "").strip()

    print(f"[pr-review] Starting review for PR #{pr_number} in {repo}")

    # Step 1: Collect
    try:
        ctx = collector.collect(repo, pr_number, token, ci_context)
    except Exception as exc:
        print(f"[pr-review] ERROR: failed to collect PR context: {exc}")
        sys.exit(1)

    if not ctx.diff.strip() or ctx.diff.strip() == "(empty diff)":
        print("[pr-review] Empty diff — nothing to review")
        sys.exit(0)

    # Step 2: Prompt + call DeepSeek
    system_prompt, user_prompt = prompt_builder.build(ctx)
    print(f"[pr-review] Sending {len(user_prompt)} chars to DeepSeek...")
    try:
        raw_response = client.call(system_prompt, user_prompt, api_key)
    except Exception as exc:
        print(f"[pr-review] ERROR: DeepSeek call failed: {exc}")
        _post_diagnostic(repo, pr_number, f"DeepSeek API call failed: {exc}")
        sys.exit(0)  # non-fatal — don't block PR

    # Step 3: Parse
    result = parser.parse(raw_response)
    if result is None:
        print("[pr-review] WARNING: failed to parse DeepSeek response")
        _post_diagnostic(repo, pr_number, "AI review parse failed — response was not valid JSON")
        sys.exit(0)

    print(f"[pr-review] Parsed: decision={result.review_decision} confidence={result.confidence:.2f} "
          f"risk={result.risk_level} concerns={len(result.all_concerns())}")

    # Step 4: Quality gate
    ok, reason = quality_gate.passes(result)
    if not ok:
        print(f"[pr-review] Quality gate rejected: {reason}")
        sys.exit(0)

    # Step 5: Enforce decision
    enforced = decision_policy.enforce(result)
    print(f"[pr-review] Decision: model={result.review_decision} enforced={enforced}")

    # Step 6: Fetch existing issue comments for dedup/update
    existing_issue_comments = github_client.get_pr_comments(repo, pr_number)
    summary_exists = deduplicator.has_summary_comment(existing_issue_comments)

    # Post summary only — no inline comments to avoid noise
    summary_body = renderer.render_summary(result, enforced, ctx.diff_truncated)
    _post_or_update_summary(repo, pr_number, summary_body, existing_issue_comments, summary_exists)

    print("[pr-review] Done.")


def _post_or_update_summary(
    repo: str,
    pr_number: int,
    body: str,
    existing_comments: list[dict],
    summary_exists: bool,
) -> None:
    if summary_exists:
        for comment in existing_comments:
            if _SUMMARY_MARKER in (comment.get("body") or ""):
                comment_id = comment.get("id")
                if comment_id:
                    updated = github_client.update_pr_comment(repo, int(comment_id), body)
                    if updated:
                        print("[pr-review] Updated existing summary comment")
                        return
    comment_id = github_client.post_pr_comment(repo, pr_number, body)
    if comment_id:
        print(f"[pr-review] Posted summary comment #{comment_id}")
    else:
        print("[pr-review] WARNING: failed to post summary comment")


def _post_diagnostic(repo: str, pr_number: int, message: str) -> None:
    body = f"<!-- ai-pr-review-summary -->\n⚠️ **AI PR Review:** {message}\n\n*DeepSeek-V3*"
    github_client.post_pr_comment(repo, pr_number, body)


if __name__ == "__main__":
    main()
