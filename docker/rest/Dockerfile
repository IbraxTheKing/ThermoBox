# syntax=docker/dockerfile:1.7
#
# REST server image (Jersey + Jetty) -> deploy/RestServer.jar + deploy/base.jar
# Build context: root of the repository (see docker-compose.yml)

ARG JAVA_VERSION=23

# ---------------------------------------------------------------------------
# Build stage.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-${JAVA_VERSION} AS build
WORKDIR /workspace
# Version written into the build, read by the update system (docker-compose.yml: THERMOBOX_VERSION)
ARG THERMOBOX_VERSION=dev

COPY pom.xml ./
COPY ibr-utils ./ibr-utils
COPY Common ./Common
COPY Persistence ./Persistence
COPY Business ./Business
COPY RestServer ./RestServer
COPY Base ./Base

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -Pdeploy -DskipTests -Dthermobox.version=${THERMOBOX_VERSION} clean package

# ---------------------------------------------------------------------------
# Runtime image
# ---------------------------------------------------------------------------
FROM eclipse-temurin:${JAVA_VERSION}-jre

RUN groupadd --system --gid 10001 thermobox \
 && useradd  --system --uid 10001 --gid thermobox --no-create-home \
             --shell /usr/sbin/nologin thermobox

WORKDIR /app

# RestServer.jar references base.jar through Class-Path: both must stay side by side.
COPY --from=build --chown=thermobox:thermobox /workspace/deploy/RestServer.jar /workspace/deploy/base.jar ./
COPY --chmod=755 docker/rest/entrypoint.sh /usr/local/bin/thermobox-entrypoint.sh
# A Windows checkout may have turned the script into CRLF: "/bin/sh^M: bad interpreter"
RUN sed -i 's/\r$//' /usr/local/bin/thermobox-entrypoint.sh

# thermobox_data volume: downloaded versions, backups and settings
RUN mkdir -p /var/lib/thermobox && chown thermobox:thermobox /var/lib/thermobox

USER thermobox

# No THERMOBOX_DB_* nor THERMOBOX_JWT_SECRET in the image: they are given at startup
# (docker-compose.yml / .env). An empty THERMOBOX_JWT_SECRET would crash the server
# (JwtService needs at least 32 bytes when it is set).
ENV THERMOBOX_HTTP_HOST=0.0.0.0 \
    THERMOBOX_HTTP_PORT=8080 \
    THERMOBOX_DATA_DIR=/var/lib/thermobox \
    JAVA_OPTS="-Xms64m -Xmx768m -XX:G1PeriodicGCInterval=60000 -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true"
# -Xmx768m: the server needs a few hundred MB; without a ceiling the JVM takes a share of the
# machine's memory and keeps it. G1PeriodicGCInterval gives unused memory back when idle.
# ExitOnOutOfMemoryError: a server short of memory stops (and is restarted) rather than limping on.

EXPOSE 8080

# Runs /app/RestServer.jar, or the version installed from the interface (updates/current),
# and restarts the server when it stops for an update.
ENTRYPOINT ["/usr/local/bin/thermobox-entrypoint.sh"]
