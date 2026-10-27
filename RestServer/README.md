# RestServer Module

Le module `RestServer` est le programme que l'on lance : un serveur HTTP (**Jetty**) qui expose
ThermoBox par une API REST (**Jersey**, JSON avec **Jackson**), sécurisée par jetons JWT. C'est par
lui que passent le client Angular ([Thermobox-Ng-Client](https://github.com/IbraxTheKing/Thermobox-Ng-Client))
et toute autre application. Au démarrage, il lance aussi l'écoute des boîtiers et le système de mises
à jour.

```
ibr-utils  ←  Common  ←  Persistence  ←  Business  ←  RestServer
```

- **Dépend de** : `Business` (toute la logique, via `BusinessFactory`), `Common`, `ibr-utils`. Il
  n'appelle jamais `Persistence` directement (sauf `PersistenceFactory.close()` à l'arrêt).
- **Livré comme** : `RestServer.jar` (profil `deploy`), qui contient ce module et toutes les
  bibliothèques tierces (Jersey, Jetty, Jackson, EclipseLink, pilote MySQL…), et trouve les modules du
  projet dans `base.jar`, à côté de lui (`Class-Path` du manifeste, voir le module `Base`).

## 🗂 Arborescence

```
RestServer/src/main/
├── kotlin/ax/ibr/thermobox/restserver/
│   ├── Main.kt          point d'entrée : configuration Jersey, démarrage de Jetty, pilote, mises à jour
│   ├── resources/       les points d'entrée de l'API (une classe par chemin /api/v2/…)
│   ├── dto/             la forme JSON des réponses et des requêtes
│   ├── filters/         filtres appliqués à chaque requête : CORS, sécurité, ancienne API
│   ├── security/        JwtSecurityContext : l'utilisateur connecté, pour les ressources
│   ├── hierarchy/       UserView : l'arbre tel qu'un utilisateur a le droit de le voir
│   ├── branding/        logo du serveur (personnalisé, du site, ou celui de ThermoBox)
│   ├── update/          RestComponent : comment ce serveur change de version
│   └── exceptions/      GlobalExceptionHandler : réponse JSON pour toute erreur non attrapée
└── resources/branding/  logo ThermoBox (SVG, et PNG 192 / 512 / 1024 px)
RestServer/src/test/     ServerLogoTest
```

### `Main.kt`

1. Charge la configuration JWT tout de suite : un `THERMOBOX_JWT_SECRET` invalide arrête le démarrage
   au lieu d'échouer à la première requête.
2. Enregistre dans Jersey les paquets `resources`, `filters` et `exceptions` (toute classe annotée
   `@Path` ou `@Provider` qui s'y trouve est prise en compte automatiquement), et active la
   compression gzip des réponses (`EncodingFilter`) pour les clients qui l'acceptent.
3. Démarre Jetty sur `THERMOBOX_HTTP_HOST:THERMOBOX_HTTP_PORT` (`localhost:8080` par défaut ; dans
   Docker, `0.0.0.0`, sinon le serveur n'est joignable que depuis le conteneur).
4. Lance l'écoute des boîtiers (`BusinessFactory.getDriver().listen()`) et le système de mises à jour
   (`start(RestComponent())`).
5. À l'arrêt : arrêt des mises à jour et fermeture des connexions à la base.

### `resources/` — l'API

Une classe par domaine. Chacune récupère ses services avec `BusinessFactory`, l'utilisateur connecté
avec le `SecurityContext`, et convertit entités ↔ JSON avec `dto/`. Les droits se déclarent par
annotation (`@PublicEndpoint`, `@RequiresRole`, `@RequiresAuth`, voir ibr-utils) ; les règles d'accès
aux éléments (`AccessScope`) sont appliquées dans la ressource : un élément interdit répond comme un
élément inexistant (404).

| Classe | Chemin |
|---|---|
| `AuthResource` | `/api/v2/auth` |
| `RoomResource` | `/api/v2/rooms` (et les photos) |
| `HierarchyResource` | `/api/v2/hierarchy` |
| `ClimateResource` | `/api/v2/climate` |
| `RoomTempAttrResource` | `/api/v2/roomtemps` |
| `TemperatureResource` | `/api/v2/temperatures` |
| `FavoriteResource` | `/api/v2/favorites` |
| `UserResource` | `/api/v2/users` |
| `UserGroupResource` | `/api/v2/groups` |
| `SettingsResource` | `/api/v2/settings` |
| `ServerResource` | `/api/v2/server` |
| `UpdateResource` | `/api/v2/updates` |

### `dto/` — le JSON

Les entités ne sont jamais renvoyées telles quelles : `UserDto` n'a pas de mot de passe, `NodeDto` porte
le type d'élément, la température et la consigne effective… Les dates sont des chaînes ISO-8601 à
l'heure du serveur (`2026-10-21T14:05:00`), les énumérations par leur nom (`BUILDING`, `PATCH`).
Fichiers : `AuthDtos`, `AccessDtos`, `HierarchyDtos`, `UpdateDtos`, `ErrorResponse`.

### `filters/` — à chaque requête, dans cet ordre

1. **`LegacyApiRequestFilter`** (avant la recherche de la ressource) : un chemin `/api/…` qui n'est pas
   `/api/v2/…` est l'ancienne API française ; il est réécrit vers son équivalent v2.
   `LegacyApiInterceptor` traduit ensuite le JSON dans les deux sens (voir
   [Ancienne API](#-ancienne-api-api-dépréciée)).
2. **`CorsFilter`** : répond directement aux requêtes de pré-vérification (`OPTIONS`) et ajoute les
   en-têtes CORS à toutes les réponses (toutes origines : la sécurité repose sur le jeton, pas sur les
   cookies).
3. **`SecurityFilter`** : sauf `@PublicEndpoint`, exige `Authorization: Bearer <jeton>` ; le jeton doit
   être bien signé, non expiré, non révoqué (déconnexion), et son utilisateur doit encore exister.
   Puis il applique `@RequiresRole` / `@RequiresAuth`. 401 sans jeton valide, 403 sans les droits.

### `security/`, `hierarchy/`, `branding/`, `update/`, `exceptions/`

- `JwtSecurityContext` / `JwtPrincipal` : l'utilisateur et son jeton, posés par le `SecurityFilter`,
  lus par les ressources (`securityContext.currentUser`, `securityContext.scope`).
- `UserView` : pour une requête, l'arbre vu par un utilisateur : ce qu'il peut voir, ses favoris, et
  les boîtiers seulement pour un MANAGER ou un ADMIN (un VIEWER voit les températures, pas le matériel).
- `ServerLogo`, `SiteLogo`, `LogoImages` : le logo du serveur (voir [Serveur](#-serveur-apiv2server)).
- `RestComponent` : pour changer de version, le serveur s'arrête avec le code **75** ; son lanceur
  (`docker/rest/entrypoint.sh` ou `scripts/thermobox-rest.sh`, qui posent `THERMOBOX_RESTART=auto`) le
  relance sur `updates/current`. Lancé par un simple `java -jar`, il refuse d'installer : personne ne le
  relancerait.
- `GlobalExceptionHandler` : toute exception non attrapée devient `{"status": …, "message": …}`, 400
  pour une `IllegalArgumentException`, 500 sinon.

### Lancer en développement

Variables minimales : `THERMOBOX_DB_URL`, `THERMOBOX_DB_USER`, `THERMOBOX_DB_PASSWORD`,
`THERMOBOX_JWT_SECRET` (voir le README principal pour toutes). Puis exécutez `main` de
`ax.ibr.thermobox.restserver.MainKt` depuis l'IDE, ou après `mvn -Pdeploy -DskipTests package` :
`java -jar deploy/RestServer.jar` (avec `base.jar` dans le même dossier). Le serveur répond sur
http://localhost:8080/api/v2/server sans authentification.

## Documentation de l'API

L'API principale est en anglais et préfixée par `/api/v2`. Les requêtes nécessitent généralement un header `Authorization: Bearer <token>`, sauf pour les points d'entrée publics.

L'ancienne API (chemins `/api/...` sans `v2`, noms français) reste disponible pour les clients existants : voir [Ancienne API](#-ancienne-api-api-dépréciée).

### Authentification (`/api/v2/auth`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `POST` | `/login` | Échange des identifiants contre un JWT. | Public |
| `POST` | `/logout` | Révoque le jeton actuel. | Authentifié |
| `GET` | `/me` | Récupère les informations de l'utilisateur connecté. | Authentifié |

### Salles (`/api/v2/rooms`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Liste toutes les salles. | Authentifié |
| `GET` | `/{id}` | Récupère une salle par son ID. | Authentifié |
| `GET` | `/name/{name}` | Récupère une salle par son nom. | Authentifié |
| `POST` | `/` | Ajoute une nouvelle salle. | ADMIN |
| `PUT` | `/{id}` | Met à jour une salle existante. | ADMIN |
| `DELETE` | `/{id}` | Supprime une salle. | ADMIN |

### Supervision Thermique (`/api/v2/roomtemps`)
*C'est le cœur du système de supervision.*

| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/?limit=1000&offset=0` | Associations salle-température, les plus récentes d'abord, page par page (voir *Listes paginées*). | Authentifié |
| `GET` | `/{id}` | Récupère une association par son ID. | Authentifié |
| `GET` | `/room/{id}` | Liste les capteurs d'une salle spécifique. | Authentifié |
| `GET` | `/room/{id}/current` | Températures actuelles d'une salle. | Authentifié |
| `GET` | `/room/{id}/setpoint` | Consigne actuelle d'une salle. | Authentifié |
| `GET` | `/room/{id}/temperatures?start=...&end=...` | Historique des températures sur une période. | Authentifié |
| `GET` | `/room/{id}/setpoints?start=...&end=...` | Historique des consignes sur une période. | Authentifié |
| `GET` | `/room/{id}/average?start=...&end=...` | Moyenne des températures sur une période. | Authentifié |
| `POST` | `/` | Ajoute une nouvelle association (capteur). | ADMIN, MANAGER |
| `PUT` | `/{id}` | Met à jour une association existante. | ADMIN, MANAGER |
| `DELETE` | `/{id}` | Supprime une association. | ADMIN |
| `PUT` | `/room/{id}/setpoint?value=...` | **Envoie une nouvelle consigne** à la salle (déclenche le pilote matériel). | ADMIN, MANAGER |
| `GET` | `/room/{id}/measured/latest` | Récupère la dernière mesure réelle. | Authentifié |

### Températures (`/api/v2/temperatures`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/?limit=1000&offset=0` | Mesures et consignes, les plus récentes d'abord, page par page (voir *Listes paginées*). | ADMIN, MANAGER, VIEWER |
| `GET` | `/{id}` | Récupère une mesure par son ID. | ADMIN, MANAGER, VIEWER |
| `GET` | `/type/{type}?limit=1000&offset=0` | Par type (`setpoint` ou `measurement`), les plus récentes d'abord, page par page. | ADMIN, MANAGER, VIEWER |
| `GET` | `/average/{type}` | Moyenne pour un type spécifique. | ADMIN, MANAGER, VIEWER |
| `POST` | `/` | Ajoute une mesure manuellement. | ADMIN |
| `POST` | `/type/{type}` | Ajoute une mesure (`measurement`) ou une consigne (`setpoint`) : `{"value": 21.5, "date": "…"}`. | ADMIN |
| `PUT` | `/{id}` | Met à jour une mesure existante. | ADMIN |
| `DELETE` | `/{id}` | Supprime une mesure. | ADMIN |

**Listes paginées.** Ces listes peuvent contenir des millions de lignes (toutes les températures de la
base) : elles sont servies page par page, les plus récentes d'abord. `limit` (1 à 10 000, 1 000 par
défaut) et `offset` (0 par défaut) choisissent la page ; la réponse reste un tableau JSON. Avec la
première page (`offset=0`), l'en-tête `X-Total-Count` donne le nombre total de lignes (utilisateurs non
limités seulement ; il est exposé au navigateur par CORS). Un utilisateur limité reçoit, de la page, ce
qu'il a le droit de voir. Sans paramètre, un client reçoit donc les 1 000 plus récentes.

### Utilisateurs (`/api/v2/users`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/{id}` | Récupère un utilisateur par son ID. | ADMIN, Owner |
| `GET` | `/` | Liste tous les utilisateurs. | ADMIN |
| `POST` | `/` | Inscription d'un nouvel utilisateur (par défaut : VIEWER). | Public |
| `PUT` | `/{id}` | Met à jour un profil utilisateur. | ADMIN, Owner |
| `DELETE` | `/{id}` | Supprime un utilisateur. | ADMIN, Owner |
| `GET` | `/username/{username}` | Recherche un utilisateur par son nom d'utilisateur. | ADMIN |
| `GET` | `/{id}/access` | Accès de l'utilisateur (tout, ou seulement certains éléments). | ADMIN, Owner |
| `PUT` | `/{id}/access` | Remplace les accès (`{"restricted": true, "elements": [ids]}`). | ADMIN |

### Groupes (`/api/v2/groups`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Liste les groupes avec leurs membres et éléments. | ADMIN |
| `GET` | `/{id}` | Un groupe. | ADMIN |
| `POST` | `/` | Crée un groupe (`{"name", "description", "members": [ids], "elements": [ids]}`). | ADMIN |
| `PUT` | `/{id}` | Remplace un groupe. | ADMIN |
| `DELETE` | `/{id}` | Supprime un groupe (ses membres gardent leurs propres accès). | ADMIN |

### Hiérarchie (`/api/v2/hierarchy`)
*Bâtiment → (Étage) → Salle → Boîtier, tel que l'utilisateur connecté a le droit de le voir. Chaque élément porte son type (`BUILDING`, `FLOOR`, `ROOM`, `DEVICE`) ; les dates sont des chaînes ISO-8601 (`2026-10-21T14:05:00`).*

| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/tree` | Tous les éléments visibles : parent, température (moyenne des boîtiers), consigne (héritée ou non), photo, favori. Les boîtiers seulement pour MANAGER et ADMIN. | Authentifié |
| `GET` | `/elements/{id}` | Un élément et ce que sa page affiche : ancêtres, enfants, mesure de chaque boîtier, salles contenues, consignes propres en dessous. | Authentifié |
| `POST` | `/elements` | Crée un élément (`{"kind": "ROOM", "name": "…", "parentId": 4}`). | ADMIN |
| `PUT` | `/elements/{id}` | Renomme et déplace (`{"name": "…", "parentId": 4}`, `null` pour détacher). | ADMIN |
| `PUT` | `/elements/{id}/parent` | Déplace seulement (`{"parentId": 4}` ou `null`). | ADMIN |

La suppression d'un élément reste `DELETE /api/v2/rooms/{id}`.

### Climat (`/api/v2/climate`)
*Températures à n'importe quel niveau : un étage ou un bâtiment agrège les boîtiers qu'il contient. Périodes en instants ISO-8601 : `?start=2026-10-20T08:00:00Z&end=2026-10-21T08:00:00Z`.*

| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/{id}/history?start=…&end=…[&points=…]` | Courbes de la période : une par boîtier (salle), une par salle (étage, bâtiment), la moyenne et les consignes. Un VIEWER n'a que la moyenne. | Authentifié |
| `GET` | `/averages?ids=3,7&start=…&end=…[&points=…]` | Courbe moyenne de plusieurs éléments à la fois (`{"3": [points], "7": [points]}`). | Authentifié |
| `PUT` | `/{id}/setpoint` | Enregistre la consigne d'un bâtiment, d'un étage ou d'une salle et l'envoie à ses boîtiers (`{"value": 21.5, "force": false}`) ; ceux qui ont leur propre consigne la gardent, sauf `force`. | ADMIN, MANAGER |

**Courbes allégées (`points`).** Une courbe complète peut être énorme (un boîtier qui mesure chaque
minute donne 43 200 points par mois, environ 2 Mo de JSON ; un bâtiment de 96 boîtiers, 40 Mo). Chaque
courbe de mesures garde donc au plus `points` de ses points (**1 000 par défaut**, 3 à 10 000 ;
`points=0` pour les avoir toutes), choisis pour dessiner
presque la même ligne : algorithme *Largest-Triangle-Three-Buckets*, qui garde le premier et le dernier
point, puis dans chaque tranche le point le plus marquant, si bien que les pics et les creux restent
visibles (une moyenne par tranche les aplatirait). Les points renvoyés sont de vraies mesures, jamais
des valeurs inventées ; une courbe déjà assez courte est renvoyée entière. Les consignes ne sont jamais
réduites. **Au-delà de deux jours**, les courbes de mesures sont faites des moyennes horaires de chaque
boîtier (un point par heure, au milieu de l'heure) plutôt que de toutes les mesures : une semaine, 168
points par boîtier ; `points=0` donne toujours toutes les mesures, quelle que soit la période. Un
graphique n'a besoin que d'à peu près autant de points que de pixels de large : par
exemple `points=500` pour une courbe pleine largeur, `points=60` pour une miniature. Le format de la
réponse ne change pas.

Toutes les réponses sont en outre compressées en gzip quand le client l'accepte (`Accept-Encoding:
gzip`, ce que tout navigateur envoie). Un mois d'un boîtier passe ainsi d'environ 2 Mo à 125 Ko
compressés avec toutes ses mesures, et à environ 2 Ko avec `points=500`.

### Favoris (`/api/v2/favorites`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Ids des salles épinglées par l'utilisateur. | Authentifié |
| `PUT` | `/{id}` | Épingle une salle. | Authentifié |
| `DELETE` | `/{id}` | Retire une salle des favoris. | Authentifié |

### Photos (`/api/v2/rooms/{id}/image`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v2/rooms/{id}/image` | La photo (JPEG), avec `ETag`. | Authentifié |
| `PUT` | `/api/v2/rooms/{id}/image` | Remplace la photo : le corps est l'image (JPEG, PNG, GIF ou BMP, 10 Mo max). | ADMIN |
| `DELETE` | `/api/v2/rooms/{id}/image` | Retire la photo. | ADMIN |

### Réglages (`/api/v2/settings`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Réglages d'affichage communs à tous (`{"style": "glass"}` ou `"panel"`). | Authentifié |
| `PUT` | `/` | Change le style pour tout le monde. | ADMIN |

### Serveur (`/api/v2/server`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Nom et version du serveur, API servies, adresse et origine du logo (`{"name": "ThermoBox", "version": "26.12.0", "apiVersions": ["v1", "v2"], "logo": "/api/v2/server/logo?v=…", "logoSource": "custom" \| "site" \| "default"}`). `version` vaut `dev` pour un build sans numéro. | Public |
| `GET` | `/logo` | Le logo du serveur, dans son format. Avec `?size=192`, un PNG d'au plus 192 px quand le logo peut être redimensionné (PNG, JPEG, GIF, logo ThermoBox) ; un logo SVG, ICO ou WebP est servi tel quel. Avec `ETag`. | Public |
| `PUT` | `/logo` | Remplace le logo : le corps est l'image (PNG, JPEG ou GIF, gardée en PNG de 1024 px au plus ; ou SVG sans script), 5 Mo max. | ADMIN |
| `DELETE` | `/logo` | Revient au logo automatique. | ADMIN |
| `GET` | `/limits` | Températures acceptées (°C) : `{"measurement": {"min": -10, "max": 50}, "setpoint": {"min": -10, "max": 50}}`. | Authentifié |
| `PUT` | `/limits` | Change les limites (une partie absente est gardée). | ADMIN |
| `DELETE` | `/limits` | Revient aux limites par défaut (-10 à 50 °C). | ADMIN |
| `GET` | `/metrics` | Charge du serveur : processeur (`processCpu`, `systemCpu` en %), mémoire Java (`heapUsedMb`, `heapMaxMb`…), threads, ramasse-miettes. Lu par le test de charge (`loadtest/`). | ADMIN |

**Limites de température.** `measurement` borne les mesures : celles envoyées par un boîtier hors de ces limites sont ignorées (erreur de capteur), celles ajoutées à la main sont refusées. `setpoint` borne les consignes : une consigne hors limites est refusée (`ImpossibleValueException` sous le minimum, `TooHotException` au-dessus du maximum). Les limites sont gardées dans `temperature-limits.properties` du dossier de données ; aucune ne peut sortir de -100 à 200 °C.

Le logo affiché est, dans l'ordre :
1. celui donné par un administrateur (`PUT /api/v2/server/logo`), gardé dans `branding/` du dossier de données (`THERMOBOX_DATA_DIR`) ;
2. sinon celui du site auquel appartient le serveur : un serveur joint à `thermobox.example.com` reprend le logo de `example.com` (icônes déclarées par sa page d'accueil, la plus grande d'abord, sinon `/favicon.ico`). Le nom vient de la requête (`X-Forwarded-Host` derrière un proxy, sinon `Host`). Rien n'est cherché pour une adresse IP, `localhost` ou un nom local (`.local`, `.lan`…). Seul HTTPS vers des adresses publiques est utilisé ; le logo trouvé est gardé un jour (une heure si rien n'est trouvé) ;
3. sinon le logo de ThermoBox.

### ⬆ Mises à jour (`/api/v2/updates`)
Voir la section « Mises à jour depuis l'interface » du README principal.

## 🕰 Ancienne API (`/api`, dépréciée)

Les chemins de ThermoBox 26.10 restent servis tels quels pour les clients existants (le client Angular actuel, des scripts) : tout chemin sous `/api/` qui n'est pas sous `/api/v2/` est l'ancienne API. Le serveur réécrit la requête vers l'API anglaise et traduit le JSON dans les deux sens (`LegacyApi`, dans `filters`) : un ancien client reçoit les mêmes clés et les mêmes valeurs qu'avant. Seuls les textes libres (messages d'erreur, intitulés des contrôles de mise à jour) sont désormais en anglais.

Les nouveaux clients doivent utiliser `/api/v2`. Pour savoir lequel employer, un client appelle
`GET /api/server` (public), servi depuis 26.11 : `apiVersions` vaut `["v1"]` en 26.11 et `["v1", "v2"]`
à partir de 26.12 ; un serveur plus ancien répond 404. La réponse a les mêmes clés que
`GET /api/v2/server`.

Correspondances :

| Ancienne API | API v2 |
| :--- | :--- |
| `/api/salles`, `/api/salletemps`, `/api/hierarchie`, `/api/climat`, `/api/favoris`, `/api/reglages` | `/api/v2/rooms`, `/api/v2/roomtemps`, `/api/v2/hierarchy`, `/api/v2/climate`, `/api/v2/favorites`, `/api/v2/settings` |
| `/api/auth`, `/api/users`, `/api/groups`, `/api/temperatures`, `/api/updates` | même chemin sous `/api/v2` |
| `…/arbre`, `…/historique`, `…/moyennes`, `…/consigne`, `…/consignes`, `…/acces` | `…/tree`, `…/history`, `…/averages`, `…/setpoint`, `…/setpoints`, `…/access` |
| `/api/temperatures/type/consigne`, `…/mesurer` | `/api/v2/temperatures/type/setpoint`, `…/measurement` |
| Clés `salle`, `salles`, `consigne`, `consignes`, `consignesPropres`, `boitiers`, `boitiersVisibles`, `noeuds`, `tout`, `rattache`, `derniereMesure`, `favori`, `heritee`, `ancetres`, `enfants`, `mesures`, `moyenne`, `conservees`, `remplacees`, `echecs` | `room`, `rooms`, `setpoint`, `setpoints`, `ownSetpoints`, `devices`, `devicesVisible`, `nodes`, `all`, `attached`, `lastMeasurement`, `favorite`, `inherited`, `ancestors`, `children`, `measurements`, `average`, `kept`, `replaced`, `failures` |
| `kind` : `BATIMENT`, `ETAGE`, `SALLE`, `BOITIER` | `BUILDING`, `FLOOR`, `ROOM`, `DEVICE` |
| `type` d'un utilisateur : `GESTIONNAIRE` | `MANAGER` |
| `type` d'une version : `CORRECTIVE`, `FONCTIONNELLE` (`typeLabel` : `Corrective`, `Fonctionnelle`) | `PATCH`, `FEATURE` (`Patch`, `Feature`) |
| `state` : `EN_ATTENTE`, `EN_COURS`, `IGNOREE`, `ECHEC`, `REDEMARRAGE`, `TERMINEE` | `PENDING`, `RUNNING`, `SKIPPED`, `FAILED`, `RESTARTING`, `DONE` |
| `level` : `AVERTISSEMENT`, `BLOQUANT` | `WARNING`, `BLOCKING` |
| `style` : `verre`, `panneau` | `glass`, `panel` |


## Exemple de requête (Envoi de consigne)
Pour envoyer une nouvelle température cible à la salle ID `5` :

```bash
curl -X PUT "http://localhost:8080/api/v2/roomtemps/room/5/setpoint?value=21.5" \
     -H "Authorization: Bearer <votre_token>" \
     -H "Content-Type: application/json"
```
