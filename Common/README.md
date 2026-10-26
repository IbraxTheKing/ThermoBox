# Module Common

`Common` est le vocabulaire partagé de ThermoBox : les **entités** (ce qui est enregistré en base), les
**interfaces des services** (ce qu'on peut leur demander) et les **types des mises à jour**. Il ne
contient aucune logique métier ni aucun accès à la base : seulement des définitions, utilisées par
toutes les autres couches.

```
ibr-utils  ←  Common  ←  Persistence  ←  Business  ←  RestServer
```

- **Dépend de** : `ibr-utils` (interface `CrudService`), `jakarta.persistence-api` (annotations JPA).
- **Utilisé par** : Persistence (implémente les interfaces de stockage), Business (implémente les
  interfaces métier), RestServer (manipule les entités et les résultats).

## Arborescence

```
Common/src/main/kotlin/ax/ibr/thermobox/common/
├── entities/   entités JPA : hiérarchie, températures, utilisateurs, accès, groupes, photos, favoris
├── services/   interfaces des services (contrats), implémentées dans Persistence et Business
├── access/     AccessScope : ce qu'un utilisateur a le droit de voir
└── update/     modèle des mises à jour (versions, contrôles, installation)
```

## `entities/` — le modèle de données

Toutes les classes sont des entités JPA (`@Entity`). Le plugin Kotlin `noarg` (préréglage `jpa`, voir
`pom.xml`) leur ajoute le constructeur sans argument qu'exige JPA ; il n'est pas appelable depuis le
code Kotlin. Les noms de tables et de colonnes sont ceux des migrations Flyway
(`Persistence/src/main/resources/db/migration`).

### La hiérarchie : bâtiments, étages, salles, boîtiers

| Classe | Table | `DTYPE` | Rôle |
|---|---|---|---|
| `Room` | `room` | `Room` | Classe de base, et une salle. Champs : `id`, `name`. |
| `Building` | `room` | `Building` | Un bâtiment (racine de l'arbre). |
| `Floor` | `room` | `Floor` | Un étage, dans un bâtiment. |
| `Device` | `room` | `Device` | Un boîtier de mesure (capteur physique), dans une salle. |

Les quatre partagent la table `room` (héritage `SINGLE_TABLE`) : la colonne `DTYPE` dit de quel type
est chaque ligne. `RoomKind` (enum `BUILDING`, `FLOOR`, `ROOM`, `DEVICE`) décrit les niveaux et les
liens permis, et l'extension `Room.kind` donne le niveau d'un élément :

| Élément | Peut être dans |
|---|---|
| Bâtiment | rien (racine) |
| Étage | un bâtiment |
| Salle | un étage, ou directement un bâtiment (annexe sans étage) |
| Boîtier | une salle |

Le lien parent → enfant est une ligne de `RoomRoomAttr` (table `room_attr`) : `roomA` contient
`roomB`. Un enfant n'a qu'un parent ; ces règles sont vérifiées par `Business` (`HierarchyRules`), pas
par la base. Un élément sans parent est « non rattaché » (un boîtier qui vient de s'annoncer, par
exemple).

### Les températures

| Classe | Table | `DTYPE` | Rôle |
|---|---|---|---|
| `Temperature` | `temperature` | `Temperature` | Classe de base : `id`, `value` (°C), `date` (`LocalDateTime`, heure du serveur). |
| `Measurement` | `temperature` | `Measurement` | Une mesure envoyée par un boîtier (ou ajoutée à la main). |
| `Setpoint` | `temperature` | `Setpoint` | Une consigne : la température voulue. |
| `RoomTempAttr` | `room_temperature_attr` | — | Rattache une température à un élément : une mesure à son boîtier, une consigne à la salle, l'étage, le bâtiment ou au boîtier qui l'a reçue. |

Une température n'existe pas seule : elle est créée et supprimée avec son `RoomTempAttr`
(`cascade = ALL`). Les mesures vont **toujours** sur un `Device` ; la température d'une salle, d'un
étage ou d'un bâtiment est calculée (moyenne de ses boîtiers) par `ClimateService`.

### Utilisateurs et droits

| Classe | Table | Rôle |
|---|---|---|
| `User` | `user` | Compte : `username`, `password` (toujours un hachage BCrypt une fois enregistré), `type`. |
| `UserType` | (colonne `type`, par son rang) | `ADMIN` (tout), `MANAGER` (consignes, boîtiers), `VIEWER` (lecture). `hasRole()` dit si un type a les droits d'un autre : un ADMIN a tous les rôles, un MANAGER a aussi celui de VIEWER. |
| `RestrictedAccess` | `restricted_access` | Présence = l'utilisateur ne voit que ses accès. Absence = il voit tout (comptes créés avant la gestion des accès). |
| `Access` | `user_access` | Un élément accordé à un utilisateur limité, avec tout ce qu'il contient. |
| `UserGroup` | `user_group` | Groupe d'utilisateurs : `name` (unique), `description`. |
| `UserGroupMember` | `user_group_member` | Un utilisateur dans un groupe. |
| `UserGroupAccess` | `user_group_access` | Un élément accordé par un groupe à tous ses membres. |

### Le reste

| Classe | Table | Rôle |
|---|---|---|
| `Favorite` | `favorite` | Une salle épinglée par un utilisateur sur son tableau de bord. |
| `RoomImage` | `room_image` | La photo d'un élément (une au plus), dans sa propre table pour que lire les salles ne charge jamais les images. `updated` sert aussi de version dans l'URL de l'image. |

## `services/` — les contrats

Chaque interface décrit ce qu'on peut demander, sans dire comment. Les interfaces CRUD héritent de
`CrudService<T>` (`ibr-utils`) : `add`, `update`, `remove`, `getAll`, `getById`.

| Interface | Implémentée par | Rôle |
|---|---|---|
| `RoomService` | Persistence (stockage) et Business | Éléments de la hiérarchie : par type exact (`getAllByType`), par nom. |
| `RoomRoomAttrService` | Persistence et Business | Liens parent → enfant : enfants directs, parent direct. |
| `TemperatureService` | Persistence et Business | Températures : par type, moyenne. |
| `RoomTempAttrService` | Persistence et Business | Températures d'un élément : dernière mesure, consigne, historique sur une période, moyennes. |
| `UserService` | Persistence et Business | Comptes : par nom, par type. |
| `FavoriteService` | Persistence et Business | Favoris d'un utilisateur, ou pointant vers une salle. |
| `RoomImageService` | Persistence et Business | Photo d'un élément ; dates de toutes les photos sans les charger. |
| `HierarchyService` | Business | L'arbre : racines, enfants, parent, chemin, boîtiers et salles contenus, déplacement (`setParent`). |
| `ClimateService` | Business | Températures à n'importe quel niveau : mesures des boîtiers, moyenne, consigne effective (propre ou héritée), historiques, envoi d'une consigne. |
| `AccessService` | Business | Qui voit quoi : accès d'un utilisateur, `scope()` (un `AccessScope`), filtrage des températures. |
| `UserGroupService` | Business | Groupes, leurs membres et leurs éléments. |

Les résultats de `ClimateService` sont des classes de données du même fichier : `DeviceMeasurement`,
`DeviceHistory`, `EffectiveSetpoint` (la consigne, l'élément d'où elle vient, héritée ou non) et
`SetpointDispatch` (ce qu'a fait l'envoi d'une consigne : éléments qui ont gardé la leur, remplacés,
boîtiers atteints, échecs).

## `access/` — `AccessScope`

Ce qu'un utilisateur peut voir, calculé une fois par requête par `AccessService.scope(user)` :

- **accessible** : accordé directement ou par un groupe, ou contenu (à n'importe quelle profondeur)
  dans un élément accordé. On en montre tout : données, page, consigne (pour un MANAGER).
- **visible** : accessible, ou ancêtre d'un élément accessible. On n'en montre que le nom, pour situer
  les éléments accessibles (fil d'Ariane, arbre), jamais ses données (la moyenne d'un bâtiment
  parlerait de salles interdites).

`AccessScope.ALL` (voit tout : ADMIN, ou compte non limité) et `AccessScope.NONE` sont fournis.

## `update/` — les mises à jour

- `Version` : numéro `AA.MM.PATCH`, avec éventuellement `-nightly.N`, `-beta.N` ou `-rc.N` ;
  lecture (`parse`), comparaison, même série (`sameSeries`), type de passage depuis la version
  installée (`typeFrom` : corrective dans la même série, fonctionnelle sinon).
- `Updates.kt` : `UpdateType` (`PATCH` corrective / `FEATURE` fonctionnelle), `Channel` (`STABLE`,
  `PRERELEASE`), `Artifact` (fichier publié avec son SHA-256), `AvailableVersion`, `Check` et
  `CheckLevel` (contrôles avant installation), `Preparation`, `InstallOptions`, `Installation`, `Step`
  et leurs états, `UpdateStatus` (ce que renvoie `GET /api/v2/updates`), `Component` (le programme qui
  se met à jour : le serveur REST).

La logique est dans `Business/update`, l'API dans `RestServer` (`UpdateResource`).

## Ajouter une entité

1. La classe dans `entities/`, avec `@Entity` et `@Table(name = "…")` (nom anglais, en minuscules).
2. Sa ligne `<class>` dans `Persistence/src/main/resources/META-INF/persistence.xml`.
3. Sa table dans une **nouvelle** migration `V<n>__….sql` (`MigrationsTest` vérifie que chaque entité a
   sa table).
4. Son service de stockage dans Persistence, en JPA **et** en JDBC (`THERMOBOX_DB_ACCESS`).
