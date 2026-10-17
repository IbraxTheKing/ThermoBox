## 🧠 Business Module

Le module `Business` contient toute la logique métier de ThermoBox. C'est le cœur du système où les règles sont appliquées et les décisions prises.

### Fonctionnalités principales :
- **Règles Métier** : Implémentation des règles complexes pour les consignes thermiques et la hiérarchie spatiale (ex: propagation automatique des réglages).
- **Protocol Drivers** : Gestion de la communication avec le matériel, incluant un pilote simulé et une intégration MQTT.
- **Validation** : Vérification de la cohérence des données avant persistance.

### Utilisation :
Ce module est consommé par `RestServer` (pour les requêtes API) et `JSF` (pour l'interface utilisateur). Il utilise les services du module `Persistence` pour stocker les résultats.
