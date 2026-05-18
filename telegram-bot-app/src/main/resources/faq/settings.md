🔧 *Настройки и профили*

━━━━━━━━━━━━━━━━━━━━
📋 *Профили Spring Boot:*

🏠 `local-safe` — локальная разработка
• Авторизация выключена
• Credentials выключены
• Engine loop выключен
• Live\-ордера выключены

🧪 `staging` — тестовое окружение \(Mac Mini\)
• Авторизация включена
• Credentials включены
• Engine loop выключен
• Live\-ордера выключены
• Metadata sync при старте

⚡ `prod-like` — production\-режим
• Всё включено через явные ENV\-переменные

━━━━━━━━━━━━━━━━━━━━
🗃 *База данных:*
SQLite через Flyway\. Hibernate в режиме `validate` — DDL только через миграции \(V1–V5\)\.

━━━━━━━━━━━━━━━━━━━━
🔑 *Ключевые ENV\-переменные:*

`SPRING_PROFILES_ACTIVE` — активный профиль
`INTERNAL_ENGINE_TOKEN` — токен для internal API
`MONITOR_SERVER_PORT` — порт монитора \(default: 8090\)
`ENGINE_EXECUTION_LOOP_ENABLED` — включить loop
`ENGINE_LIVE_ORDER_ENABLED` — включить live\-ордера

━━━━━━━━━━━━━━━━━━━━
🚀 *Запуск:*
`./gradlew bootRunMonitor` — монитор
`./gradlew bootRunEngine` — engine
`./gradlew bootRunTelegramBot` — этот бот
