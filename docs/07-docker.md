# Docker (One container, Linux)

## Цель
Один контейнер, внутри:
- app.jar (Spring Boot)
- TDLib native lib (libtdjson.so)
- SQLite db file на volume /data

## Multi-stage build (факт)
- Builder: образ `gradle:8.10.2-jdk21`, сборка `bootJar` с аргументом `TD_NATIVES` (по умолчанию `linux_amd64_gnu_ssl3`).
- Runtime: `eclipse-temurin:21-jre`, копируем app.jar, создаём `/data/tdlib`, переменные `TG_SESSION_DIR` и `SPRING_DATASOURCE_URL` уже выставлены.

### Volumes
- /data -> хранит fundingarb.db и tdlib session

### ENV
см. docs/02-runtime-config.md

## TDLib / tdlight
Используем prebuilt tdlight-natives через Maven (classifier задаётся через `TD_NATIVES`), поэтому не компилируем TDLib внутри образа.

## Проверка в контейнере
- приложение стартует
- TDLib успешно грузится
- создаётся/читается sqlite файл
- scheduler видит записи
