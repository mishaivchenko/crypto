FROM gradle:8.10.2-jdk21 AS build
WORKDIR /app

COPY gradle.properties build.gradle settings.gradle* /app/
COPY src /app/src

ARG TD_NATIVES=linux_amd64
RUN gradle clean bootJar -PtdNativesClassifier=${TD_NATIVES} --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates libstdc++6 zlib1g \
  && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /data/tdlib
VOLUME ["/data"]

COPY --from=build /app/build/libs/*.jar /app/app.jar

ENV TG_SESSION_DIR=/data/tdlib
ENV SPRING_DATASOURCE_URL=jdbc:sqlite:/data/fundingarb.db
ENV SERVER_PORT=8090

EXPOSE 8090
ENTRYPOINT ["java","-jar","/app/app.jar"]
