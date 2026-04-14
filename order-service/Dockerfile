FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build

COPY pom.xml .
RUN mvn install -N -DskipTests

COPY shared-events shared-events
COPY shared-security shared-security

RUN mvn -f shared-events/pom.xml install -DskipTests
RUN mvn -f shared-security/pom.xml install -DskipTests

COPY order-service/pom.xml order-service/
RUN mvn -f order-service/pom.xml dependency:go-offline

COPY order-service/src order-service/src
RUN mvn -f order-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/order-service/target/order-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
