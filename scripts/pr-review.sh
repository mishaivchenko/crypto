#!/usr/bin/env bash
# Fetches PR diff, sends to DeepSeek for project-aware review, posts gh PR comment.
# Requires: GH_TOKEN, DEEPSEEK_API_KEY, GITHUB_REPOSITORY, PR_NUMBER
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/deepseek.sh
source "$SCRIPT_DIR/deepseek.sh"

SYSTEM_PROMPT='You are a senior engineer reviewing a pull request for a Java Spring Boot 3.5 project called funding-arb.
The project has three modules: monitor-app (operator control plane), engine-app (execution runtime), telegram-bot-app (notifications).

Check the diff for these project-specific invariants and report status for each:

1. [engine-app TDD] Every new production class in engine-app must appear in docs/engine-tdd/gap-matrix.md and have a matching test. Flag any new .java file in engine-app/src/main that lacks a corresponding test or gap-matrix entry.

2. [Venue adapter] Adding a new exchange venue requires implementing all three ports: VenueCredentialCheckPort, VenueMetadataPort, VenueMarkPricePort. Flag if a partial implementation is present.

3. [Flyway schema] Any change to JPA entity fields or new @Entity/@Table annotations requires a new Flyway migration (Vx__*.sql in monitor-app/src/main/resources/db/migration). Flag schema changes without a migration.

4. [Passphrase venues] Adding a venue that uses a passphrase (like okx, bitget, kucoin) requires updating requiresPassphrase() in both VenueProfileService (monitor-app) and LiveExchangeExecutionPort (engine-app). Flag if only one side is updated.

5. [Security] Look for: hardcoded secrets or API keys, SQL injection risks, command injection in shell-executed strings, unencrypted credential storage.

6. [General code quality] Correctness issues, missing null checks at system boundaries, obvious logic bugs, broken error handling.

Respond in this exact format:
## 🤖 PR Review (DeepSeek-V3)

**[engine-app TDD]** ✅ No new production classes without tests. / ⚠️ <issue>
**[Venue adapter]** ✅ ... / ⚠️ <issue>
**[Flyway schema]** ✅ ... / ⚠️ <issue>
**[Passphrase venues]** ✅ ... / ⚠️ <issue>
**[Security]** ✅ ... / ⚠️ <issue>
**[General]** ✅ ... / ⚠️ <issue>

If there are ⚠️ items, add a ### Recommendations section with concrete fixes.

---
*Reviewed by DeepSeek-V3 · [deepseek-chat]*'

echo "[pr-review] Fetching diff for PR #${PR_NUMBER}..."
diff=$(gh pr diff "$PR_NUMBER" --repo "$GITHUB_REPOSITORY" 2>/dev/null || true)

if [ -z "$diff" ]; then
    echo "[pr-review] Empty diff, nothing to review."
    exit 0
fi

# Truncate to ~48KB to stay within token budget (~12K tokens)
MAX_BYTES=48000
if [ "${#diff}" -gt "$MAX_BYTES" ]; then
    diff="${diff:0:$MAX_BYTES}
[... diff truncated at ${MAX_BYTES} bytes ...]"
    echo "[pr-review] Diff truncated to ${MAX_BYTES} bytes."
fi

echo "[pr-review] Sending diff to DeepSeek..."
review=$(deepseek_chat "$SYSTEM_PROMPT" "Please review this pull request diff:

$diff")

echo "[pr-review] Posting comment to PR #${PR_NUMBER}..."
gh pr comment "$PR_NUMBER" \
    --repo "$GITHUB_REPOSITORY" \
    --body "$review"

echo "[pr-review] Done."
