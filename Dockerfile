FROM gradle:8.10.2-jdk21 AS build
ARG APP_MODULE=monitor-app
WORKDIR /app

COPY gradle.properties build.gradle settings.gradle* /app/
COPY gradle /app/gradle
COPY gradlew gradlew.bat /app/
COPY platform-core /app/platform-core
COPY monitor-app /app/monitor-app
COPY engine-app /app/engine-app

RUN gradle :${APP_MODULE}:bootJar --no-daemon

FROM eclipse-temurin:21-jre
ARG APP_MODULE=monitor-app
ARG APP_CLASSIFIER=monitor
ARG APP_PORT=8090
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates libstdc++6 zlib1g \
  && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /data
VOLUME ["/data"]

COPY --from=build /app/${APP_MODULE}/build/libs/*-${APP_CLASSIFIER}.jar /app/app.jar

ENV SPRING_DATASOURCE_URL=jdbc:sqlite:/data/fundingarb.db

EXPOSE ${APP_PORT}
ENTRYPOINT ["java","-jar","/app/app.jar"]
