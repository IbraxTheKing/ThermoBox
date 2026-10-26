#!/bin/sh
# Adapts deploy/JsfClient.war (made for Tomcat) to a WildFly deployment.
# Utilisation : prepare-war.sh <JsfClient.war> <ROOT.war>
#
# Only the JDK's `jar` tool is used (no zip/unzip in the Maven image).
#
# 1. Mojarra (Faces) and Weld (CDI) are bundled in the WAR for Tomcat, which doesn't provide
#    them. WildFly has its own: keeping the WAR's causes class conflicts.
#    -> jakarta.faces-*.jar and weld-servlet-shaded-*.jar are removed.
#
# 2. The application creates its EntityManagerFactory itself (PersistenceFactory, JDBC read from
#    the environment variables). WildFly detects the persistence.xml of base.jar and would try
#    to start the persistence unit without a datasource, which fails the deployment.
#    -> jboss.as.jpa.managed=false is added to the persistence.xml of base.jar: WildFly no
#       longer manages it, the application creates it as under Tomcat.

[ "$#" -eq 2 ] || { echo "usage: prepare-war.sh <entree.war> <sortie.war>" >&2; exit 2; }

# Absolute paths: the script changes directory while it runs
IN="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
OUT="$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

mkdir "$WORK/war"
cd "$WORK/war"
jar xf "$IN"

# --- 1. Faces / CDI fournis par WildFly ------------------------------------------------
rm -f WEB-INF/lib/jakarta.faces-[0-9]*.jar WEB-INF/lib/weld-servlet-shaded-*.jar

# --- 2. Persistence unit not managed by the container ---------------------------------
BASE_JAR="$(ls WEB-INF/lib/base-*.jar 2>/dev/null | head -n 1 || true)"
if [ -z "$BASE_JAR" ]; then
    echo "prepare-war: base-*.jar not found in WEB-INF/lib (did the build use -Pdeploy?)" >&2
    exit 1
fi

mkdir -p "$WORK/px"
cd "$WORK/px"
jar xf "$WORK/war/$BASE_JAR" META-INF/persistence.xml

if ! grep -q '</properties>' META-INF/persistence.xml; then
    echo "prepare-war: no </properties> tag in persistence.xml" >&2
    exit 1
fi

sed -i 's#</properties>#    <property name="jboss.as.jpa.managed" value="false"/>\n        </properties>#' \
    META-INF/persistence.xml

jar uf "$WORK/war/$BASE_JAR" -C "$WORK/px" META-INF/persistence.xml

# Check: the build fails if the change was not applied
cd "$WORK"
mkdir check
cd check
jar xf "$WORK/war/$BASE_JAR" META-INF/persistence.xml
grep -q 'jboss.as.jpa.managed' META-INF/persistence.xml

# --- Rebuilding the WAR ----------------------------------------------------------------
cd "$WORK/war"
rm -f META-INF/MANIFEST.MF
jar --create --file "$OUT" -C "$WORK/war" .

echo "prepare-war: $OUT ready"
