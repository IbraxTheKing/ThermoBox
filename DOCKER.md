# Déploiement Docker pour ThermoBox

Ce document décrit le déploiement local et de démonstration du projet ThermoBox avec Docker.

## Prérequis

- Docker
- Docker Compose
- Git

## Stack fournie

Le fichier `docker-compose.yml` démarre :

- `mysql` : base de données MySQL 8.1
- `mosquitto` : broker MQTT
- `restserver` : backend Java/Kotlin exposé sur le port 8080
- `jsf` : interface web JSF/Tomcat exposée sur le port 8081 (par défaut)

## Fichiers ajoutés

- `Dockerfile` : image du backend REST
- `Dockerfile-jsf` : image de l’interface web JSF (Tomcat)
- `Dockerfile-jsf-wildfly` : alternative pour WildFly (Jakarta EE)
- `docker-compose.yml` : orchestration locale
- `.dockerignore` : exclusion des artefacts générés

## Build et lancement (démo / local)

Depuis la racine du projet :

```bash
docker compose build
docker compose up -d
```

Vérification :

```bash
docker compose ps
```

Points d’accès :

- API REST : http://localhost:8080
- Interface JSF (Tomcat) : http://localhost:8081
- MySQL : localhost:3306
- MQTT : localhost:1883

## Variables d’environnement

Les services utilisent les variables suivantes :

```env
THERMOBOX_DB_URL=jdbc:mysql://mysql:3306/thermobox
THERMOBOX_DB_USER=thermobox
THERMOBOX_DB_PASSWORD=changeit
THERMOBOX_JWT_SECRET=replace-with-a-strong-secret
THERMOBOX_JWT_TTL_MINUTES=60
```

Le backend est configuré pour lire ces variables au démarrage.

## Build Maven avant Docker (si nécessaire)

Le backend utilise le profil Maven `deploy` pour produire les artefacts de déploiement :

```bash
mvn -Pdeploy -DskipTests clean package
```

Cela génère notamment :

- `deploy/base.jar`
- `deploy/RestServer.jar`
- `deploy/thermobox.war` (module JSF) — le nom du WAR peut varier selon votre configuration ; ajustez-le si besoin.

L’image Docker de l’API charge ces fichiers pour exécuter le serveur.

## Interface JSF : compatibilité Tomcat et WildFly

Le projet JSF est écrit pour Jakarta Faces (Jakarta EE). Deux déploiements sont possibles : Tomcat 10.1 (conteneur de servlets) ou WildFly (serveur Jakarta EE complet).

- `Dockerfile-jsf` (par défaut) construit une image basée sur Tomcat 10.1 : il suffit de fournir `deploy/thermobox.war` et Tomcat déploiera l’application.

- `Dockerfile-jsf-wildfly` (optionnel) construit une image WildFly qui :
  - télécharge le driver JDBC MySQL depuis Maven Central (par ex. la version utilisée dans le projet)
  - installe le driver comme module WildFly
  - copie le WAR dans le dossier `standalone/deployments/` pour déploiement automatique

### Notes pratiques pour WildFly

1. Datasource

   WildFly ne connaîtra pas automatiquement une datasource nommée `thermobox` : vous devez configurer une datasource via la console d'administration, un script CLI (`jboss-cli.sh`) ou en ajoutant la configuration `standalone.xml` adaptée.

   Exemple CLI (à adapter aux identifiants et à la version WildFly) :

   ```bash
   /subsystem=datasources/jdbc-driver=mysql:add(driver-name=mysql,driver-module-name=com.mysql,driver-class-name=com.mysql.cj.jdbc.Driver)
   data-source add --name=ThermoBoxDS --driver-name=mysql --jndi-name=java:/jdbc/ThermoBoxDS --connection-url=jdbc:mysql://mysql:3306/thermobox --user-name=thermobox --password=changeit --use-ccm=true --max-pool-size=20 --blocking-timeout-wait-millis=5000
   ```

   Ajustez le `jndi-name` et la configuration selon le code de votre application (le projet utilise des variables d'environnement JDBC dans le code pour l'accès JPA ; vous pouvez conserver un driver + datasource standard). 

2. Driver JDBC

   `Dockerfile-jsf-wildfly` inclut une étape qui récupère le jar MySQL depuis Maven Central et installe un module WildFly `com.mysql` avec un `module.xml` minimal.

3. Versions

   - Tomcat 10.1 et WildFly 30+ sont compatibles Jakarta EE 10 (Jakarta Faces 4) — c’est le stack ciblé par le module JSF (voir JSF/pom-snippet.xml).
   - Vérifiez la version de Java requise pour le WAR (le build Maven indique Java 17/23 dans différents modules). WildFly image choisie embarque OpenJDK 17 ; si votre WAR a besoin de Java 23, adaptez l’image.

## Construire l’image JSF WildFly (exemple)

Depuis la racine du repo :

```bash
# Construire les artefacts avec Maven
mvn -Pdeploy -DskipTests clean package

# Construire l'image WildFly (téléchargera le driver JDBC au build)
docker build -f Dockerfile-jsf-wildfly -t thermobox-jsf-wildfly .

# Lancer (exemple minimal)
docker run --name thermobox-jsf-wildfly --link thermobox-mysql:mysql -p 8081:8080 thermobox-jsf-wildfly
```

## Arrêt

```bash
docker compose down
```

Pour supprimer aussi les volumes persistants :

```bash
docker compose down -v
```

## Bonnes pratiques

- Ne jamais stocker les secrets dans le dépôt.
- En production, utilisez des secrets Docker / variables d’environnement sécurisées.
- Le mode `create` de JPA est utile pour le développement, mais il faut le vérifier avant un environnement partagé ou de production.
- Pour WildFly en production, préférez fournir la datasource via un module d’infra (ou via configuration Kubernetes/Ansible) plutôt que de l’auto-configurer dans l’image.

## Dépannage rapide

Consultez les logs :

```bash
docker compose logs -f restserver
docker compose logs -f jsf
```

---

(ajouté : instructions pour déployer JSF sur Tomcat et WildFly)
