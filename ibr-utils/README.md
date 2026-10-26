# Module ibr-utils

`ibr-utils` (`ax.ibr:ibr-utils`) rassemble des briques sans lien avec le métier de ThermoBox, réutilisables
dans un autre projet : contrat CRUD, service JPA générique, annotations de sécurité REST, exceptions.
Il ne dépend d'aucun autre module du projet ; tous les autres dépendent de lui.

## Arborescence

```
ibr-utils/src/main/kotlin/ax/ibr/utils/
├── services/
│   ├── CrudService.kt        contrat CRUD générique
│   └── jpa/CrudJpaService.kt implémentation JPA de ce contrat
├── rest/                     annotations lues par le filtre de sécurité de RestServer
│   ├── PublicEndpoint.kt
│   ├── RequiresAuth.kt
│   └── RequiresRole.kt
├── exceptions/               exceptions communes
├── Logger.kt, JsonLogger.kt  journal JSON (non utilisé par ThermoBox)
└── Cryptographic.kt          ébauche, non utilisée
```

## `services/`

### `CrudService<T>`

Le contrat de base de tous les services de stockage : `add(t)`, `update(t)`, `remove(t)`, `getAll()`,
`getById(id)`, plus les alias `save` (= `update`), `delete` (= `remove`), `findAll`, `findById`.

### `jpa/CrudJpaService<T>`

Implémentation JPA réutilisable : une sous-classe donne l'`EntityManagerFactory` et la classe de
l'entité, et hérite des cinq opérations.

- Un `EntityManager` n'est pas *thread-safe* : le service n'en garde jamais. Chaque opération en ouvre
  un et le ferme. Un service peut donc être un singleton partagé par toutes les requêtes.
- `read { em -> … }` exécute une lecture, `transaction { em -> … }` un bloc dans une transaction
  (validée en cas de succès, annulée à la moindre exception). Les sous-classes s'en servent pour leurs
  requêtes JPQL.
- `update` fait un `merge`, `remove` ré-attache l'entité avant de la supprimer. Les entités renvoyées
  sont détachées.

Son équivalent JDBC, `CrudJdbcService`, est dans `Persistence/…/jdbc` (il dépend de la base de
ThermoBox).

## `rest/` — annotations de sécurité

Elles ne font rien seules : le `SecurityFilter` de RestServer les lit sur chaque requête.

| Annotation | Sur | Effet |
|---|---|---|
| *(aucune)* | | Un jeton valide est exigé : **tout point d'entrée est protégé par défaut**. |
| `@PublicEndpoint` | méthode ou classe | Aucune authentification (connexion, inscription, nom et logo du serveur). |
| `@RequiresRole("ADMIN")` | méthode | L'utilisateur doit avoir ce rôle (`UserType.hasRole` : un ADMIN a tous les rôles, un MANAGER aussi celui de VIEWER). |
| `@RequiresAuth(roles = […], allowOwner = …, ownerParam = "id")` | méthode | Authentifié ; si `roles` n'est pas vide, un de ces rôles **ou**, avec `allowOwner`, être le propriétaire : l'id du chemin nommé `ownerParam` est celui de l'utilisateur connecté. |

Sans jeton ou avec un jeton invalide : 401. Droits insuffisants : 403.

## `exceptions/`

| Exception | Sens |
|---|---|
| `AlreadyExistsException` | La ressource existe déjà (nom pris…). Prévue pour un 409. |
| `DontExistException` | La ressource n'existe pas (un boîtier inconnu à qui on envoie une consigne…). |
| `NullException` | Une valeur nulle là où elle est interdite (un élément sans id). |
| `PermissionDeniedException` | Opération non autorisée. Prévue pour un 403. |

Les ressources REST attrapent ces exceptions et choisissent le code de réponse ; une exception
qu'aucune n'attrape arrive au `GlobalExceptionHandler` de RestServer, qui répond 400 pour une
`IllegalArgumentException` et 500 sinon.

## `Logger`, `JsonLogger`, `Cryptographic`

`Logger` écrit des entrées (`LogEntry` : message, niveau, horodatage) dans `env/axibr/logs.json` via
`JsonLogger`, en relisant et réécrivant tout le fichier à chaque entrée. ThermoBox ne s'en sert pas : le
serveur écrit sur la sortie standard et via `java.util.logging`. `Cryptographic` est une ébauche
(`encrypt` et `decrypt` renvoient une chaîne vide) ; les mots de passe sont hachés par
`Business/security/PasswordHasher` (BCrypt). Ne pas les utiliser en l'état.

## Tests

Le module n'a pas de tests propres : `CrudJpaService` est exercé à travers les services de Persistence,
les annotations à travers le `SecurityFilter`.
