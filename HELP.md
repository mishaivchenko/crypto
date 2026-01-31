# Getting Started

### Что здесь
- Основные команды: `./gradlew clean build --no-daemon` (бэкенд + фронт), `./gradlew test` (юнит-тесты), `./gradlew frontendBuild` (Vite build).
- CI/CD: GitHub Actions (`.github/workflows/ci-cd.yml`) собирает, тестирует и пушит Docker-образ при push в `main`.

### Reference Documentation

For further reference, please consider the following sections:

* [Official Gradle documentation](https://docs.gradle.org)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/3.5.7/gradle-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/3.5.7/gradle-plugin/packaging-oci-image.html)
* [Spring Boot Actuator](https://docs.spring.io/spring-boot/3.5.7/reference/actuator/index.html)
* [Prometheus](https://docs.spring.io/spring-boot/3.5.7/reference/actuator/metrics.html#actuator.metrics.export.prometheus)
* [Spring Web](https://docs.spring.io/spring-boot/3.5.7/reference/web/servlet.html)
* [WebSocket](https://docs.spring.io/spring-boot/3.5.7/reference/messaging/websockets.html)

### Guides

The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service with Spring Boot Actuator](https://spring.io/guides/gs/actuator-service/)
* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Using WebSocket to build an interactive web application](https://spring.io/guides/gs/messaging-stomp-websocket/)

### Additional Links

These additional references should also help you:

* [Gradle Build Scans – insights for your project's build](https://scans.gradle.com#gradle)
