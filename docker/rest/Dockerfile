# syntax=docker/dockerfile:1.7
#
# Image du serveur REST (Jersey + Jetty) -> deploy/RestServer.jar + deploy/base.jar
# Contexte de build : racine du dépôt (voir docker-compose.yml)

ARG JAVA_VERSION=23

# ---------------------------------------------------------------------------
# Étape de build. Les 3 Dockerfiles (rest, jsf-tomcat, jsf-wildfly) ont EXACTEMENT
# la même étape : BuildKit la met en cache une seule fois pour les trois images.
# Si vous modifiez une ligne ici, modifiez-la aussi dans les deux autres.
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
# Image d'exécution
# ---------------------------------------------------------------------------
FROM eclipse-temurin:${JAVA_VERSION}-jre

RUN groupadd --system --gid 10001 thermobox \
 && useradd  --system --uid 10001 --gid thermobox --no-create-home \
             --shell /usr/sbin/nologin thermobox

WORKDIR /app

# RestServer.jar référence base.jar via Class-Path : les deux doivent rester côte à côte.
COPY --from=build --chown=thermobox:thermobox /workspace/deploy/RestServer.jar /workspace/deploy/base.jar ./
COPY --chmod=755 docker/rest/entrypoint.sh /usr/local/bin/thermobox-entrypoint.sh

# Volume thermobox_data (partagé avec l'interface JSF) : versions téléchargées et sauvegardes
RUN mkdir -p /var/lib/thermobox && chown thermobox:thermobox /var/lib/thermobox

USER thermobox

# Pas de THERMOBOX_DB_* ni THERMOBOX_JWT_SECRET dans l'image : ils sont fournis au démarrage
# (docker-compose.yml / .env). Un THERMOBOX_JWT_SECRET vide ferait planter le serveur
# (JwtService exige au moins 32 octets s'il est défini).
ENV THERMOBOX_HTTP_HOST=0.0.0.0 \
    THERMOBOX_HTTP_PORT=8080 \
    THERMOBOX_DATA_DIR=/var/lib/thermobox \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.awt.headless=true"

EXPOSE 8080

# Lance /app/RestServer.jar, ou la version installée depuis l'interface (updates/current),
# et relance le serveur quand il s'arrête pour une mise à jour.
ENTRYPOINT ["/usr/local/bin/thermobox-entrypoint.sh"]
