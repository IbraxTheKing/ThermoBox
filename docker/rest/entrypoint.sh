#!/bin/sh
# Starts the REST server on the version installed from the interface (updates/current, in the
# thermobox_data volume), otherwise on the files of the image.
#
# To move to a new version, the server stops with exit code 75: the loop starts it again at
# once on updates/current, without restarting the container.
#
# A new image wins over a version installed from the interface: when the image version
# (THERMOBOX_IMAGE_VERSION) is not the one seen at the previous start (updates/image), someone
# pulled or built another image on purpose, so updates/current is dropped (the files of that version
# stay in updates/<version>). A "dev" image (built locally without a version) never does.

DATA="${THERMOBOX_DATA_DIR:-/var/lib/thermobox}"
# Tells the server that someone restarts it (see RestComponent).
export THERMOBOX_RESTART=auto

IMAGE="${THERMOBOX_IMAGE_VERSION:-dev}"
if [ "$IMAGE" != "dev" ] && mkdir -p "$DATA/updates" 2>/dev/null; then
    seen="$(cat "$DATA/updates/image" 2>/dev/null || true)"
    if [ "$seen" != "$IMAGE" ]; then
        installed="$(cat "$DATA/updates/current" 2>/dev/null || true)"
        if [ -n "$seen" ] && [ -n "$installed" ]; then
            echo "ThermoBox: new image $IMAGE (previous: $seen), used instead of $installed installed from the interface"
            rm -f "$DATA/updates/current"
        fi
        echo "$IMAGE" > "$DATA/updates/image"
    fi
fi

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
