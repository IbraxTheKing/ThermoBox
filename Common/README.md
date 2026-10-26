## Common Module

Le module `Common` définit le modèle de domaine central du projet ThermoBox. Ce module contient les entités JPA représentant la structure physique et logique du système.

### Fonctionnalités principales :
- Entités Spatiales : Modélisation des Bâtiments, Étages et Salles (`Building`, `Floor`, `Room`, avec une stratégie d'héritage Single Table).
- Capteurs & Mesures : Définition des Boîtiers de capteurs (`Device`), des mesures de Température (`Measurement`) et des Consignes (`Setpoint`).
- Utilisateurs : Gestion des types d'utilisateurs et des profils.

### Utilisation :
Ce module est la base de données partagée par les couches Persistence, Business et RestServer. Il ne contient aucune logique métier, uniquement des définitions de données.