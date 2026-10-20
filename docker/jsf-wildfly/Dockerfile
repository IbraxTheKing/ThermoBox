# syntax=docker/dockerfile:1.7
#
# Interface JSF sur WildFly -> deploy/JsfClient.war adapté et déployé en ROOT.war (URL : /)
# Contexte de build : racine du dépôt (voir docker-compose.yml)
#
# Le bytecode du projet est en Java 23 : WildFly (livré en JDK 17 dans l'image officielle)
# est donc copié sur une base Temurin 23 au lieu d'être utilisé tel quel.
# Si WildFly refuse de démarrer sur Java 23, montez WILDFLY_IMAGE vers une version plus récente.

ARG JAVA_VERSION=23
ARG WILDFLY_IMAGE=quay.io/wildfly/wildfly:36.0.1.Final-jdk21

# ---------------------------------------------------------------------------
# Étape de build (identique dans les 3 Dockerfiles, voir docker/rest/Dockerfile)
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
# Adaptation du WAR pour WildFly (voir prepare-war.sh pour le détail)
# ---------------------------------------------------------------------------
FROM build AS war
COPY docker/jsf-wildfly/prepare-war.sh /tmp/prepare-war.sh
RUN sh /tmp/prepare-war.sh /workspace/deploy/JsfClient.war /workspace/deploy/ROOT.war

# Distribution WildFly (sert uniquement de source de fichiers)
FROM ${WILDFLY_IMAGE} AS wildfly-dist

# ---------------------------------------------------------------------------
# Image d'exécution : Temurin 23 + WildFly
# ---------------------------------------------------------------------------
FROM eclipse-temurin:${JAVA_VERSION}-jre

ENV JBOSS_HOME=/opt/jboss/wildfly

RUN groupadd --system --gid 10001 jboss \
 && useradd  --system --uid 10001 --gid jboss --no-create-home \
             --shell /usr/sbin/nologin jboss

COPY --from=wildfly-dist --chown=jboss:jboss /opt/jboss/wildfly ${JBOSS_HOME}

COPY docker/jsf-wildfly/configure.cli /tmp/configure.cli

USER jboss

# Configuration du serveur au build (sans `|| true` : une erreur ici doit faire échouer le build)
RUN ${JBOSS_HOME}/bin/jboss-cli.sh --file=/tmp/configure.cli \
 && rm -rf ${JBOSS_HOME}/standalone/configuration/standalone_xml_history

COPY --from=war --chown=jboss:jboss /workspace/deploy/ROOT.war ${JBOSS_HOME}/standalone/deployments/ROOT.war

# Si vous surchargez JAVA_OPTS, gardez ces options : WildFly n'ajoute plus ses valeurs par défaut
# dès que JAVA_OPTS est défini (preferIPv4Stack et modules.system.pkgs notamment).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.net.preferIPv4Stack=true -Djboss.modules.system.pkgs=org.jboss.byteman -Djava.awt.headless=true"

EXPOSE 8080

# THERMOBOX_DB_URL / USER / PASSWORD et THERMOBOX_JWT_SECRET sont fournis au démarrage
# (docker-compose.yml / .env) : WildFly les transmet à la JVM, PersistenceFactory les lit.
ENTRYPOINT ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0"]
