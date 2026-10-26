# ThermoBox avec Docker

## Démarrage rapide

```bash
sh scripts/init-env.sh                              # crée .env avec des secrets aléatoires
docker compose up -d --build                        # MySQL + REST
docker compose --profile mqtt up -d --build         # + broker MQTT pour de vrais boîtiers
```

Sans `openssl` (Windows hors Git Bash/WSL) : copiez `.env.example` en `.env` et remplissez les mots de passe
et `THERMOBOX_JWT_SECRET` (32 caractères minimum). Ajoutez aussi `.env` au `.gitignore`.

| Service | Profil | URL / port |
|---|---|---|
| REST | toujours | http://localhost:8080 |
| MySQL | toujours | 127.0.0.1:3306 (machine locale uniquement) |
| Mosquitto (MQTT) | `mqtt` | localhost:1883 |

Les ports se changent dans `.env`.

L'interface est le client Angular [Thermobox-Ng-Client](https://github.com/IbraxTheKing/Thermobox-Ng-Client)
(branche `dev` pour l'instant), déployé à part avec `http://localhost:8080` comme API. L'interface JSF et
ses profils `tomcat` et `wildfly` ont été supprimés en 26.12 : après la mise à jour, arrêtez les anciens
conteneurs avec `docker compose --profile tomcat --profile wildfly down --remove-orphans`.

## Fichiers

```
docker-compose.yml               orchestration (profil mqtt)
.env.example                     modèle de configuration
.dockerignore
scripts/init-env.sh              génère .env
docker/rest/Dockerfile           REST : build Maven, puis RestServer.jar + base.jar sur Temurin 23
docker/rest/entrypoint.sh        lance le serveur et le relance après une mise à jour (code 75)
docker/mosquitto/mosquitto.conf  broker MQTT
```

## Points à connaître

- **Java 23.** Le bytecode du projet est en Java 23 : l'image d'exécution est basée sur Temurin 23.
  `ARG JAVA_VERSION` permet de changer la version.
- **Pas de datasource.** `PersistenceFactory` ouvre sa connexion JDBC avec `THERMOBOX_DB_URL/USER/PASSWORD`.
- **MQTT.** `THERMOBOX_DRIVER=simulated` (défaut) simule les boîtiers ; avec `mqtt`, le serveur se connecte à
  `THERMOBOX_MQTT_URL` (le service `mosquitto` du profil `mqtt`, ou votre broker).
- **Schéma.** Créé et mis à jour au démarrage par les migrations Flyway (voir le README, « Migrations de
  schéma ») : rien à faire à la main, même sur une base existante.

## Mises à jour depuis l'interface

Les versions installées depuis **Administration → Mises à jour** (ou `POST /api/v2/updates/{version}/install`)
sont téléchargées dans le volume `thermobox_data` (`/var/lib/thermobox`), avec les sauvegardes de la base
(`backups/`) et les réglages des administrateurs (logo, limites de température, style). Au démarrage, le
conteneur prend la version de `updates/current` si elle existe, sinon les fichiers de l'image : une version
installée survit donc à la recréation du conteneur.

- `THERMOBOX_VERSION` (dans `.env`) inscrit la version dans l'image construite ici. Laissée à `dev`,
  le serveur ne connaît pas sa version et traite toute mise à jour comme fonctionnelle.
- Reconstruire l'image ne remplace pas une version installée depuis l'interface. Pour revenir aux
  fichiers de l'image : `docker compose exec rest rm /var/lib/thermobox/updates/current`, puis
  `docker compose up -d --force-recreate rest`.

## Commandes utiles

```bash
docker compose ps
docker compose logs -f rest
docker compose --profile mqtt down       # arrêt
docker compose --profile mqtt down -v    # arrêt + suppression des données
```

## Dépannage

- `required variable ... is missing` : `.env` absent ou incomplet, lancez `scripts/init-env.sh`.
- Le REST s'arrête avec `THERMOBOX_JWT_SECRET must be at least 32 bytes long` : secret trop court.
- Le REST démarre mais n'est pas joignable : vérifiez que `Main.kt` contient la lecture de `THERMOBOX_HTTP_HOST`
  (sans elle, Jetty n'écoute que sur le loopback du conteneur).
- Erreur `UnsupportedClassVersionError` : le serveur tourne sur un JDK inférieur à 23.
