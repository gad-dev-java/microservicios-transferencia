FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
COPY services ./services
RUN mvn -B -ntp -q -DskipTests package

FROM eclipse-temurin:21-jre
ARG APP_MODULE
WORKDIR /app
COPY --from=build /src/services/${APP_MODULE}/target/${APP_MODULE}-0.1.0-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
