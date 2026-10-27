#!/bin/sh
# Adapte deploy/JsfClient.war (conçu pour Tomcat) au déploiement sur WildFly.
# Utilisation : prepare-war.sh <JsfClient.war> <ROOT.war>
#
# Seul l'outil `jar` du JDK est utilisé (pas de zip/unzip dans l'image Maven).
#
# 1. Mojarra (Faces) et Weld (CDI) sont embarqués dans le WAR pour Tomcat, qui ne les fournit
#    pas. WildFly a les siens : garder ceux du WAR provoque des conflits de classes.
#    -> on retire jakarta.faces-*.jar et weld-servlet-shaded-*.jar.
#
# 2. L'application crée elle-même son EntityManagerFactory (PersistenceFactory, JDBC lu dans
#    les variables d'environnement). WildFly, lui, détecte le persistence.xml de base.jar et
#    tenterait de démarrer l'unité de persistance sans datasource, ce qui fait échouer le
#    déploiement.
#    -> on ajoute jboss.as.jpa.managed=false dans le persistence.xml de base.jar :
#       WildFly ne la gère plus, l'application la crée comme sous Tomcat.

[ "$#" -eq 2 ] || { echo "usage: prepare-war.sh <entree.war> <sortie.war>" >&2; exit 2; }

# Chemins absolus : le script change de répertoire pendant son exécution
IN="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
OUT="$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

mkdir "$WORK/war"
cd "$WORK/war"
jar xf "$IN"

# --- 1. Faces / CDI fournis par WildFly ------------------------------------------------
rm -f WEB-INF/lib/jakarta.faces-[0-9]*.jar WEB-INF/lib/weld-servlet-shaded-*.jar

# --- 2. Unité de persistance non gérée par le conteneur --------------------------------
BASE_JAR="$(ls WEB-INF/lib/base-*.jar 2>/dev/null | head -n 1 || true)"
if [ -z "$BASE_JAR" ]; then
    echo "prepare-war: base-*.jar introuvable dans WEB-INF/lib (le build a-t-il utilisé -Pdeploy ?)" >&2
    exit 1
fi

mkdir -p "$WORK/px"
cd "$WORK/px"
jar xf "$WORK/war/$BASE_JAR" META-INF/persistence.xml

if ! grep -q '</properties>' META-INF/persistence.xml; then
    echo "prepare-war: balise </properties> introuvable dans persistence.xml" >&2
    exit 1
fi

sed -i 's#</properties>#    <property name="jboss.as.jpa.managed" value="false"/>\n        </properties>#' \
    META-INF/persistence.xml

jar uf "$WORK/war/$BASE_JAR" -C "$WORK/px" META-INF/persistence.xml

# Vérification : le build échoue si la modification n'a pas été appliquée
cd "$WORK"
mkdir check
cd check
jar xf "$WORK/war/$BASE_JAR" META-INF/persistence.xml
grep -q 'jboss.as.jpa.managed' META-INF/persistence.xml

# --- Reconstruction du WAR -------------------------------------------------------------
cd "$WORK/war"
rm -f META-INF/MANIFEST.MF
jar --create --file "$OUT" -C "$WORK/war" .

echo "prepare-war: $OUT prêt"
