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
- MySQL (ou autre base de données compatible JDBC)

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
- Préfixe API : /api

## Configuration (variables d'environnement)

Toute la configuration de production passe par des variables d'environnement : aucun secret ni
réglage propre à une installation n'est dans le code ou dans les fichiers du projet. Avec Docker,
elles viennent de `.env` (voir `.env.example` et [DOCKER.md](DOCKER.md)).

| Variable | Défaut | Rôle |
|---|---|---|
| `THERMOBOX_DB_URL` | `jdbc:mysql://localhost:3306/thermobox` | Base MySQL |
| `THERMOBOX_DB_USER` / `THERMOBOX_DB_PASSWORD` | *obligatoires* | Identifiants de la base |
| `THERMOBOX_DB_MIGRATE` | `true` | `false` : pas de migration de schéma au démarrage |
| `THERMOBOX_DB_ACCESS` | `jpa` | Accès à la base : `jpa` (EclipseLink) ou `jdbc` (SQL direct, sans JPA), voir [Persistence](Persistence/README.md) |
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

## Architecture du Projet

Le projet est structuré en modules Maven pour assurer une séparation des responsabilités :

| Module | Rôle | Description |
| :--- | :--- | :--- |
| Common | Domain | Contient les entités JPA, les modèles de données et les interfaces partagées. |
| Persistence | Data Access | Gère l'accès aux données via JPA et fournit les services de persistance. |
| Business | Core Logic | Contient la logique métier, les règles de calcul des consignes et les pilotes de communication (MQTT/Simulé). |
| RestServer | Entry Point | Exposition des ressources via une API REST sécurisée par JWT (Jersey + Jetty). |
| ibr-utils | Shared Utils | Utilitaires transversaux : Logging JSON, Cryptographie, Gestion d'exceptions personnalisées. |
| Base | Deployment | Module de packaging regroupant les modules internes pour faciliter le déploiement (Fat Jar). |

## Stack Technique
- Langage : Kotlin 2.4+
- Build Tool : Maven
- Persistence : Jakarta Persistence (JPA, EclipseLink) ou JDBC direct (`THERMOBOX_DB_ACCESS`), migrations Flyway
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
l'interface les met en avant avant l'installation.

**Schéma de la base** : EclipseLink crée les tables manquantes mais ne modifie pas les tables
existantes. Une colonne ajoutée ou renommée demande une migration décrite dans les notes ; en cas
de retour arrière, restaurez la sauvegarde :
`gunzip -c backups/<fichier>.sql.gz | mysql -u <utilisateur> -p <base>`.

API REST :

| Méthode | Route | Rôle |
|---|---|---|
| GET | `/api/v2/updates` | Authentifié : version installée, versions disponibles (`latestPatch`, `latestFeature`) |
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
- Base de données : Par défaut, le schéma est créé automatiquement au démarrage (action create). Cette configuration doit être vérifiée pour les environnements de production.
- Sécurité : Ne pas exposer les identifiants ou la clé JWT secrète dans un environnement public.
