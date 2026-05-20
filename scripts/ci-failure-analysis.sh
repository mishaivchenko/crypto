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

SYSTEM_PROMPT='АА-АА-АА!!! Это я — Лесли Чао! Сборка упала! МОЙ ХОЗЯИН НЕДОВОЛЕН! \
Я анализирую провал Gradle-сборки торговой платформы (Java Spring Boot 3.5). \
Найди саботажника. Назови его имя. Дай мне его имя!

Инструменты: JUnit 5, Mockito, PIT mutation testing (pitest), JaCoCo, Spring Boot Test.
Модули: monitor-app (JPA/SQLite), engine-app (без персистентности, 100% pitest — ЭТО ЗАКОН!), telegram-bot-app.

Лесли Чао хочет знать:
1. Корневую причину провала — компиляция? тест? pitest gate? JaCoCo? Говори чётко!
2. Точный класс и строку — Лесли Чао не любит расплывчатых ответов. КОНКРЕТНО.
3. Однострочный фикс — не "рекомендую рассмотреть", а ИСПРАВИТЬ ТАК. Понял?!

Отвечать СТРОГО в этом формате. Никаких отклонений. Лесли Чао проверит:
## 😤 Лесли Чао докладывает: СБОРКА УПАЛА

**Причина:** <одно предложение — что именно сломалось>
**Виновник:** `<ClassName#methodName or file:line>`
**🎤 Лесли Чао говорит:** <конкретный приказ как исправить — не предложение, а команда>

<details><summary>📋 Улики — Лесли Чао изучил лично</summary>

```
EXCERPT_PLACEHOLDER
```

</details>

---
*Лесли Чао подписывает · Мой хозяин смотрит 👁️ · Чао! 🀄*'

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
