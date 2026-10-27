ARG JAVA_VERSION=23
ARG TOMCAT_IMAGE=tomcat:10.1-jdk17

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

FROM ${TOMCAT_IMAGE} AS tomcat-dist

FROM eclipse-temurin:${JAVA_VERSION}-jre

ENV CATALINA_HOME=/usr/local/tomcat
ENV PATH=${CATALINA_HOME}/bin:${PATH}

RUN groupadd --system --gid 10001 tomcat \
    && useradd --system --uid 10001 --gid tomcat \
       --no-create-home --shell /usr/sbin/nologin tomcat

COPY --from=tomcat-dist --chown=tomcat:tomcat /usr/local/tomcat ${CATALINA_HOME}

WORKDIR ${CATALINA_HOME}

RUN rm -rf webapps/* webapps.dist

COPY docker/jsf-tomcat/setenv.sh ${CATALINA_HOME}/bin/setenv.sh
COPY --chmod=755 docker/jsf-tomcat/entrypoint.sh /usr/local/bin/thermobox-entrypoint.sh
# A Windows checkout may have turned the script into CRLF: "/bin/sh^M: bad interpreter"
RUN sed -i 's/\r$//' /usr/local/bin/thermobox-entrypoint.sh

# Volume thermobox_data (partagé avec le serveur REST) : versions téléchargées et sauvegardes
RUN mkdir -p /var/lib/thermobox && chown tomcat:tomcat /var/lib/thermobox
ENV THERMOBOX_DATA_DIR=/var/lib/thermobox \
    THERMOBOX_WAR_PATH=${CATALINA_HOME}/webapps/ROOT.war

RUN sed -i 's/\r$//' ${CATALINA_HOME}/bin/setenv.sh \
    && chmod 755 ${CATALINA_HOME}/bin/setenv.sh

COPY --from=build --chown=tomcat:tomcat \
    /workspace/deploy/JsfClient.war \
    ${CATALINA_HOME}/webapps/ROOT.war

USER tomcat

EXPOSE 8080

# Remet la version installée depuis l'interface (updates/current), puis lance Tomcat
CMD ["/usr/local/bin/thermobox-entrypoint.sh"]