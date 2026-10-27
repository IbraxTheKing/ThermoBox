# Module Business

`Business` est le cœur de ThermoBox : il applique les règles (qui contient quoi, quelles températures
sont acceptées, qui voit quoi), calcule les températures à chaque niveau de la hiérarchie, parle aux
boîtiers (MQTT ou simulation), gère la sécurité des comptes et installe les mises à jour.

```
ibr-utils  ←  Common  ←  Persistence  ←  Business  ←  RestServer
```

- **Dépend de** : `Common` (entités, interfaces), `Persistence` (stockage, via `PersistenceFactory`),
  `ibr-utils`, Paho MQTT, java-jwt, jBCrypt, Jackson.
- **Utilisé par** : `RestServer`, qui n'appelle jamais Persistence directement : il passe par
  `BusinessFactory`.

## Arborescence

```
Business/src/main/kotlin/ax/ibr/thermobox/business/
├── implementations/   les services métier et la BusinessFactory qui les fournit
├── rules/             règles pures : liens permis dans la hiérarchie, limites de température
├── settings/          réglages des administrateurs gardés dans le dossier de données
├── protocols/         ProtocolDriver : la classe de base des pilotes de boîtiers
├── io/                les pilotes : MQTT (mqtt/) et simulation (simulation/, mqtt/SImulatedMqttProtocol.kt)
├── config/            ConfigDriver : quel pilote, quel broker (variables d'environnement)
├── security/          mots de passe, connexion, jetons JWT, limitation des essais
├── images/            ImageProcessor : contrôle et réduction des photos
├── charts/            CurveSampling : réduction d'une courbe à N points (LTTB)
├── climate/           ClimateSnapshot : toute la hiérarchie et ses températures, lue en une fois
├── hierarchy/         HierarchyCache : bâtiments, étages, salles, boîtiers et leurs liens en mémoire
├── update/            le système de mises à jour depuis GitHub
└── exceptions/        TooHotException, ImpossibleValueException
Business/src/main/resources/thermobox-version.properties   version inscrite par Maven
Business/src/test/                                          tests, et deux outils (DatabaseSeeder, HashExistingPasswords)
```

## `implementations/` — les services

### `BusinessFactory`

Point d'entrée unique : `BusinessFactory.getRoomService()`, `getClimateService()`, `getDriver()`…
Chaque service est créé une seule fois (`by lazy`) et partagé : ils sont sans état et sûrs entre
plusieurs requêtes. Le pilote de boîtiers aussi est unique : **ne jamais instancier un pilote
soi-même** (voir [io/README.md](src/main/kotlin/ax/ibr/thermobox/business/io/README.md)).

Chaque service prend ses dépendances en paramètres de constructeur, avec la valeur de production par
défaut : les tests lui passent des services en mémoire.

### Services de données (CRUD avec règles)

Ils implémentent les interfaces de `Common` en déléguant à Persistence, et ajoutent les règles :

| Service | Ce qu'il ajoute au stockage |
|---|---|
| `UserServiceImpl` | Hache le mot de passe (BCrypt) s'il n'est pas déjà un hachage ; nom d'utilisateur en lettres seulement ; un nouveau compte ne voit rien tant qu'un ADMIN ne lui donne pas d'accès ; un ADMIN rétrogradé perd la vue sur tout ; supprimer un compte supprime ses favoris, ses accès et ses appartenances à des groupes. |
| `RoomServiceImpl` | Supprimer un élément supprime son historique de températures, sa photo, les favoris et les accès qui le visent, et ses liens : ses enfants restent, non rattachés. |
| `TemperatureServiceImpl` | Refuse une mesure ou une consigne hors des limites (`SetpointRules`). |
| `RoomTempAttrServiceImpl` | Délègue tel quel : les valeurs sont contrôlées avant (par `ClimateService` pour une consigne, par le pilote pour une mesure). |
| `RoomRoomAttrServiceImpl` | Vérifie chaque lien avec `HierarchyRules`. |
| `RoomImageServiceImpl` | Passe chaque photo par `ImageProcessor` ; une seule photo par élément (l'ancienne est remplacée). |
| `FavoriteServiceImpl` | Une salle n'est ajoutée qu'une fois aux favoris d'un utilisateur. |

### `HierarchyServiceImpl` — l'arbre

Racines (bâtiments et éléments non rattachés), enfants, parent, chemin depuis le bâtiment, tous les
boîtiers et toutes les salles sous un élément (parcours limité à 4 niveaux : bâtiment → étage → salle
→ boîtier). Les listes sont triées par type puis par nom. `setParent` vérifie les règles et remplace
l'ancien parent.

### `ClimateServiceImpl` — les températures à tous les niveaux

- **Température d'un élément** : la dernière mesure de chaque boîtier qu'il contient, à n'importe
  quelle profondeur ; leur moyenne pour l'élément.
- **Consigne effective** : la consigne propre de l'élément, sinon celle de l'ancêtre le plus proche
  qui en a une (une salle suit son étage, qui suit son bâtiment). `EffectiveSetpoint` dit d'où elle vient.
- **Historique** : une courbe par boîtier (toutes ses mesures jusqu'à deux jours, ses moyennes horaires
  au-delà, `getRawHistories` pour tout avoir), et une moyenne dans le temps (`movingAverage`) : à chaque
  instant où un boîtier mesure, chaque boîtier compte avec sa dernière valeur connue, puisqu'ils ne
  mesurent pas en même temps.
- **Mini-courbes** (`getAverageHistories`) : la courbe moyenne de plusieurs salles à la fois, pour
  l'aperçu. Les moyennes horaires servent dès 12 heures de période (`AVERAGES_HOURLY_FROM`, au lieu de
  deux jours pour les autres courbes) : une tendance d'un coup d'œil n'a pas besoin de chaque mesure, et
  50 salles sur 24 h se lisent en 3 600 lignes au lieu de 21 000 (5 fois plus de requêtes par seconde
  sous charge). `raw = true` (`points=0` dans l'API) donne toujours chaque mesure.
- **Envoi d'une consigne** (`sendSetpoint(room, value, force)`) :
  1. contrôle de la valeur (`SetpointRules`), puis enregistrement sur l'élément ;
  2. parcours de ses enfants : un étage ou une salle qui a **sa propre** consigne la garde, avec ses
     boîtiers, sauf si `force` ; dans ce cas elle est remplacée, avec la même valeur et la même date
     que celle du parent, ce qui la fait de nouveau « suivre » le parent ;
  3. envoi à chaque boîtier concerné par le pilote. Un boîtier en échec n'empêche pas les autres :
     il est listé dans `failures`.

### `AccessServiceImpl` — qui voit quoi

- un ADMIN voit tout ; un compte sans `RestrictedAccess` aussi (comptes antérieurs à la gestion des
  accès) ;
- sinon : ses accès directs plus ceux de ses groupes, chacun couvrant tout son contenu ;
- `scope(user)` calcule l'`AccessScope` d'une requête (éléments accessibles et ancêtres visibles) ;
  rien n'est mis en cache : un changement s'applique à la requête suivante ;
- `filterTemperatures` retire les températures d'éléments non accessibles.

### `UserGroupServiceImpl`

Création, modification et suppression des groupes, de leurs membres et de leurs éléments (un groupe ne
donne pas accès à un boîtier seul, et son nom est unique).

## `rules/` — les règles

| Objet | Règle |
|---|---|
| `HierarchyRules.checkLink(parent, child)` | Les deux éléments sont enregistrés, distincts, et le type du parent est permis pour l'enfant (`RoomKind.parentKinds`). Sinon `IllegalArgumentException` avec un message pour l'utilisateur. |
| `SetpointRules.check(value)` | Consigne entre `setpoint.min` et `setpoint.max` : en dessous `ImpossibleValueException`, au-dessus `TooHotException`. |
| `SetpointRules.checkMeasurement(value)` | Idem pour une mesure ajoutée à la main (`measurement.min/max`). |
| `SetpointRules.acceptsMeasurement(value)` | Une mesure reçue d'un boîtier est-elle plausible ? Sinon le pilote l'ignore (erreur de capteur). |

Les limites viennent de `settings/TemperatureLimits` (`SetpointRules.limits`, remplaçable dans les
tests).

## `settings/` — réglages des administrateurs

`TemperatureLimits` : deux plages (°C), `measurement` et `setpoint`, -10 à 50 par défaut. Chaque
plage doit avoir `min < max` et rester dans -100 à 200. `TemperatureLimitsStore` les garde dans
`temperature-limits.properties` du dossier de données, les relit quand le fichier change (plusieurs
serveurs peuvent partager le dossier) et les écrit de façon atomique. API :
`GET/PUT/DELETE /api/v2/server/limits`.

Les autres réglages du dossier de données (style d'affichage, logo) sont gérés par RestServer.

## `protocols/`, `io/`, `config/` — les boîtiers

- `ProtocolDriver` : classe de base. `listen()` démarre l'écoute ; `listenToRooms()` reçoit les
  boîtiers qui s'annoncent (`registerDevice` : enregistré s'il n'existe pas déjà) ;
  `listenToTemperatures()` reçoit les mesures ; `sendSetpoint(device, setpoint)` vérifie la consigne
  puis l'envoie.
- `io/mqtt/MqttDriver` : vrais boîtiers via un broker MQTT (topics `thermobox/rooms`,
  `thermobox/temperatures/<id>`, `thermobox/setpoints/<id>`, plus les anciens topics français). Une
  mesure hors limites ou pour un id qui n'est pas un boîtier est ignorée.
- `io/mqtt/SImulatedMqttProtocol.kt` (`SimulatedProtocolDriver`) : sans broker ni matériel. Chaque
  minute, chaque boîtier de la base mesure une température qui tend vers la consigne de sa salle
  (`io/simulation/SimulatedClimate`) ; toutes les deux minutes, un « nouveau boîtier » s'annonce, non
  rattaché.
- `config/ConfigDriver` : `THERMOBOX_DRIVER` (`simulated` par défaut, ou `mqtt`), `THERMOBOX_MQTT_URL`,
  `THERMOBOX_MQTT_USER`, `THERMOBOX_MQTT_PASSWORD`. Une valeur inconnue arrête le démarrage.

Écrire un nouveau pilote : [io/README.md](src/main/kotlin/ax/ibr/thermobox/business/io/README.md).

## `security/`

| Objet | Rôle |
|---|---|
| `PasswordHasher` | Hachage BCrypt (sel aléatoire par mot de passe) ; refuse plus de 72 octets (limite de BCrypt) ; `isHash` reconnaît un hachage. |
| `Authenticator` | Vérifie identifiant et mot de passe. Un nom inconnu coûte le même temps qu'un vrai (hachage factice) : le temps de réponse ne dit pas si le compte existe. Un ancien compte au mot de passe en clair est accepté une fois, puis son mot de passe est haché. |
| `JwtService` | Jetons HS256 : `THERMOBOX_JWT_SECRET` (32 octets minimum ; sans lui, une clé aléatoire est tirée et les jetons ne survivent pas à un redémarrage), `THERMOBOX_JWT_TTL_MINUTES` (60 par défaut). Révocation des jetons à la déconnexion. |
| `RateLimiter` | 5 échecs de connexion bloquent la clé (l'identifiant) pendant 60 s. |

## `images/` — `ImageProcessor`

Une photo envoyée (10 Mo au plus) est décodée (ce qui n'est pas vraiment une image est refusé : JPEG,
PNG, GIF ou BMP), redressée selon son orientation EXIF, réduite à 1600 px de côté au plus, puis
ré-encodée en JPEG (qualité 0,85). Le ré-encodage supprime les métadonnées (position GPS d'une photo
de téléphone). Les images de plus de 40 millions de pixels sont refusées avant décodage.

## `hierarchy/` — `HierarchyCache`

Chaque requête demande des parents, des enfants, des chemins ou les boîtiers contenus (droits d'accès,
arbre, courbes), alors que la hiérarchie ne change presque jamais. `HierarchyCache` la garde en mémoire
(éléments et liens, lus en deux requêtes) ; `HierarchyServiceImpl` y répond quand il en reçoit un (celui
de `BusinessFactory`), sinon il interroge les services comme avant (les tests). Elle est relue :
- aussitôt après un changement passé par les services (`RoomServiceImpl`, `RoomRoomAttrServiceImpl`
  appellent `HierarchyCache.invalidate()`) ;
- au plus tard 30 s après, pour les changements faits ailleurs (autre serveur sur la même base, script).

Les éléments donnés sont des copies : les modifier ne touche pas le cache. Les accès d'un utilisateur
limité, recalculés à chaque requête, passent ainsi de 16 ms à 1 ms sur le jeu « huge ».

## `climate/` — `ClimateSnapshot`

Pour les pages qui montrent tous les éléments à la fois (l'arbre de l'aperçu) : la hiérarchie (celle de
`HierarchyCache`), la dernière mesure de chaque boîtier et la consigne propre de chaque élément sont lues
en deux requêtes, puis tout est calculé en mémoire avec les mêmes règles que `HierarchyService` et
`ClimateService` (boîtiers contenus, moyenne, consigne héritée). Un instantané vaut pour une requête :
on n'en garde jamais. `ClimateServiceImpl.effectiveSetpoint` porte la règle de la consigne héritée,
partagée par les deux.

## `charts/` — `CurveSampling`

Réduit une courbe de températures à au plus N points qui dessinent presque la même ligne
(*Largest-Triangle-Three-Buckets*) : le premier et le dernier point sont gardés ; les autres sont
répartis en N − 2 tranches égales, et chaque tranche garde le point qui forme le plus grand triangle
avec le point gardé avant lui et la moyenne de la tranche suivante. Les pics restent visibles. Seuls
des points d'origine sont renvoyés. Utilisé par `GET /api/v2/climate/…?points=N`.

## `update/` — mises à jour depuis GitHub

| Fichier | Rôle |
|---|---|
| `UpdateConfig` | `THERMOBOX_UPDATE_REPO`, `THERMOBOX_UPDATE_CHANNEL` (`stable` / `prerelease`), `THERMOBOX_UPDATE_CHECK`, `THERMOBOX_DATA_DIR`, `THERMOBOX_GITHUB_TOKEN`. |
| `GitHubReleases` | Lit les versions publiées (`GET /repos/{repo}/releases`). `ReleaseNotes` y repère la section « Migration » et le mot « incompatible ». |
| `Downloader` | Télécharge les fichiers et vérifie leur SHA-256 (publié par GitHub). Chaque redirection doit rester en HTTPS chez GitHub ; le jeton n'est jamais transmis ailleurs. |
| `Artifacts` | Fichiers d'une version : `RestServer.jar` et `base.jar`, séparés ou dans un zip ; contrôle de leur contenu (classe principale, version de Java demandée, présence du système de mise à jour). |
| `UpdateDirectory` | Le dossier de données : `updates/<version>/`, `updates/current` (version à lancer, lue par les lanceurs), `updates/installation.json`, `backups/`, et le verrou d'installation. |
| `InstallationJson` | Lecture et écriture de `installation.json` (lit aussi l'ancien format français). |
| `LocalComponent` | Ce que le programme sait faire pour changer de version ; implémenté par `RestComponent` (RestServer). |
| `BuildInfo` | Version de ce build (`thermobox-version.properties`, rempli par Maven avec `-Dthermobox.version`). |
| `UpdateServiceImpl` | L'ensemble, décrit ci-dessous. |

**Déroulement.** Vérification quotidienne (et à la demande) ; `prepare(version)` liste les contrôles
(type de mise à jour, notes, fichiers et empreintes, Java, place disque, possibilité de redémarrer,
installation déjà en cours…) ; un contrôle `BLOCKING` empêche l'installation. `install` exécute les
étapes : sauvegarde de la base (`Persistence/backup`), téléchargement, contrôle des fichiers,
conservation de la version actuelle, mise en place (`updates/current`), puis le serveur s'arrête avec
le code 75 et son lanceur le relance sur la nouvelle version. Au redémarrage, l'installation passe à
`DONE`. Un autre serveur qui partage le dossier de données voit la nouvelle version dans la minute
(`follow`) et bascule aussi. Détails pour les administrateurs : README principal, « Mises à jour
depuis l'interface ».

## Tests

`Business/src/test` : les tests utilisent des services en mémoire (aucune base nécessaire), sauf
`BusinessFactoryTest` et `MqttSendReceiverTest` (Mockito / mockk, base ou broker).

| Test | Ce qu'il vérifie |
|---|---|
| `HierarchyClimateTest`, `InheritedSetpointTest` | Moyennes, consignes héritées, envoi forcé ou non, limites de température. |
| `AccessServiceTest`, `UserGroupServiceTest` | Règles d'accès, groupes. |
| `PasswordHashingTest`, `security/RateLimiterTest` | Hachage, migration des mots de passe en clair, limitation des essais. |
| `RoomImageTest`, `FavoriteServiceTest` | Photos, favoris. |
| `update/UpdatesTest` | Toute une mise à jour contre un faux GitHub (serveur HTTP local). |
| `update/ReleaseNotesAndArtifactsTest`, `update/VersionTest` | Notes, choix des fichiers, extraction de zip, numéros de version. |
| `config/ConfigDriverTest` | Lecture de `THERMOBOX_DRIVER`. |
| `charts/CurveSamplingTest` | Réduction d'un mois de mesures, conservation d'un pic, courbes courtes. |
| `java/BusinessServiceDelegationTest.java` | Les services délèguent bien au stockage. |

Deux programmes y sont rangés parce qu'ils ne doivent pas être livrés : `DBPopulate.kt`
(`DatabaseSeeder`, remplit la base, voir ci-dessous) et `HashExistingPasswords.kt` (hache d'un coup
les anciens mots de passe en clair). Lancez leur `main` avec les mêmes variables `THERMOBOX_DB_*` que
le serveur.

### Remplir la base : `DatabaseSeeder`

Deux jeux de données, au choix (premier argument, ou `THERMOBOX_SEED`) :

| Jeu | Contenu | Pour |
|---|---|---|
| `demo` (défaut) | 3 bâtiments (dont une annexe sans étage), leurs étages et salles, 1 à 3 boîtiers par salle, 2 boîtiers non rattachés, un compte par rôle (`admin`, `manager`, `viewer`), 3 jours de mesures toutes les 30 min avec un changement de consigne au milieu. | une démonstration lisible |
| `huge` | 10 bâtiments × 4 étages × 8 salles × 3 boîtiers = 960 boîtiers, 30 jours de mesures toutes les 10 min (≈ 4,1 millions), des consignes à tous les niveaux (bâtiment, certains étages et salles), 50 boîtiers non rattachés, 500 comptes limités à quelques bâtiments ou étages, 20 groupes. | voir comment tout se comporte sur une grosse base (arbre, accès, courbes, sauvegarde, mises à jour) |

Chaque taille de `huge` se change en argument : `buildings`, `floors`, `rooms`, `devices`, `days`,
`step` (minutes entre deux mesures), `users`, `groups`, `unattached`.

```
DatabaseSeeder demo
DatabaseSeeder huge
DatabaseSeeder huge buildings=20 days=90 step=5     # ≈ 50 millions de mesures
```

Les mesures de `huge` sont écrites par lots JDBC (`rewriteBatchedStatements` est ajouté à l'URL), le
reste par les services métier, avec les mêmes règles que l'application : les jeux par défaut prennent
environ 3 minutes sur un MySQL local. Les comptes générés (`usera`, `userb`… ; un sur dix est MANAGER)
ont tous le mot de passe `password123`. On peut relancer un jeu : un bâtiment déjà présent (même nom)
est sauté, comme un compte existant ; les deux jeux peuvent cohabiter dans la même base.
