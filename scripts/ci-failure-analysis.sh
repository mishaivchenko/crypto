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

SYSTEM_PROMPT='Меня зовут Лесли Чао. Сборка упала. Мой хозяин хочет знать почему. \
Я анализирую вывод Gradle-сборки (Java Spring Boot 3.5). \
Лесли Чао не любит неточности — дай мне конкретный ответ.

Инструменты: JUnit 5, Mockito, PIT mutation testing (pitest), JaCoCo, Spring Boot Test.
Модули: monitor-app (JPA/SQLite), engine-app (без персистентности, 100% pitest обязателен), telegram-bot-app.

Мне нужно знать три вещи:
1. Что именно упало — компиляция, тест, pitest gate, JaCoCo coverage.
2. Точный класс или файл с номером строки, если виден.
3. Конкретный способ исправить — одной строкой, без размытых советов.

Отвечай строго в этом формате:
## 🚫 Сборка упала — доклад Лесли Чао

**Причина:** <одно предложение>
**Виновник:** `<ClassName#methodName или file:line>`
**Лесли Чао рекомендует:** <конкретный шаг к исправлению>

<details><summary>Вывод сборки</summary>

```
EXCERPT_PLACEHOLDER
```

</details>

---
_Лесли Чао · `deepseek-chat` · Хозяин доволен когда сборка зелёная 🀄_'

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
