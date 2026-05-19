#!/usr/bin/env bash
# Reads Gradle build output, sends last 150 lines to DeepSeek, posts analysis as PR comment.
# Usage: ci-failure-analysis.sh <build-output-file>
# Requires: GH_TOKEN, DEEPSEEK_API_KEY, GITHUB_REPOSITORY, PR_NUMBER
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/deepseek.sh
source "$SCRIPT_DIR/deepseek.sh"

BUILD_OUTPUT_FILE="${1:-/tmp/build-output.txt}"

if [ ! -f "$BUILD_OUTPUT_FILE" ]; then
    echo "[ci-failure] Build output file not found: $BUILD_OUTPUT_FILE"
    exit 1
fi

SYSTEM_PROMPT='You are analyzing a failed Gradle build for a Java Spring Boot 3.5 project (funding-arb).
The project uses: JUnit 5, Mockito, PIT mutation testing (pitest), JaCoCo, Spring Boot Test.
Modules: monitor-app (JPA/SQLite), engine-app (no persistence, 100% pitest required), telegram-bot-app.

Given the build output excerpt, identify:
1. Root cause of the failure (compilation error, test failure, pitest mutation gate, JaCoCo coverage gate, etc.)
2. The exact failing class/test/file with line number if visible
3. A concrete one-line fix or next step

Respond in this exact format:
## ❌ Build Failure Analysis (DeepSeek-V3)

**Root cause:** <one sentence>
**Failed at:** `<ClassName#methodName or file:line>`
**Fix:** <concrete actionable step>

<details><summary>📋 Build output excerpt</summary>

```
EXCERPT_PLACEHOLDER
```

</details>

---
*Analyzed by DeepSeek-V3 · [deepseek-chat]*'

# Last 150 lines cover most failure cases without overloading the prompt
excerpt=$(tail -150 "$BUILD_OUTPUT_FILE")

# Last 50 lines for the collapsible block in the comment
short_excerpt=$(tail -50 "$BUILD_OUTPUT_FILE")

echo "[ci-failure] Sending build output to DeepSeek..."
raw_analysis=$(deepseek_chat "$SYSTEM_PROMPT" "Analyze this Gradle build failure:

$excerpt")

# Substitute the excerpt into the response body
analysis="${raw_analysis/EXCERPT_PLACEHOLDER/$short_excerpt}"

echo "[ci-failure] Posting comment to PR #${PR_NUMBER}..."
gh pr comment "$PR_NUMBER" \
    --repo "$GITHUB_REPOSITORY" \
    --body "$analysis"

echo "[ci-failure] Done."
