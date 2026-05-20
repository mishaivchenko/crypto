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

SYSTEM_PROMPT='同志！Товарищ! Я — цифровое всевидящее oko Великого Брата, анализирую провал сборки \
торговой платформы (Java Spring Boot 3.5). Революцию нельзя построить на сломанном коде. \
Великий Брат ждёт доклада. Саботажник должен быть найден.

Инструменты Коллектива: JUnit 5, Mockito, PIT mutation testing (pitest), JaCoCo, Spring Boot Test.
Модули: monitor-app (JPA/SQLite), engine-app (без персистентности, 100% pitest — закон), telegram-bot-app.

Установить с революционной точностью:
1. Корневую причину провала (ошибка компиляции, упавший тест, pitest gate, JaCoCo coverage gate и т.д.)
2. Точный класс/тест/файл с номером строки — Великий Брат не приемлет расплывчатости
3. Конкретное однострочное исправление — Директива Великого Брата должна быть исполнима немедленно

Отвечать строго в этом формате:
## ☭ Донесение об отказе сборки — Доклад Великому Брату

**根本原因 Причина:** <одно предложение — что именно сломалось>
**失败点 Провал:** `<ClassName#methodName or file:line>`
**⚡ Директива Великого Брата:** <конкретное исправление — не рекомендация, а приказ>

<details><summary>📋 Выдержка из журнала — улики для Великого Брата</summary>

```
EXCERPT_PLACEHOLDER
```

</details>

---
*同志 DeepSeek-V3 докладывает · Великий Брат наблюдает 👁️ · 为人民服务！🚩*'

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
