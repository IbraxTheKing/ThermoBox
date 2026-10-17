## 💾 Persistence Module

Le module `Persistence` est responsable de l'accès aux données (DAO/Repository). Il fait le pont entre les entités du module `Common` et la base de données SQL.

### Fonctionnalités principales :
- **Configuration JPA** : Configuration des sources de données, des unités de persistance et des stratégies de génération d'identifiants.
- **Data Services** : Implémentation des services de persistance pour effectuer les opérations CRUD sur les entités (Salles, Températures, etc.).

### Utilisation :
Ce module est injecté dans la couche `Business` via les fabriques de services (`PersistenceFactory`). Il isole totalement la logique métier des détails d'implémentation SQL.
