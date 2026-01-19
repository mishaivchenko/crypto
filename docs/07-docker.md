# Docker (One container, Linux)

## Цель
Один контейнер, внутри:
- app.jar (Spring Boot)
- TDLib native lib (libtdjson.so)
- SQLite db file на volume /data

## Вариант A (рекомендуется): Multi-stage build с компиляцией TDLib

### Dockerfile outline
1) builder stage:
    - install build deps (cmake, g++, git, make, zlib, openssl)
    - clone tdlib
    - build tdjson
2) runtime stage:
    - JRE (temurin/jre 21)
    - copy libtdjson.so into /usr/local/lib
    - copy app.jar
    - set LD_LIBRARY_PATH

### Volumes
- /data -> хранит fundingarb.db и tdlib session

### ENV
см. docs/02-runtime-config.md

## Вариант B: prebuilt TDLib
Если найдём стабильный prebuilt tdjson под нужную arch (amd64), можно уменьшить время сборки,
но меньше контроля над зависимостями.

## Проверка в контейнере
- приложение стартует
- TDLib успешно грузится
- создаётся/читается sqlite файл
- scheduler видит записи
