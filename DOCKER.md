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
- `jsf` : interface web JSF/Tomcat exposée sur le port 8081

## Fichiers ajoutés

- `Dockerfile` : image du backend REST
- `Dockerfile-jsf` : image de l’interface web JSF
- `docker-compose.yml` : orchestration locale
- `.dockerignore` : exclusion des artefacts générés

## Build et lancement

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
- Interface JSF : http://localhost:8081
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

L’image Docker de l’API charge ces fichiers pour exécuter le serveur.

## Interface JSF

Le fichier `Dockerfile-jsf` attend un WAR nommé :

```text
deploy/thermobox.war
```

Si le nom de votre artefact généré diffère, renommez-le avant de lancer le conteneur, ou ajustez la ligne suivante dans `Dockerfile-jsf` :

```dockerfile
COPY deploy/thermobox.war /usr/local/tomcat/webapps/ROOT.war
```

Exemple :

```bash
cp deploy/your-war-name.war deploy/thermobox.war
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
- Vérifiez la compatibilité Java 23 du build Maven avec votre environnement local et l’image Docker utilisée.

## Dépannage rapide

### Le backend ne démarre pas

Vérifiez :

```bash
docker compose logs restserver
```

Points fréquents :

- base de données MySQL non prête
- secret JWT absent
- mauvais JDBC URL
- version Java incompatible lors du build

### Le WAR JSF ne se charge pas

Vérifiez le nom du fichier dans `deploy/`, puis mettez-le en cohérence avec `Dockerfile-jsf`.

### MySQL inaccessible

Vérifiez la configuration :

```bash
docker compose logs mysql
```

## Commandes utiles

```bash
# rebuild complet

docker compose up --build

# voir les logs

docker compose logs -f restserver

docker compose logs -f jsf

# arrêter tout

docker compose down
```
