#!/bin/sh
# Remet en place la version de l'interface installée depuis l'interface (updates/current, dans le
# volume thermobox_data) avant de lancer Tomcat : l'image, elle, contient toujours le WAR
# d'origine, et un conteneur recréé repartirait sinon sur celui-ci.
set -eu

DATA="${THERMOBOX_DATA_DIR:-/var/lib/thermobox}"
WAR="${THERMOBOX_WAR_PATH:-$CATALINA_HOME/webapps/ROOT.war}"

v="$(cat "$DATA/updates/current" 2>/dev/null || true)"
if [ -n "$v" ] && [ -f "$DATA/updates/$v/JsfClient.war" ] && ! cmp -s "$DATA/updates/$v/JsfClient.war" "$WAR"; then
    cp "$DATA/updates/$v/JsfClient.war" "$WAR.tmp"
    mv -f "$WAR.tmp" "$WAR"
    # Unpacked copy of the previous version
    rm -rf "${WAR%.war}"
    echo "ThermoBox : interface en version $v, installée depuis l'interface"
fi

exec catalina.sh run
