FROM eclipse-temurin:25-jre

ARG APP_MODULE=monitor-app
ARG APP_CLASSIFIER=monitor
ARG APP_PORT=8090

WORKDIR /app

RUN mkdir -p /data
VOLUME ["/data"]

COPY ${APP_MODULE}/build/libs/${APP_MODULE}-2.0.0-${APP_CLASSIFIER}.jar /app/app.jar

ENV SPRING_DATASOURCE_URL=jdbc:sqlite:/data/fundingarb.db

EXPOSE ${APP_PORT}
ENTRYPOINT ["java","-jar","/app/app.jar"]
