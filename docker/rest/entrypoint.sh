#!/bin/sh
# Démarre le serveur REST sur la version installée depuis l'interface (updates/current, dans le
# volume thermobox_data), sinon sur les fichiers de l'image.
#
# Pour passer à une nouvelle version, le serveur s'arrête avec le code 75 : la boucle le relance
# aussitôt sur updates/current, sans redémarrer le conteneur.

DATA="${THERMOBOX_DATA_DIR:-/var/lib/thermobox}"
# Tells the server that someone restarts it (see ComposantRest).
export THERMOBOX_RESTART=auto

pid=""
trap 'if [ -n "$pid" ]; then kill -TERM "$pid" 2>/dev/null; wait "$pid"; fi; exit 143' TERM INT

while true; do
    JAR=/app/RestServer.jar
    v="$(cat "$DATA/updates/current" 2>/dev/null || true)"
    if [ -n "$v" ] && [ -f "$DATA/updates/$v/RestServer.jar" ] && [ -f "$DATA/updates/$v/base.jar" ]; then
        JAR="$DATA/updates/$v/RestServer.jar"
        echo "ThermoBox : version $v, installée depuis l'interface ($JAR)"
    fi
    # shellcheck disable=SC2086
    java $JAVA_OPTS -jar "$JAR" &
    pid=$!
    wait "$pid"
    code=$?
    pid=""
    [ "$code" -eq 75 ] || exit "$code"
    echo "ThermoBox : redémarrage pour la mise à jour"
done
