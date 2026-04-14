FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build

COPY pom.xml .
RUN mvn install -N -DskipTests

COPY shared-events shared-events
COPY shared-security shared-security

RUN mvn -f shared-events/pom.xml install -DskipTests
RUN mvn -f shared-security/pom.xml install -DskipTests

COPY user-service/pom.xml user-service/
RUN mvn -f user-service/pom.xml dependency:go-offline

COPY user-service/src user-service/src
RUN mvn -f user-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/user-service/target/user-service-*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
