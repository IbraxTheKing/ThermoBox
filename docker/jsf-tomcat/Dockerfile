ARG JAVA_VERSION=23
ARG TOMCAT_IMAGE=tomcat:10.1-jdk17

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

COPY --from=build --chown=tomcat:tomcat \
    /workspace/deploy/JsfClient.war \
    ${CATALINA_HOME}/webapps/ROOT.war

USER tomcat

EXPOSE 8080

CMD ["catalina.sh", "run"]