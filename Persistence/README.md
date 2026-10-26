# Module Persistence

`Persistence` est la seule couche qui parle à la base de données. Il stocke et relit les entités de
`Common`, tient le schéma à jour (migrations Flyway) et sait faire une sauvegarde complète de la base.
La couche métier (`Business`) ne voit que ses interfaces, obtenues par `PersistenceFactory`.

```
ibr-utils  ←  Common  ←  Persistence  ←  Business  ←  RestServer
```

- **Dépend de** : `Common`, `ibr-utils`, EclipseLink (JPA), MySQL Connector/J, Flyway.
- **Utilisé par** : `Business` uniquement.
- **Base** : MySQL 8 (ou MariaDB), dont l'adresse et les identifiants viennent de l'environnement :
  `THERMOBOX_DB_URL` (`jdbc:mysql://localhost:3306/thermobox` par défaut), `THERMOBOX_DB_USER`,
  `THERMOBOX_DB_PASSWORD`. Aucun identifiant n'est écrit dans le code ni dans `persistence.xml`.

## Arborescence

```
Persistence/src/main/
├── kotlin/ax/ibr/thermobox/persistence/
│   ├── dataservices/  interfaces de stockage (…DataService) et PersistenceFactory
│   ├── jpa/           implémentation JPA de chaque service (…DataServiceJPAImpl)
│   ├── jdbc/          implémentation JDBC de chaque service (…DataServiceJdbcImpl)
│   ├── migration/     DatabaseMigrations : lance Flyway au démarrage
│   └── backup/        DatabaseBackup : sauvegarde SQL complète, avant une mise à jour
└── resources/
    ├── META-INF/persistence.xml   unité de persistance JPA « thermoboxPU »
    └── db/migration/              scripts Flyway V1__…, V2__…
Persistence/src/test/              tests des migrations, des services JDBC, de la fabrique
```

## `dataservices/` — interfaces et fabrique

Chaque `…DataService` est l'interface de stockage d'une entité. La plupart reprennent l'interface de
`Common` telle quelle (`RoomDataService : RoomService`…) ; `AccessDataService` et
`UserGroupDataService` ajoutent les opérations en bloc dont la couche métier a besoin (remplacer tous
les accès d'un utilisateur, tous les membres d'un groupe… en une transaction).

| Interface | Tables |
|---|---|
| `UserDataService` | `user` |
| `RoomDataService` | `room` (bâtiments, étages, salles, boîtiers) |
| `RoomRoomAttrDataService` | `room_attr` (liens parent → enfant) |
| `TemperatureDataService` | `temperature` |
| `RoomTempAttrDataService` | `room_temperature_attr` (+ `temperature`) |
| `FavoriteDataService` | `favorite` |
| `RoomImageDataService` | `room_image` |
| `AccessDataService` | `user_access`, `restricted_access` |
| `UserGroupDataService` | `user_group`, `user_group_member`, `user_group_access` |

`PersistenceFactory` fournit un exemplaire unique de chaque service (`getRoomDataService()`…). Au
premier appel, elle lance les migrations, puis ouvre l'accès choisi par `THERMOBOX_DB_ACCESS`. Elle
expose aussi `backupDatabase(file)` et `close()` (appelé à l'arrêt du serveur).

## JPA ou JDBC (`THERMOBOX_DB_ACCESS`)

| Valeur | Implémentation | Paquet |
|---|---|---|
| `jpa` (défaut) | JPA avec EclipseLink, requêtes JPQL | `jpa/` |
| `jdbc` | SQL écrit à la main, `java.sql` seul | `jdbc/` |

Les deux lisent et écrivent les mêmes tables, avec les mêmes valeurs : on peut passer de l'une à
l'autre à n'importe quel redémarrage, sans migration. Une valeur inconnue arrête le démarrage.

### `jpa/`

Chaque `…JPAImpl` hérite de `CrudJpaService` (`ibr-utils`) pour le CRUD et écrit ses requêtes en
JPQL. Un `EntityManager` est ouvert par opération et refermé aussitôt : les services sont partagés
sans risque entre les requêtes, et les entités renvoyées sont détachées. L'unité de persistance
(`META-INF/persistence.xml`) liste les entités et laisse le schéma aux migrations
(`schema-generation.database.action = none`).

### `jdbc/`

- `JdbcDatabase` : connexions (mêmes variables `THERMOBOX_DB_*`), transactions (`transaction { }` :
  validée ou annulée en bloc), et jusqu'à 8 connexions gardées ouvertes entre deux requêtes (une
  connexion morte est remplacée). Le pilote JDBC est chargé d'après l'URL (`jdbc:mysql:`,
  `jdbc:mariadb:`).
- `Rows` : correspondance entre lignes et entités. Le type d'un élément ou d'une température vient de
  `DTYPE` (`Building`, `Floor`, `Room`, `Device` ; `Setpoint`, `Measurement`), le rôle d'un
  utilisateur de son rang (`0` ADMIN, `1` MANAGER, `2` VIEWER), comme JPA l'enregistre.
- `CrudJdbcService` : ajout (l'entité reçoit l'identifiant généré), mise à jour (une entité sans id est
  ajoutée), suppression, lecture ; même contrat que `CrudJpaService`.
- un `…JdbcImpl` par service. Les entités liées sont lues dans la même requête (jointures) : un
  favori arrive avec son utilisateur et sa salle. Comme avec JPA, une température est enregistrée et
  supprimée avec son `RoomTempAttr`.

## `migration/` et `resources/db/migration/` — le schéma

Le schéma appartient aux scripts Flyway, lancés par `DatabaseMigrations.migrate` avant toute
utilisation de la base (JPA comme JDBC ne créent ni ne modifient jamais de table).

| Script | Contenu |
|---|---|
| `V1__schema_26_10.sql` | Les tables de 26.10, telles qu'EclipseLink les créait (noms français). |
| `V2__acces.sql` | Accès des utilisateurs (26.11). |
| `V3__groupes.sql` | Groupes d'utilisateurs (26.11). |
| `V4__english_names.sql` | Noms anglais des tables, colonnes et valeurs de `DTYPE` (26.12). Une version antérieure ne peut plus ouvrir la base. |

- Base vide : tous les scripts s'exécutent.
- Base créée avant Flyway (jusqu'à 26.10) : elle est marquée version 0 puis tous les scripts passent ;
  ils n'utilisent que `CREATE TABLE IF NOT EXISTS`, les tables existantes sont gardées.
- Base déjà migrée par une version plus récente (retour arrière) : les scripts inconnus sont tolérés.
- Plusieurs serveurs qui démarrent ensemble : Flyway prend un verrou MySQL, le second attend.
- `THERMOBOX_DB_MIGRATE=false` laisse le schéma à l'administrateur.

**Règle** : un script publié ne change plus jamais (Flyway compare leurs sommes de contrôle et refuse
de démarrer sinon). Toute modification du schéma est un nouveau script `V<n+1>__description.sql`.

## `backup/` — `DatabaseBackup`

Sauvegarde complète faite avant une mise à jour, sans `mysqldump` (absent des images Docker) : le
schéma (`SHOW CREATE TABLE`) puis les données en `INSERT`, compressés en gzip, dans
`backups/` du dossier de données. Toute la sauvegarde lit un même instantané InnoDB (les mesures
continuent d'arriver pendant ce temps), les lignes sont lues en flux (photos et historique peuvent être
gros) et le fichier n'a son nom définitif qu'une fois complet. Restauration :
`gunzip -c fichier.sql.gz | mysql -u <utilisateur> -p <base>`.

## Tests

| Test | Ce qu'il vérifie | Base nécessaire |
|---|---|---|
| `migration/MigrationsTest` | Noms et ordre des scripts ; chaque entité de `persistence.xml` a sa table. | non |
| `jdbc/AccessModeTest` | Lecture de `THERMOBOX_DB_ACCESS`, pilote d'après l'URL. | non |
| `jdbc/JdbcDataServicesTest` | Chaque service JDBC. | oui, une base de test |
| `dataservices/PersistenceFactoryTest` | La fabrique renvoie les services JPA par défaut. | oui (`THERMOBOX_DB_*`) |

`JdbcDataServicesTest` ne s'exécute que si `THERMOBOX_TEST_DB_URL`, `THERMOBOX_TEST_DB_USER` et
`THERMOBOX_TEST_DB_PASSWORD` désignent une base de test : **elle est vidée avant chaque test**. Les
migrations y sont appliquées d'abord, sauf avec `THERMOBOX_DB_MIGRATE=false`.

```bash
THERMOBOX_TEST_DB_URL='jdbc:mysql://localhost:3306/thermobox_test' \
THERMOBOX_TEST_DB_USER=thermobox THERMOBOX_TEST_DB_PASSWORD=… \
mvn -pl Persistence -am test
```

## Ajouter une requête

Ajoutez-la à l'interface `…DataService` (ou à celle de `Common`), puis aux **deux** implémentations,
`jpa/` et `jdbc/`, et un cas dans `JdbcDataServicesTest`. Une colonne ou une table nouvelle demande
aussi une migration.
