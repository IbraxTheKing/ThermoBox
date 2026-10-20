#!/bin/sh
# Lance le serveur REST hors Docker, en le relançant quand il s'arrête pour une mise à jour
# installée depuis l'interface (code 75). À utiliser à la place de « java -jar RestServer.jar »,
# par exemple dans un service systemd :
#
#   ExecStart=/opt/thermobox/scripts/thermobox-rest.sh /opt/thermobox/deploy
#
# Utilisation : thermobox-rest.sh [dossier contenant RestServer.jar et base.jar]  (défaut : ./deploy)
# Les variables THERMOBOX_* (base, JWT, THERMOBOX_DATA_DIR…) sont lues dans l'environnement.

DEPLOY="${1:-deploy}"
DATA="${THERMOBOX_DATA_DIR:-$HOME/.thermobox}"
export THERMOBOX_DATA_DIR="$DATA"
export THERMOBOX_RESTART=auto

pid=""
trap 'if [ -n "$pid" ]; then kill -TERM "$pid" 2>/dev/null; wait "$pid"; fi; exit 143' TERM INT

while true; do
    JAR="$DEPLOY/RestServer.jar"
    v="$(cat "$DATA/updates/current" 2>/dev/null || true)"
    if [ -n "$v" ] && [ -f "$DATA/updates/$v/RestServer.jar" ] && [ -f "$DATA/updates/$v/base.jar" ]; then
        JAR="$DATA/updates/$v/RestServer.jar"
        echo "ThermoBox : version $v, installée depuis l'interface ($JAR)"
    fi
    # shellcheck disable=SC2086
    java ${JAVA_OPTS:-} -jar "$JAR" &
    pid=$!
    wait "$pid"
    code=$?
    pid=""
    [ "$code" -eq 75 ] || exit "$code"
    echo "ThermoBox : redémarrage pour la mise à jour"
done
