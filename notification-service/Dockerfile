FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build

COPY pom.xml .
RUN mvn install -N -DskipTests

COPY shared-events shared-events
COPY shared-security shared-security

RUN mvn -f shared-events/pom.xml install -DskipTests
RUN mvn -f shared-security/pom.xml install -DskipTests

COPY notification-service/pom.xml notification-service/
RUN mvn -f notification-service/pom.xml dependency:go-offline

COPY notification-service/src notification-service/src
RUN mvn -f notification-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/notification-service/target/notification-service-*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
