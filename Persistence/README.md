## Persistence Module

Le module `Persistence` gère l'accès aux données (DAO/Repository). Il assure la liaison entre les entités du module Common et la base de données SQL.

### Fonctionnalités principales :
- Configuration JPA : Configuration des sources de données, des unités de persistance et des stratégies de génération d'identifiants.
- Data Services : Implémentation des services de persistance pour effectuer les opérations CRUD sur les entités (Salles, Températures, etc.).

### Utilisation :
Ce module est injecté dans la couche Business via les fabriques de services (PersistenceFactory). Il isole la logique métier des détails d'implémentation SQL.