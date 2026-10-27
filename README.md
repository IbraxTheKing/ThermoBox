# ThermoBox

ThermoBox est une application de supervision thermique permettant la gestion des températures dans des infrastructures complexes telles que des bâtiments ou des salles serveurs. Le système permet d'organiser des espaces hiérarchiques, de suivre les mesures en temps réel et de gérer des consignes dynamiques.

> [!IMPORTANT]
> **L'interface JSF a été supprimée en 26.12.** L'interface de ThermoBox est le client Angular
> [Thermobox-Ng-Client](https://github.com/IbraxTheKing/Thermobox-Ng-Client)
> (branche [`dev`](https://github.com/IbraxTheKing/Thermobox-Ng-Client/tree/dev) pour l'instant),
> qui passe par l'API REST de ce serveur. Voir [Suppression de l'interface JSF](#suppression-de-linterface-jsf-2612).

## Quick Start

### Prérequis
- JDK 23
- Maven 3.9+
- MySQL 8 ou MariaDB

### Installation & Configuration
1. Clonez le dépôt :
   ```bash
   git clone https://github.com/IbraxTheKing/ThermoBox.git
   cd ThermoBox
   ```

2. Configurez les variables d'environnement nécessaires (exemple pour Linux/macOS) :
   ```bash
   export THERMOBOX_DB_URL='jdbc:mysql://localhost:3306/thermobox'
   export THERMOBOX_DB_USER='votre_utilisateur'
   export THERMOBOX_DB_PASSWORD='votre_mot_de_passe'
   export THERMOBOX_JWT_SECRET='une_cle_secrete_de_32_octets_minimum'
   ```

3. Compilez et installez les modules :
   ```bash
   mvn clean install
   ```

### Lancement de l'API REST
Pour lancer le serveur API, exécutez la classe principale du module `RestServer` depuis votre IDE ou via Maven en ciblant le module spécifique. Le serveur est accessible sur :
- URL : http://localhost:8080/
- Préfixe API : /api/v2 (voir [RestServer/README.md](RestServer/README.md))

## Configuration (variables d'environnement)

Toute la configuration de production passe par des variables d'environnement : aucun secret ni
réglage propre à une installation n'est dans le code ou dans les fichiers du projet. Avec Docker,
elles viennent de `.env` (voir `.env.example` et [DOCKER.md](DOCKER.md)).

| Variable | Défaut | Rôle |
|---|---|---|
| `THERMOBOX_DB_URL` | `jdbc:mysql://localhost:3306/thermobox` | Base MySQL |
| `THERMOBOX_DB_USER` / `THERMOBOX_DB_PASSWORD` | *obligatoires* | Identifiants de la base |
| `THERMOBOX_DB_MIGRATE` | `true` | `false` : pas de migration de schéma au démarrage |
| `THERMOBOX_DB_POOL_SIZE` | `20` | Connexions à la base au plus en même temps ; les autres requêtes attendent leur tour |
| `THERMOBOX_DB_ACCESS` | `jdbc` | Accès à la base : `jdbc` (SQL direct, défaut depuis 26.12) ou `jpa` (EclipseLink, le retour à l'accès de 26.11), voir [Persistence](Persistence/README.md#jpa-ou-jdbc-thermobox_db_access) |
| `THERMOBOX_JWT_SECRET` | *obligatoire* | Clé de signature des jetons (32 caractères minimum) |
| `THERMOBOX_JWT_TTL_MINUTES` | `60` | Durée de vie d'un jeton |
| `THERMOBOX_DRIVER` | `simulated` | `simulated` (boîtiers simulés) ou `mqtt` (vrais boîtiers) ; l'ancienne valeur `simule` est acceptée |
| `THERMOBOX_MQTT_URL` | `tcp://localhost:1883` | Broker MQTT (`ssl://hôte:8883` pour TLS) |
| `THERMOBOX_MQTT_USER` / `THERMOBOX_MQTT_PASSWORD` | *aucun* | Identifiants du broker, si demandés |
| `THERMOBOX_HTTP_HOST` / `THERMOBOX_HTTP_PORT` | `localhost` / `8080` | Écoute du serveur REST |
| `THERMOBOX_DATA_DIR` | `~/.thermobox` | Téléchargements et sauvegardes des mises à jour, réglages choisis par un administrateur (style d'affichage, logo, limites de température) |
| `THERMOBOX_UPDATE_CHANNEL` / `_CHECK` / `THERMOBOX_GITHUB_TOKEN` | `stable` / `true` / *aucun* | Mises à jour (voir plus bas) |

Une valeur invalide (`THERMOBOX_DRIVER=modbus`, une URL MQTT mal formée) arrête le démarrage avec un message clair plutôt que de repartir sur une valeur par défaut.

## Suppression de l'interface JSF (26.12)

L'interface web JSF faisait double emploi avec le client Angular
[Thermobox-Ng-Client](https://github.com/IbraxTheKing/Thermobox-Ng-Client), qui couvre les mêmes
fonctions à travers l'API REST. Dépréciée en 26.11, elle est supprimée en 26.12 : le module `JSF`, ses
images Docker (profils `tomcat` et `wildfly`) et son installation par les mises à jour n'existent plus.
Seuls le serveur REST et le client Angular restent.

Les versions publiées contiennent encore un `JsfClient.war`, réduit à une page qui annonce la
suppression et renvoie vers Thermobox-Ng-Client : une interface JSF 26.11 qui se met à jour
l'installe, et ses utilisateurs savent où aller. Le serveur REST ne le télécharge jamais.

**Pour migrer :** déployez Thermobox-Ng-Client (branche `dev` pour l'instant) en suivant son README,
avec ce serveur REST comme API (`http://localhost:8080` avec Docker), puis arrêtez Tomcat ou WildFly.
Les comptes, salles, consignes et groupes sont dans la base et restent tels quels. Les préférences
personnelles de l'interface JSF (unité, teinte, animations, raccourcis) étaient des cookies du
navigateur et sont à refaire dans le client.

## Noms anglais (26.12)

Depuis 26.12, le code, la base et l'API sont en anglais (salle → `Room`, bâtiment → `Building`,
étage → `Floor`, boîtier → `Device`, consigne → `Setpoint`, mesure → `Measurement`, gestionnaire →
`MANAGER`…). Les interfaces restent en français.

- **API REST** : l'API anglaise est sous `/api/v2`. L'ancienne API (`/api/salles`, `/api/hierarchie`,
  `/api/climat`…) répond toujours à l'identique pour les clients existants ; voir
  [RestServer/README.md](RestServer/README.md#-ancienne-api-api-dépréciée) pour les correspondances.
- **Base de données** : la migration `V4__english_names.sql` renomme les tables et les colonnes
  (`salle` → `room`, `groupe` → `user_group`, `acces` → `user_access`…) et les types enregistrés
  (`Batiment` → `Building`…). C'est une mise à jour fonctionnelle : une version antérieure ne peut
  plus ouvrir la base, revenir en arrière demande de restaurer la sauvegarde faite avant la mise à jour.
- **Boîtiers MQTT** : topics `thermobox/rooms` et `thermobox/setpoints/<id>` ; les anciens topics
  (`thermobox/salles`, `thermobox/consignes/<id>`) restent servis.
- **Fichiers du dossier de données** : `interface.properties` (`style=glass` ou `panel`) et
  `updates/installation.json` sont écrits en anglais ; les versions françaises sont toujours lues.

## Performances et accès JDBC par défaut (26.12)

Un serveur ThermoBox n'a pas besoin d'une grosse machine. Sur une base de 4 millions de mesures (960
boîtiers), avec les réglages par défaut (`rest` 1 Go, `mysql` 1,5 Go) :

| Test de charge | Résultat |
|---|---|
| 150 utilisateurs, 2 s de lecture par page | 155 requêtes/s, 41 ms (p95), 150 Mo de mémoire Java |
| 400 utilisateurs, 2 s de lecture par page | 20 ms (p95), ~3 cœurs, 500 Mo de mémoire Java |
| Sans pause : débit maximal | 770 requêtes/s, sans erreur |

Le premier test de 26.12, avant ce travail, donnait des pages en 36 s (p95) et 3 Go de mémoire Java.

Ce qui a changé : index des courbes et moyennes horaires (migrations V5 à V7, quelques dizaines de
secondes au premier démarrage sur une grosse base), hiérarchie gardée en mémoire, mini-courbes de
l'aperçu tirées des moyennes horaires, connexions à MySQL gardées ouvertes, limites de mémoire des
conteneurs. Le détail, la méthode et les chiffres de chaque étape sont dans [loadtest](loadtest/README.md).

**L'accès à la base passe en JDBC direct par défaut** (`THERMOBOX_DB_ACCESS=jdbc`) : sur le même test,
il répond à 22 fois plus de requêtes que JPA (EclipseLink). Les deux utilisent les mêmes tables :

- rien à faire pour une installation qui ne fixait pas `THERMOBOX_DB_ACCESS` (elle passe en JDBC) ;
- un `.env` copié d'une version précédente peut contenir `THERMOBOX_DB_ACCESS=jpa` : remplacez-le par
  `jdbc`, ou supprimez la ligne ;
- pour revenir à JPA en cas de problème : `THERMOBOX_DB_ACCESS=jpa`, puis redémarrer. JPA reste
  disponible pendant quelques versions ; son retrait sera annoncé dans les notes de version.

Mesurer son propre serveur : `k6 run -e USERS=100 loadtest/thermobox.js` (voir [loadtest](loadtest/README.md)).

## Architecture du Projet

Le projet est un ensemble de modules Maven ; chacun a son README, qui détaille ses dossiers et ses
fichiers.

| Module | Rôle | Documentation |
| :--- | :--- | :--- |
| `ibr-utils` | Briques génériques : contrat CRUD, service JPA générique, annotations de sécurité REST, exceptions. | [ibr-utils/README.md](ibr-utils/README.md) |
| `Common` | Le modèle : entités (hiérarchie, températures, utilisateurs, accès…), interfaces des services, types des mises à jour. | [Common/README.md](Common/README.md) |
| `Persistence` | La base : services de stockage en JPA ou en JDBC, migrations Flyway, sauvegarde SQL. | [Persistence/README.md](Persistence/README.md) |
| `Business` | Les règles et calculs : hiérarchie, consignes héritées, accès, limites de température, pilotes des boîtiers (MQTT, simulation), sécurité, mises à jour. | [Business/README.md](Business/README.md) |
| `RestServer` | Le programme lancé : API REST (Jersey + Jetty) sécurisée par JWT, logo du serveur. | [RestServer/README.md](RestServer/README.md) |
| `Base` | Pas de code : fabrique `base.jar` (les modules du projet) pour le déploiement. | [Base/README.md](Base/README.md) |

Chaque module ne dépend que de ceux à sa gauche :

```
ibr-utils  ←  Common  ←  Persistence  ←  Business  ←  RestServer
```

Une requête traverse les couches dans l'ordre : `RestServer` (filtres : ancienne API, CORS, jeton JWT
et droits ; puis la ressource) → `Business` (règles, accès, calculs, via `BusinessFactory`) →
`Persistence` (SQL, via `PersistenceFactory`) → MySQL. Les mesures suivent un autre chemin : le pilote
de boîtiers (`Business/io`) les reçoit (MQTT ou simulation) et les enregistre directement par les
services métier.

### Dossiers du dépôt

```
ThermoBox/
├── ibr-utils/, Common/, Persistence/, Business/, RestServer/, Base/   les modules (voir ci-dessus)
├── pom.xml                Maven parent : modules, versions, profil deploy
├── deploy/                (créé par mvn -Pdeploy) RestServer.jar, base.jar, JsfClient.war
├── docker/
│   ├── rest/              Dockerfile du serveur REST et entrypoint.sh (relance après une mise à jour)
│   └── mosquitto/         configuration du broker MQTT (profil compose mqtt)
├── docker-compose.yml     MySQL + REST (+ Mosquitto) ; voir DOCKER.md
├── Dockerfile             image REST simple (build Maven puis JRE), sans relance automatique
├── .env.example           modèle du fichier .env de docker compose
├── loadtest/             test de charge k6 : combien d'utilisateurs simultanés le serveur tient
├── scripts/
│   ├── init-env.sh        crée .env avec des secrets aléatoires
│   └── thermobox-rest.sh  lance le serveur hors Docker et le relance après une mise à jour (systemd…)
├── .github/workflows/
│   ├── nightly.yml        chaque nuit : pré-version AA.MM.0-nightly.N depuis la branche dev
│   ├── prerelease.yml     à la demande : beta ou rc
│   ├── release.yml        version publiée à la main : joint RestServer.jar, base.jar, JsfClient.war
│   ├── docker-image.yml   image Docker de chaque version sur ghcr.io (amd64, arm64)
│   ├── qodana_code_quality.yml  analyse de code (Qodana) sur les pull requests et dev
│   └── summary.yml        résumé automatique des nouvelles issues
├── qodana.yaml            réglages de Qodana
├── DOCKER.md              tout sur Docker
└── README.md              ce fichier
```

### Dossier de données (`THERMOBOX_DATA_DIR`)

Ce que le serveur écrit hors de la base, dans `~/.thermobox` (volume `thermobox_data` sous Docker).
Plusieurs serveurs REST peuvent le partager.

```
interface.properties             style d'affichage choisi par un administrateur (glass / panel)
temperature-limits.properties    limites des mesures et des consignes
branding/logo.png | logo.svg     logo donné par un administrateur
updates/<version>/               RestServer.jar, base.jar d'une version téléchargée ou conservée
updates/current                  version à lancer (lue par les lanceurs)
updates/installation.json        dernière installation et ses étapes
backups/                         sauvegardes de la base faites avant une mise à jour
```

## Stack Technique
- Langage : Kotlin 2.4+
- Build Tool : Maven
- Persistence : JDBC direct (défaut) ou Jakarta Persistence (JPA, EclipseLink), au choix par `THERMOBOX_DB_ACCESS` ; migrations Flyway
- API : Jersey, Jetty
- Sécurité : JWT (JSON Web Tokens)
- Frontend : client Angular séparé ([Thermobox-Ng-Client](https://github.com/IbraxTheKing/Thermobox-Ng-Client))

## Déploiement
Le projet inclut un profil de déploiement spécifique. Pour générer les artefacts destinés à la production (incluant le regroupement des modules dans base.jar) :

```bash
mvn -Pdeploy -DskipTests package
```
Les fichiers produits sont disponibles dans le dossier /deploy.

## Stratégie de branches et gestion des versions

ThermoBox suit un cycle de publication mensuel. Les nouvelles fonctionnalités sont prévues pour la prochaine version mensuelle, tandis que les corrections de bugs et les correctifs de sécurité peuvent être rétroportés vers les versions stables encore prises en charge.

### Branches

| Préfixe | Rôle |
|---|---|
| `dev/` | Branche de développement de la prochaine version. Les nouvelles fonctionnalités et améliorations y sont intégrées avant la publication. |
| `feature/` | Développement d'une nouvelle fonctionnalité destinée à la prochaine version mensuelle. Ces branches doivent être créées à partir de la branche `dev/` appropriée et fusionnées dans celle-ci. |
| `hotfix/` | Correction d'un bug affectant la dernière version stable. Les modifications doivent rester compatibles avec cette version et être reportées vers `dev/` lorsque cela est pertinent. |
| `security/` | Correction d'une vulnérabilité de sécurité. Elle doit impérativement rester compatible avec la version stable ciblée et être intégrée à `dev/` dès que possible afin que les prochaines versions bénéficient également du correctif. |
| `release/` | Branches réservées aux versions avec support à long terme (*Long-Term Support*, ou LTS). Elles ne doivent pas recevoir de nouvelles fonctionnalités dans le cadre du développement courant. Les correctifs de sécurité et les corrections de bugs applicables doivent rester compatibles avec la version LTS correspondante. |

#### Règles de gestion des branches

- Ne pas effectuer de développement directement sur les branches `release/`.
- Appliquer les correctifs aux différentes branches de versions encore prises en charge lorsque celles-ci sont concernées.
- Reporter les corrections vers `dev/` dès que possible afin d'éviter leur réapparition dans les prochaines versions.
- Réserver les nouvelles fonctionnalités à la prochaine version mensuelle. Elles ne doivent pas être intégrées aux branches stables ou LTS, sauf nécessité explicite.

### Numérotation des versions

ThermoBox utilise le format suivant :

**Versions stables**

`AA.MM.PATCH`

- `AA` : année de publication, sur deux chiffres.
- `MM` : mois de publication, de `01` à `12`.
- `PATCH` : numéro du correctif, commençant à `0` pour chaque nouvelle version mensuelle.

Exemples :

- `26.10.0` : première version stable d'octobre 2026.
- `26.10.1` : première version corrective de la série d'octobre 2026.
- `26.11.0` : première version stable de novembre 2026, intégrant les nouvelles fonctionnalités prévues pour ce mois.

**Versions de prépublication**

| Format | Utilisation |
|---|---|
| `AA.MM.PATCH-nightly.N` | Versions de développement fréquentes, destinées aux tests continus. |
| `AA.MM.PATCH-beta.N` | Versions bêta destinées aux tests et aux retours des utilisateurs. |
| `AA.MM.PATCH-rc.N` | Versions candidates à la publication (*Release Candidate*), destinées à la validation finale. |

Le numéro `N` est un numéro séquentiel commençant à `1` pour chaque série de prépublication.

Exemples :

- `26.11.0-nightly.1`
- `26.11.0-beta.1`
- `26.11.0-rc.1`
- `26.11.0`

Les versions de prépublication ne sont pas considérées comme stables. Une version stable n'est publiée qu'après validation de la version candidate.

### Cycle de publication mensuel

1. **Développement :** les nouvelles fonctionnalités sont développées dans des branches `feature/`, puis intégrées dans `dev/`.
2. **Stabilisation :** la prochaine version mensuelle est testée et préparée. Des versions `nightly` et `beta` peuvent être publiées.
3. **Validation finale :** une ou plusieurs versions `rc` sont publiées afin de vérifier que la version est prête.
4. **Publication stable :** après validation, la version stable est publiée avec son tag Git correspondant.
5. **Maintenance :** les corrections de bugs et de sécurité sont publiées sous forme de versions correctives et reportées vers les branches stables et LTS concernées.

Chaque nouveau mois ouvre une nouvelle série de versions destinée aux nouvelles fonctionnalités. Les anciennes séries peuvent continuer à recevoir des correctifs selon leur statut de prise en charge.

### Tags Git

Chaque version publiée doit posséder un tag Git correspondant exactement à son numéro de version, sans préfixe `v`.

Exemples : `26.10.0`, `26.10.1`, `26.11.0-beta.1`.

Les tags constituent des références immuables vers les commits publiés. Un tag existant ne doit jamais être déplacé ni réutilisé pour désigner un autre commit.

## Mises à jour depuis l'interface

ThermoBox cherche chaque jour une nouvelle version sur GitHub (et à la demande, depuis
**Administration → Mises à jour**). Les administrateurs sont prévenus par l'API REST, que le client affiche.

**Types de mise à jour**, d'après le numéro de version :

| Type | Exemple depuis 26.10.1 | Ce que cela implique |
|---|---|---|
| Corrective | 26.10.2 | Corrections de bugs et de sécurité, sans nouvelle fonctionnalité ni changement de la base : installation recommandée. Sauvegarde facultative. |
| Fonctionnelle | 26.11.0 | Nouvelle série : peut modifier la base. Avertissement à accepter, sauvegarde de la base faite d'office, retour arrière non garanti. |

Les prépublications (nightly, beta, rc) ne sont proposées qu'avec `THERMOBOX_UPDATE_CHANNEL=prerelease`.
Les nightly sont construites chaque nuit depuis la branche `dev/` (`dev/AA.MM` de préférence) et portent
le numéro de la prochaine version fonctionnelle : après 26.10.x, `26.11.0-nightly.N`. Les correctifs
n'ont pas de nightly, ils sont publiés directement en version stable.
Les beta et les RC se publient à la demande : onglet **Actions** → **Beta / RC** → *Run workflow*,
choisir `beta` ou `rc` (et la branche, `dev` par défaut). Le numéro suit le même principe :
`26.11.0-beta.1`, `26.11.0-beta.2`, puis `26.11.0-rc.1`…

**Installation** (ADMIN) : sauvegarde de la base (SQL gzip, dans `backups/`), téléchargement des
fichiers, vérification de leur empreinte SHA-256 (publiée par GitHub), contrôle de la version de
Java demandée, conservation des fichiers actuels, puis bascule :

- le serveur REST s'arrête avec le code 75 et son lanceur le relance sur la nouvelle version :
  `docker/rest/entrypoint.sh` dans Docker, `scripts/thermobox-rest.sh` ailleurs (avec
  `java -jar` seul, l'installation est refusée : personne ne relancerait le serveur).

Plusieurs serveurs REST peuvent partager le dossier de données (`THERMOBOX_DATA_DIR`, volume
`thermobox_data` dans Docker, `~/.thermobox` sinon) : une version installée depuis l'un est
appliquée par les autres dans la minute. Chaque étape est affichée pendant l'installation, et les erreurs sont remontées à
l'administrateur.

**Pour publier une version** : le tag suit `AA.MM.PATCH` ; le workflow `release.yml` joint
`RestServer.jar`, `base.jar` et `JsfClient.war` (la page « JSF supprimé », voir plus haut) à la version publiée, avec le numéro inscrit dedans
(`-Dthermobox.version`). Dans les notes, une section `## Migration` (ou des lignes `Migration : …`)
liste les opérations à faire, et le mot « incompatible » signale une version incompatible :
l'interface les met en avant avant l'installation. Une ligne dit si l'on peut revenir en arrière
après l'avoir installée :

```
Rétrocompatibilité : non
```

(ou `oui` ; en gras ou dans une liste ; aussi `Retour arrière : possible / impossible`, `Rollback: yes / no`,
`Backward compatible: yes / no`). Le serveur et le client appliquent la même règle (`RollbackRule`) :
une version antérieure n'est plus proposée sans avertissement dès qu'une version entre elle et la
version installée (incluse) dit `non` ; il faut alors restaurer la sauvegarde faite avant celle-ci.
Sans cette ligne, rien n'est affirmé (« retour arrière non garanti »). Mettez `non` dès qu'une version
change la base d'une façon que les précédentes ne suivraient pas (copies, tables tenues à jour, noms
renommés…).

Chaque version publie aussi son **image Docker** (`ghcr.io/ibraxtheking/thermobox:<version>`, voir
[DOCKER.md](DOCKER.md#image-publiée)) : workflow `docker-image.yml`, lancé par une version publiée à
la main, par les nightly et par les beta / RC.

**Schéma de la base** : les migrations Flyway de la nouvelle version s'appliquent à son premier
démarrage (voir [Persistence](Persistence/README.md#migration-et-resourcesdbmigration--le-schéma)).
Une version antérieure ne sait pas toujours relire une base migrée : en cas de retour arrière,
restaurez la sauvegarde faite avant la mise à jour :
`gunzip -c backups/<fichier>.sql.gz | mysql -u <utilisateur> -p <base>`.

API REST :

| Méthode | Route | Rôle |
|---|---|---|
| GET | `/api/v2/updates` | Authentifié : version installée, versions disponibles (`latestPatch`, `latestFeature`), versions antérieures en canal prerelease (`olderVersions`, avec `blockedBy` et `incompatible` quand une version plus récente interdit d'y revenir), notes de la version installée (`installedNotes`) et ce qu'elles disent du retour arrière (`installedRollback` : `true`, `false`, `null`) |
| POST | `/api/v2/updates/check` | ADMIN : vérifier maintenant |
| GET | `/api/v2/updates/{version}` | ADMIN : contrôles et risques avant installation |
| POST | `/api/v2/updates/{version}/install` | ADMIN : `{"backupDatabase": true, "acceptRisks": true}`, réponse 202 |
| GET | `/api/v2/updates/installation` | ADMIN : déroulement de la dernière installation |

Variables : `THERMOBOX_UPDATE_CHANNEL` (`stable` / `prerelease`), `THERMOBOX_UPDATE_CHECK`
(`false` coupe la vérification quotidienne), `THERMOBOX_DATA_DIR`, `THERMOBOX_UPDATE_REPO`,
`THERMOBOX_GITHUB_TOKEN` (facultatif).

## Accès aux bâtiments, étages et salles

Un administrateur choisit ce que chaque utilisateur peut consulter : **Administration → Utilisateurs**,
bouton **Accès** (clé) d'un utilisateur, ou `PUT /api/v2/users/{id}/access`.

Règles :

- un **ADMIN** voit et gère tout, quels que soient les accès enregistrés ;
- un **MANAGER** (gestionnaire) ou un **VIEWER** a soit accès à tout, soit seulement à des éléments choisis ;
- un accès à un bâtiment ou à un étage couvre tout ce qu'il contient (étages, salles, boîtiers),
  y compris ce qui y sera ajouté plus tard. Les accès s'additionnent, il n'y a pas de refus
  explicite : aucune règle ne peut en contredire une autre ;
- les bâtiments et étages qui contiennent un élément accessible sont visibles par leur nom seulement
  (fil d'Ariane, arbre, regroupement de l'aperçu), sans leurs données : leur moyenne parlerait des
  salles non autorisées. La moyenne d'un bâtiment sur l'aperçu est calculée sur les seules salles
  accessibles ;
- un MANAGER n'envoie de consigne qu'aux éléments auxquels il a accès.

Les accès sont vérifiés côté serveur à chaque requête, dans l'API REST :
listes, recherches, pages de détail, photos, températures, consignes. Un élément non autorisé
répond comme un élément inexistant, même avec son identifiant tapé dans l'URL. Un changement
d'accès s'applique dès la requête suivante.

**Comptes existants et nouveaux comptes.** Les comptes créés avant cette fonctionnalité gardent
l'accès à tout (rien ne change pour eux tant qu'un administrateur ne les limite pas). Un compte créé
ensuite, y compris par l'inscription publique, n'a accès à rien tant qu'un administrateur ne lui en
donne pas ; un ADMIN rétrogradé aussi. Les accès sont stockés dans deux tables (`user_access`,
`restricted_access`).

API REST :

| Méthode | Route | Rôle |
|---|---|---|
| GET | `/api/v2/users/{id}/access` | ADMIN, ou l'utilisateur lui-même : `{"restricted": true, "elements": [...]}` |
| PUT | `/api/v2/users/{id}/access` | ADMIN : `{"restricted": true, "elements": [ids de bâtiments, étages, salles]}` |
| GET / POST | `/api/v2/groups` | ADMIN : groupes ; `{"name", "description", "members": [ids], "elements": [ids]}` |
| GET / PUT / DELETE | `/api/v2/groups/{id}` | ADMIN : un groupe |

**Groupes d'utilisateurs.** Un ADMIN crée des groupes (page Gérer → « Utilisateurs et groupes ») :
chaque membre d'un groupe voit en plus les bâtiments, étages et salles du groupe, avec ce qu'ils
contiennent. Les accès s'additionnent (les siens et ceux de tous ses groupes) ; un utilisateur qui
voit tout n'est pas limité par ses groupes. Tables `user_group`, `user_group_member`, `user_group_access`
(migrations `V3__groupes.sql` puis `V4__english_names.sql`).

## Notes importantes
- Base de données : le schéma est créé puis tenu à jour au démarrage par les migrations Flyway ; `THERMOBOX_DB_MIGRATE=false` le laisse à l'administrateur. Ni JDBC ni JPA ne créent ou ne modifient de table.
- Sécurité : Ne pas exposer les identifiants ou la clé JWT secrète dans un environnement public.
