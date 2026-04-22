FROM eclipse-temurin:25-jre

ARG APP_JAR=monitor-app/build/libs/monitor-app-2.0.0-monitor.jar
ARG APP_PORT=8090

WORKDIR /app

RUN mkdir -p /data
VOLUME ["/data"]

COPY ${APP_JAR} /app/app.jar

ENV SPRING_DATASOURCE_URL=jdbc:sqlite:/data/fundingarb.db

EXPOSE ${APP_PORT}
ENTRYPOINT ["java","-jar","/app/app.jar"]
