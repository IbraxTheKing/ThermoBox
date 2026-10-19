## Business Module

Le module `Business` contient la logique métier de ThermoBox. C'est ici que sont appliquées les règles de calcul et les décisions opérationnelles.

### Fonctionnalités principales :
- Règles Métier : Implémentation des règles pour les consignes thermiques et la hiérarchie spatiale (ex: propagation automatique des réglages).
- Protocol Drivers : Gestion de la communication avec le matériel, incluant un pilote simulé et une intégration MQTT.
- Validation : Vérification de la cohérence des données avant persistance.

### Utilisation :
Ce module est consommé par RestServer (pour les requêtes API) et JSF (pour l'interface utilisateur). Il utilise les services du module Persistence pour le stockage des résultats.