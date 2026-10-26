## Persistence Module

Le module `Persistence` gère l'accès aux données. Il assure la liaison entre les entités du module Common et la base de données SQL.

### Fonctionnalités principales :
- Data Services : services de persistance (CRUD et requêtes) sur les entités (salles, températures, utilisateurs, accès, groupes…).
- Deux implémentations de chaque service, au choix : JPA (EclipseLink) ou JDBC direct.
- Migrations Flyway : le schéma est créé et mis à jour au démarrage, avant l'ouverture de la base.

### Utilisation :
Ce module est injecté dans la couche Business via la fabrique de services (`PersistenceFactory`). Il isole la logique métier des détails d'implémentation SQL.

### JPA ou JDBC (`THERMOBOX_DB_ACCESS`)

| Valeur | Implémentation | Paquet |
|---|---|---|
| `jpa` (défaut) | JPA avec EclipseLink, requêtes JPQL | `persistence.jpa` |
| `jdbc` | SQL écrit à la main, `java.sql` seul | `persistence.jdbc` |

Les deux lisent et écrivent les mêmes tables (celles des migrations), de la même façon : on peut passer
de l'une à l'autre à n'importe quel redémarrage, sans migration. Une valeur inconnue arrête le démarrage.

L'implémentation JDBC (`jdbc/`) :
- `JdbcDatabase` : connexions (`THERMOBOX_DB_URL/USER/PASSWORD`), transactions, et jusqu'à 8 connexions
  gardées ouvertes entre deux requêtes (une connexion morte est remplacée) ;
- `Rows` : correspondance entre lignes et entités, dont les types enregistrés dans `DTYPE`
  (`Building`, `Floor`, `Room`, `Device` ; `Setpoint`, `Measurement`) et le rôle d'un utilisateur
  (stocké par son rang, comme JPA) ;
- `CrudJdbcService` : ajout (l'entité reçoit l'identifiant généré), mise à jour, suppression, lecture,
  avec le même contrat que `CrudJpaService` ;
- un `…JdbcImpl` par service. Comme avec JPA, une température est enregistrée et supprimée avec son
  lien à la salle (`RoomTempAttr`), et les remplacements (accès, groupes) se font en une transaction.

### Tests

`JdbcDataServicesTest` vérifie chaque service JDBC sur une vraie base MySQL ou MariaDB. Il ne s'exécute
que si `THERMOBOX_TEST_DB_URL`, `THERMOBOX_TEST_DB_USER` et `THERMOBOX_TEST_DB_PASSWORD` désignent une
base de test : **elle est vidée avant chaque test**. Les migrations y sont appliquées d'abord, sauf avec
`THERMOBOX_DB_MIGRATE=false`.

```bash
THERMOBOX_TEST_DB_URL='jdbc:mysql://localhost:3306/thermobox_test' \
THERMOBOX_TEST_DB_USER=thermobox THERMOBOX_TEST_DB_PASSWORD=… \
mvn -pl Persistence -am test
```
