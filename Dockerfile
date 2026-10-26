# syntax=docker/dockerfile:1.7

FROM maven:3.9-eclipse-temurin-23 AS build
WORKDIR /workspace

COPY . /workspace

# Build the deployment artifacts of the project (RestServer.jar + base.jar)
RUN mvn -B -Pdeploy -DskipTests clean package

FROM eclipse-temurin:23-jre-jammy
WORKDIR /app

# Copy of the production artifacts
COPY --from=build /workspace/deploy/RestServer.jar /app/RestServer.jar
COPY --from=build /workspace/deploy/base.jar /app/base.jar

RUN useradd --system --create-home --home-dir /home/thermobox thermobox \
    && chown -R thermobox:thermobox /app

USER thermobox

EXPOSE 8080

ENV THERMOBOX_DB_URL='jdbc:mysql://mysql:3306/thermobox' \
    THERMOBOX_DB_USER='thermobox' \
    THERMOBOX_DB_PASSWORD='changeit' \
    THERMOBOX_JWT_SECRET='' \
    THERMOBOX_JWT_TTL_MINUTES='60'

ENTRYPOINT ["sh", "-c", "java -jar /app/RestServer.jar"]
