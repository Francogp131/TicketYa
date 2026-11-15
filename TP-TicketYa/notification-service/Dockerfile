# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
WORKDIR /app
COPY --from=build /app/target/notification-service-*.jar app.jar
EXPOSE 8085
HEALTHCHECK --interval=15s --timeout=3s --retries=10 \
    CMD wget -qO- http://localhost:8085/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
