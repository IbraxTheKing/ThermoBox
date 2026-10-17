## Base Module (Deployment)

Le module `Base` est un module de packaging spécialisé pour le déploiement en production.

### Fonctionnalités principales :
- Regroupement (Shading) : Il fusionne les modules ibr-utils, Common, Persistence et Business dans un seul fichier JAR (base.jar).
- Optimisation : Permet de réduire le nombre de dépendances à charger par les clients finaux (API ou Web).

### Utilisation :
Ce module n'est pas utilisé directement pour le développement quotidien, mais est essentiel pour la production. Il doit être compilé avec le profil deploy :
```bash
mvn -Pdeploy package
```