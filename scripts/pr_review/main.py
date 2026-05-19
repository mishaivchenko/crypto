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

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import client
import collector
import decision_policy
import deduplicator
import github_client
import parser
import prompt_builder
import quality_gate
import renderer


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

    # Step 6: Dedup — fetch existing comments
    existing_issue_comments = github_client.get_pr_comments(repo, pr_number)
    existing_review_comments = github_client.get_review_comments(repo, pr_number)
    all_existing = existing_issue_comments + existing_review_comments

    posted_fps = deduplicator.already_posted_fingerprints(all_existing)
    summary_exists = deduplicator.has_summary_comment(existing_issue_comments)

    # Step 7: Render inline concerns
    inline_candidates = renderer.select_inline_concerns(result)
    new_inline = deduplicator.filter_new_concerns(inline_candidates, posted_fps)

    # Step 8: Get head SHA for the review
    head_sha = github_client.get_pr_head_sha(repo, pr_number)

    # Step 9: Post review
    summary_body = renderer.render_summary(result, enforced, ctx.diff_truncated)

    if head_sha and new_inline:
        inline_payloads = [
            {
                "path": c.file,
                "line": c.line_hint,
                "body": renderer.render_inline_comment(c),
                "side": "RIGHT",
            }
            for c in new_inline
            if c.line_hint > 0
        ]
        ok_review = github_client.post_review_with_comments(
            repo, pr_number, head_sha, enforced, summary_body, inline_payloads
        )
        if ok_review:
            print(f"[pr-review] Posted review ({enforced}) with {len(inline_payloads)} inline comment(s)")
        else:
            print("[pr-review] WARNING: review submission failed — falling back to plain comment")
            _post_or_update_summary(repo, pr_number, summary_body, existing_issue_comments, summary_exists)
    else:
        _post_or_update_summary(repo, pr_number, summary_body, existing_issue_comments, summary_exists)

    print("[pr-review] Done.")


def _post_or_update_summary(
    repo: str,
    pr_number: int,
    body: str,
    existing_comments: list[dict],
    summary_exists: bool,
) -> None:
    from renderer import _SUMMARY_MARKER
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
