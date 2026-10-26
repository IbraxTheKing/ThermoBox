#!/bin/sh
# Starts the REST server on the version installed from the interface (updates/current, in the
# thermobox_data volume), otherwise on the files of the image.
#
# To move to a new version, the server stops with exit code 75: the loop starts it again at
# once on updates/current, without restarting the container.

DATA="${THERMOBOX_DATA_DIR:-/var/lib/thermobox}"
# Tells the server that someone restarts it (see RestComponent).
export THERMOBOX_RESTART=auto

pid=""
trap 'if [ -n "$pid" ]; then kill -TERM "$pid" 2>/dev/null; wait "$pid"; fi; exit 143' TERM INT

while true; do
    JAR=/app/RestServer.jar
    v="$(cat "$DATA/updates/current" 2>/dev/null || true)"
    if [ -n "$v" ] && [ -f "$DATA/updates/$v/RestServer.jar" ] && [ -f "$DATA/updates/$v/base.jar" ]; then
        JAR="$DATA/updates/$v/RestServer.jar"
        echo "ThermoBox: version $v, installed from the interface ($JAR)"
    fi
    # shellcheck disable=SC2086
    java $JAVA_OPTS -jar "$JAR" &
    pid=$!
    wait "$pid"
    code=$?
    pid=""
    [ "$code" -eq 75 ] || exit "$code"
    echo "ThermoBox: restarting for the update"
done
