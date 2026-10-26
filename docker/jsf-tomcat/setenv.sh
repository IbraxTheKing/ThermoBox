#!/bin/sh

# Read by catalina.sh when Tomcat starts.
# To add options: CATALINA_OPTS variable in docker-compose.yml.

# Memory: 75 % of what the container is given
CATALINA_OPTS="${CATALINA_OPTS:-} -XX:MaxRAMPercentage=75"

# Room photos go through ImageIO: no display in a container
CATALINA_OPTS="$CATALINA_OPTS -Djava.awt.headless=true"

# Faster startup (no blocking on /dev/random)
CATALINA_OPTS="$CATALINA_OPTS -Djava.security.egd=file:/dev/./urandom"

export CATALINA_OPTS