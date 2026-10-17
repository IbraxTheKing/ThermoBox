# ThermoBox

ThermoBox est une application de supervision thermique permettant la gestion des températures dans des infrastructures complexes telles que des bâtiments ou des salles serveurs. Le système permet d'organiser des espaces hiérarchiques, de suivre les mesures en temps réel et de gérer des consignes dynamiques.

## Quick Start

### Prérequis
- JDK 23
- Maven 3.9+
- MySQL (ou autre base de données compatible JDBC)

### Installation & Configuration
1. Clonez le dépôt :
   ```bash
   git clone https://github.com/IbraxTheKing/ThermoBox.git
   cd ThermoBox
   ```

2. Configurez les variables d'environnement nécessaires (exemple pour Linux/macOS) :
   ```bash
   export THERMOBOX_DB_URL='jdbc:mysql://localhost:3306/thermobox'
   export THERMOBOX_DB_USER='votre_utilisateur'
   export THERMOBOX_DB_PASSWORD='votre_mot_de_passe'
   export THERMOBOX_JWT_SECRET='une_cle_secrete_de_32_octets_minimum'
   ```

3. Compilez et installez les modules :
   ```bash
   mvn clean install
   ```

### Lancement de l'API REST
Pour lancer le serveur API, exécutez la classe principale du module `RestServer` depuis votre IDE ou via Maven en ciblant le module spécifique. Le serveur est accessible sur :
- URL : http://localhost:8080/
- Préfixe API : /api

## Architecture du Projet

Le projet est structuré en modules Maven pour assurer une séparation des responsabilités :

| Module | Rôle | Description |
| :--- | :--- | :--- |
| Common | Domain | Contient les entités JPA, les modèles de données et les interfaces partagées. |
| Persistence | Data Access | Gère l'accès aux données via JPA et fournit les services de persistance. |
| Business | Core Logic | Contient la logique métier, les règles de calcul des consignes et les pilotes de communication (MQTT/Simulé). |
| RestServer | Entry Point | Exposition des ressources via une API REST sécurisée par JWT (Jersey + Jetty). |
| JSF | UI Layer | Interface utilisateur web construite avec Jakarta Faces et PrimeFaces. |
| ibr-utils | Shared Utils | Utilitaires transversaux : Logging JSON, Cryptographie, Gestion d'exceptions personnalisées. |
| Base | Deployment | Module de packaging regroupant les modules internes pour faciliter le déploiement (Fat Jar). |

## Stack Technique
- Langage : Kotlin 2.4+
- Build Tool : Maven
- Persistence : Jakarta Persistence (JPA) / Hibernate
- API : Jersey, Jetty
- Sécurité : JWT (JSON Web Tokens)
- Frontend : Jakarta Faces (JSF), PrimeFaces

## Déploiement
Le projet inclut un profil de déploiement spécifique. Pour générer les artefacts destinés à la production (incluant le regroupement des modules dans base.jar) :

```bash
mvn -Pdeploy -DskipTests package
```
Les fichiers produits sont disponibles dans le dossier /deploy.

## Notes importantes
- Base de données : Par défaut, le schéma est créé automatiquement au démarrage (action create). Cette configuration doit être vérifiée pour les environnements de production.
- Sécurité : Ne pas exposer les identifiants ou la clé JWT secrète dans un environnement public.
