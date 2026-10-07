# ThermoBox

ThermoBox est une application de supervision de température par salle. Le projet combine une API REST backend en Kotlin/JPA, une couche métier orientée services, et un front web pour visualiser les mesures, les consignes et l'historique des salles.

## Objectif

Le système permet de :
- mesurer la température d'une salle,
- enregistrer les historiques de température,
- définir une consigne de température,
- consulter les données via une interface web ou une API REST,
- gérer les accès selon plusieurs rôles utilisateurs.

## Fonctionnalités

- Collecte et stockage des mesures de température
- Gestion des consignes par salle
- Visualisation des données sur des périodes (heure, journée, semaine)
- API REST exposée pour les clients et le front office
- Authentification et rôles : ADMIN, GESTIONNAIRE, VIEWER
- Architecture multi-modules Maven
- Support MQTT pour l'intégration de capteurs / box connectées

## Architecture

Le projet est organisé en modules Maven :

- `Business` : logique métier et services applicatifs
- `Common` : modèles, entités JPA et interfaces partagées
- `Persistence` : accès aux données via JPA
- `RestServer` : API REST
- `JSF` : interface web JavaServer Faces / PrimeFaces
- `ibr-utils` : utilitaires et composants transverses

## Stack technique

- Kotlin
- Java / Jakarta EE
- JPA / Hibernate
- Maven
- API REST (JAX-RS)
- MQTT
- HTML / CSS / JavaScript
- JSF / PrimeFaces (selon partie front)

## Flux principal

Capteur/MQTT -> Business -> Persistence -> API REST -> Interface utilisateur

## Configuration

Variables d'environnement :

| Variable | Obligatoire | Description |
|---|---|---|
| `THERMOBOX_DB_URL` | non | URL JDBC (défaut : `jdbc:mysql://localhost:3306/thermobox`) |
| `THERMOBOX_DB_USER` | oui | Utilisateur de la base |
| `THERMOBOX_DB_PASSWORD` | oui | Mot de passe de la base |
| `THERMOBOX_JWT_SECRET` | recommandé | Clé de signature JWT (32 octets min.). Sans elle, clé aléatoire : les tokens sont invalidés à chaque redémarrage |
| `THERMOBOX_JWT_TTL_MINUTES` | non | Durée de vie d'un token (défaut : 60) |

## Rôles

- ADMIN : gestion complète
- GESTIONNAIRE : gestion des consignes et consultation
- VIEWER : consultation seule

## Notes

Ce projet est surtout conçu comme un projet de démonstration / étude autour de l'architecture logicielle, de la persistance JPA, de l'API REST et de l'instrumentation de salles par température.

## Licence

MIT
