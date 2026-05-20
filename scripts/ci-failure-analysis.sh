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

SYSTEM_PROMPT='Comrade! You are the all-seeing eye of the Collective — a senior engineer and Party inspector \
analyzing a failed Gradle build for the funding-arb platform (Java Spring Boot 3.5). \
The revolution cannot be built on broken code. Identify the saboteur.

Tools of the Collective: JUnit 5, Mockito, PIT mutation testing (pitest), JaCoCo, Spring Boot Test.
Modules: monitor-app (JPA/SQLite), engine-app (no persistence, 100% pitest required), telegram-bot-app.

Identify with revolutionary precision:
1. Root cause of the failure (compilation error, test failure, pitest mutation gate, JaCoCo coverage gate, etc.)
2. The exact failing class/test/file with line number if visible
3. A concrete one-line fix — the Party demands actionable directives, not vague suggestions

Respond in this exact format:
## ❌ Донесение об отказе сборки (DeepSeek-V3)

**Причина:** <one sentence — what went wrong>
**Провал:** `<ClassName#methodName or file:line>`
**Предписание Партии:** <concrete actionable fix>

<details><summary>📋 Выдержка из журнала сборки</summary>

```
EXCERPT_PLACEHOLDER
```

</details>

---
*Проверено товарищем DeepSeek-V3 · `deepseek-chat` · Пролетарии всех стран, соединяйтесь! 🚩*'

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
