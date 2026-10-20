# ThermoBox avec Docker

## Démarrage rapide

```bash
sh scripts/init-env.sh                              # crée .env avec des secrets aléatoires
docker compose up -d --build                        # MySQL + REST
docker compose --profile tomcat up -d --build       # + interface JSF sur Tomcat
docker compose --profile wildfly up -d --build      # ... ou sur WildFly
```

Sans `openssl` (Windows hors Git Bash/WSL) : copiez `.env.example` en `.env` et remplissez les mots de passe
et `THERMOBOX_JWT_SECRET` (32 caractères minimum). Ajoutez aussi `.env` au `.gitignore`.

| Service | Profil | URL / port |
|---|---|---|
| REST | toujours | http://localhost:8080 |
| JSF sur Tomcat | `tomcat` | http://localhost:8081 |
| JSF sur WildFly | `wildfly` | http://localhost:8082 |
| MySQL | toujours | 127.0.0.1:3306 (machine locale uniquement) |
| Mosquitto (MQTT) | `mqtt` | localhost:1883 |

Les ports se changent dans `.env`. Les profils Tomcat et WildFly peuvent tourner ensemble (ports différents),
mais un seul suffit.

## Fichiers

```
docker-compose.yml               orchestration (profils tomcat / wildfly / mqtt)
.env.example                     modèle de configuration
.dockerignore
scripts/init-env.sh              génère .env
docker/rest/Dockerfile           REST : RestServer.jar + base.jar sur Temurin 23
docker/jsf-tomcat/Dockerfile     JSF : Tomcat 10.1 sur Temurin 23
docker/jsf-tomcat/setenv.sh      options JVM de Tomcat
docker/jsf-wildfly/Dockerfile    JSF : WildFly sur Temurin 23
docker/jsf-wildfly/prepare-war.sh  adapte le WAR à WildFly
docker/jsf-wildfly/configure.cli   configuration WildFly (page d'accueil, taille des uploads)
docker/mosquitto/mosquitto.conf  broker MQTT
```

Les trois Dockerfiles ont la même étape de build Maven : BuildKit ne la joue qu'une fois pour les trois images.
Si vous la modifiez, modifiez-la dans les trois.

## Points à connaître

- **Java 23 partout.** Le bytecode du projet est en Java 23 : toutes les images d'exécution sont basées sur
  Temurin 23 (Tomcat et WildFly y sont copiés depuis leurs images officielles). `ARG TOMCAT_IMAGE`,
  `ARG WILDFLY_IMAGE` et `ARG JAVA_VERSION` permettent de changer les versions.
- **Pas de datasource.** `PersistenceFactory` ouvre sa connexion JDBC avec `THERMOBOX_DB_URL/USER/PASSWORD` :
  Tomcat et WildFly n'ont donc rien à configurer côté base.
- **WildFly.** Le WAR contient Mojarra et Weld pour Tomcat. `prepare-war.sh` les retire (WildFly a les siens)
  et marque l'unité de persistance `jboss.as.jpa.managed=false` pour que WildFly ne tente pas de la gérer.
- **MQTT.** `BusinessFactory.MQTT = false` (driver simulé) et `MqttDriver` vise `tcp://localhost:1883` en dur :
  Mosquitto est optionnel tant que l'URL ne passe pas par une variable d'environnement.
- **Mode développement.** `web.xml` est en `PROJECT_STAGE=Development` : à passer en `Production` pour un
  déploiement réel. Les `schema-generation ... create` de `persistence.xml` sont à revoir pour la prod.

## Mises à jour depuis l'interface

Les versions installées depuis **Administration → Mises à jour** (ou `POST /api/updates/{version}/install`)
sont téléchargées dans le volume `thermobox_data`, partagé par `rest` et `jsf-tomcat`, avec les
sauvegardes de la base (`/var/lib/thermobox/backups`). Au démarrage, chaque conteneur prend la version
de `updates/current` si elle existe, sinon les fichiers de l'image : une version installée survit donc
à la recréation des conteneurs.

- `THERMOBOX_VERSION` (dans `.env`) inscrit la version dans les images construites ici. Laissée à `dev`,
  l'interface ne connaît pas sa version et traite toute mise à jour comme fonctionnelle.
- Reconstruire les images ne remplace pas une version installée depuis l'interface. Pour revenir aux
  fichiers des images : `docker compose exec rest rm /var/lib/thermobox/updates/current`, puis
  `docker compose --profile tomcat up -d --force-recreate`.
- WildFly : pas d'installation depuis l'interface (le WAR doit passer par `prepare-war.sh`).

## Commandes utiles

```bash
docker compose ps
docker compose logs -f rest
docker compose logs -f jsf-tomcat        # ou jsf-wildfly
docker compose --profile tomcat --profile wildfly --profile mqtt down       # arrêt
docker compose --profile tomcat --profile wildfly --profile mqtt down -v    # arrêt + suppression des données
```

## Dépannage

- `required variable ... is missing` : `.env` absent ou incomplet, lancez `scripts/init-env.sh`.
- Le REST s'arrête avec `THERMOBOX_JWT_SECRET must be at least 32 bytes long` : secret trop court.
- Le REST démarre mais n'est pas joignable : vérifiez que `Main.kt` contient la lecture de `THERMOBOX_HTTP_HOST`
  (sans elle, Jetty n'écoute que sur le loopback du conteneur).
- Erreur `UnsupportedClassVersionError` : le serveur tourne sur un JDK inférieur à 23.
