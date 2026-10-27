#!/bin/sh

# Lu automatiquement par catalina.sh au démarrage de Tomcat.
# Pour ajouter des options : variable CATALINA_OPTS dans docker-compose.yml.

# Mémoire : 75 % de celle allouée au conteneur
CATALINA_OPTS="${CATALINA_OPTS:-} -XX:MaxRAMPercentage=75"

# Les photos de salles passent par ImageIO : pas d'écran dans un conteneur
CATALINA_OPTS="$CATALINA_OPTS -Djava.awt.headless=true"

# Démarrage plus rapide (pas de blocage sur /dev/random)
CATALINA_OPTS="$CATALINA_OPTS -Djava.security.egd=file:/dev/./urandom"

export CATALINA_OPTS