# syntax=docker/dockerfile:1.7
#
# JSF interface on WildFly -> deploy/JsfClient.war adapted and deployed as ROOT.war (URL: /)
# Build context: root of the repository (see docker-compose.yml)
#
# The bytecode of the project is Java 23: WildFly (shipped with JDK 17 in the official image) is
# therefore copied onto a Temurin 23 base instead of being used as is.
# If WildFly refuses to start on Java 23, move WILDFLY_IMAGE to a newer version.

ARG JAVA_VERSION=23
ARG WILDFLY_IMAGE=quay.io/wildfly/wildfly:36.0.1.Final-jdk21

# ---------------------------------------------------------------------------
# Build stage (the same in the 3 Dockerfiles, see docker/rest/Dockerfile)
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
COPY JSF ./JSF
COPY Base ./Base

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -Pdeploy -DskipTests -Dthermobox.version=${THERMOBOX_VERSION} clean package

# ---------------------------------------------------------------------------
# Adapting the WAR to WildFly (see prepare-war.sh for the details)
# ---------------------------------------------------------------------------
FROM build AS war
COPY docker/jsf-wildfly/prepare-war.sh /tmp/prepare-war.sh
RUN sh /tmp/prepare-war.sh /workspace/deploy/JsfClient.war /workspace/deploy/ROOT.war

# WildFly distribution (only used as a source of files)
FROM ${WILDFLY_IMAGE} AS wildfly-dist

# ---------------------------------------------------------------------------
# Runtime image: Temurin 23 + WildFly
# ---------------------------------------------------------------------------
FROM eclipse-temurin:${JAVA_VERSION}-jre

ENV JBOSS_HOME=/opt/jboss/wildfly

RUN groupadd --system --gid 10001 jboss \
 && useradd  --system --uid 10001 --gid jboss --no-create-home \
             --shell /usr/sbin/nologin jboss

COPY --from=wildfly-dist --chown=jboss:jboss /opt/jboss/wildfly ${JBOSS_HOME}

COPY docker/jsf-wildfly/configure.cli /tmp/configure.cli

USER jboss

# Server configuration at build time (no `|| true`: an error here must fail the build)
RUN ${JBOSS_HOME}/bin/jboss-cli.sh --file=/tmp/configure.cli \
 && rm -rf ${JBOSS_HOME}/standalone/configuration/standalone_xml_history

COPY --from=war --chown=jboss:jboss /workspace/deploy/ROOT.war ${JBOSS_HOME}/standalone/deployments/ROOT.war

# If you override JAVA_OPTS, keep these options: WildFly no longer adds its defaults once
# JAVA_OPTS is set (preferIPv4Stack and modules.system.pkgs in particular).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.net.preferIPv4Stack=true -Djboss.modules.system.pkgs=org.jboss.byteman -Djava.awt.headless=true"

EXPOSE 8080

# THERMOBOX_DB_URL / USER / PASSWORD and THERMOBOX_JWT_SECRET are given at startup
# (docker-compose.yml / .env): WildFly passes them to the JVM, PersistenceFactory reads them.
ENTRYPOINT ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0"]
