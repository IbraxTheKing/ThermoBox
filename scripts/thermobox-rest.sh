#!/bin/sh
# Runs the REST server outside Docker, and starts it again when it stops for an update installed
# from the interface (exit code 75). Use it instead of "java -jar RestServer.jar", for example in a
# systemd service:
#
#   ExecStart=/opt/thermobox/scripts/thermobox-rest.sh /opt/thermobox/deploy
#
# Usage: thermobox-rest.sh [folder holding RestServer.jar and base.jar]  (default: ./deploy)
# The THERMOBOX_* variables (database, JWT, THERMOBOX_DATA_DIR…) are read from the environment.

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
        echo "ThermoBox: version $v, installed from the interface ($JAR)"
    fi
    # shellcheck disable=SC2086
    java ${JAVA_OPTS:-} -jar "$JAR" &
    pid=$!
    wait "$pid"
    code=$?
    pid=""
    [ "$code" -eq 75 ] || exit "$code"
    echo "ThermoBox: restarting for the update"
done
