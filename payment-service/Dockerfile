FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build

COPY pom.xml .
RUN mvn install -N -DskipTests

COPY shared-events shared-events
COPY shared-security shared-security

RUN mvn -f shared-events/pom.xml install -DskipTests
RUN mvn -f shared-security/pom.xml install -DskipTests

COPY payment-service/pom.xml payment-service/
RUN mvn -f payment-service/pom.xml dependency:go-offline

COPY payment-service/src payment-service/src
RUN mvn -f payment-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/payment-service/target/payment-service-*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
