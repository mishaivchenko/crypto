# Docker (One container, Linux)

## Цель
Один контейнер, внутри:
- app.jar (Spring Boot)
- SQLite db file на volume /data
- polling candidate source через внешний funding API

## Multi-stage build (факт)
- Builder: образ `gradle:8.10.2-jdk21`, сборка `bootJar`.
- Runtime: `eclipse-temurin:21-jre`, копируем app.jar, создаём `/data`, переменная `SPRING_DATASOURCE_URL` уже выставлена.

### Volumes
- /data -> хранит fundingarb.db

### ENV
см. docs/02-runtime-config.md

## Проверка в контейнере
- приложение стартует
- funding API доступен
- создаётся/читается sqlite файл
- candidate source обновляет watchlist и создаёт `SignalCandidate`
