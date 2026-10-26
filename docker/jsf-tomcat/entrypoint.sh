#!/bin/sh
# Puts back the version of the interface installed from the interface (updates/current, in the
# thermobox_data volume) before starting Tomcat: the image always holds the original WAR, and a
# container created again would otherwise start from it.
set -eu

DATA="${THERMOBOX_DATA_DIR:-/var/lib/thermobox}"
WAR="${THERMOBOX_WAR_PATH:-$CATALINA_HOME/webapps/ROOT.war}"

v="$(cat "$DATA/updates/current" 2>/dev/null || true)"
if [ -n "$v" ] && [ -f "$DATA/updates/$v/JsfClient.war" ] && ! cmp -s "$DATA/updates/$v/JsfClient.war" "$WAR"; then
    cp "$DATA/updates/$v/JsfClient.war" "$WAR.tmp"
    mv -f "$WAR.tmp" "$WAR"
    # Unpacked copy of the previous version
    rm -rf "${WAR%.war}"
    echo "ThermoBox: interface in version $v, installed from the interface"
fi

exec catalina.sh run
