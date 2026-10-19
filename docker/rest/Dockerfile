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

COPY pom.xml ./
COPY ibr-utils ./ibr-utils
COPY Common ./Common
COPY Persistence ./Persistence
COPY Business ./Business
COPY RestServer ./RestServer
COPY JSF ./JSF
COPY Base ./Base

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -Pdeploy -DskipTests clean package

# ---------------------------------------------------------------------------
# Image d'exécution
# ---------------------------------------------------------------------------
FROM eclipse-temurin:${JAVA_VERSION}-jre-jammy

RUN groupadd --system --gid 10001 thermobox \
 && useradd  --system --uid 10001 --gid thermobox --no-create-home \
             --shell /usr/sbin/nologin thermobox

WORKDIR /app

# RestServer.jar référence base.jar via Class-Path : les deux doivent rester côte à côte.
COPY --from=build --chown=thermobox:thermobox /workspace/deploy/RestServer.jar /workspace/deploy/base.jar ./

USER thermobox

# Pas de THERMOBOX_DB_* ni THERMOBOX_JWT_SECRET dans l'image : ils sont fournis au démarrage
# (docker-compose.yml / .env). Un THERMOBOX_JWT_SECRET vide ferait planter le serveur
# (JwtService exige au moins 32 octets s'il est défini).
ENV THERMOBOX_HTTP_HOST=0.0.0.0 \
    THERMOBOX_HTTP_PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.awt.headless=true"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/RestServer.jar"]
