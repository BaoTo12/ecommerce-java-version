FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build

COPY pom.xml .
RUN mvn install -N -DskipTests

COPY shared-events shared-events
COPY shared-security shared-security

RUN mvn -f shared-events/pom.xml install -DskipTests
RUN mvn -f shared-security/pom.xml install -DskipTests

COPY inventory-service/pom.xml inventory-service/
RUN mvn -f inventory-service/pom.xml dependency:go-offline

COPY inventory-service/src inventory-service/src
RUN mvn -f inventory-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/inventory-service/target/inventory-service-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
