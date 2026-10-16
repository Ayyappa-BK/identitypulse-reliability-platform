FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B package -DskipTests
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -g 10001 app && adduser -D -u 10001 -G app app
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/target/identitypulse-1.0.0.jar app.jar
USER 10001:10001
EXPOSE 8080 9090
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]
