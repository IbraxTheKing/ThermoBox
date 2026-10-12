# ThermoBox

ThermoBox est un projet de supervision thermique permettant d'organiser des salles, de consulter leurs températures et de gérer des consignes. Il comprend une API REST sécurisée par JWT, un backend métier en Kotlin, une persistance JPA et une interface web JSF/PrimeFaces.

État du projet : projet en cours de développement. Certaines parties, notamment les associations entre salles, étages, bâtiments et leurs services de persistance, évoluent encore.

## Fonctionnalités

- Gestion des salles et des attributs associés.
- Enregistrement et consultation des températures et des consignes.
- Consultation de l'historique des mesures sur une période donnée.
- API REST JSON pour accéder aux données et envoyer des consignes.
- Authentification par jetons JWT et contrôle des droits selon les rôles.
- Pilote de communication simulé et intégration MQTT dans la couche métier.
- Interface web construite avec Jakarta Faces (JSF) et PrimeFaces.

## Architecture

Le dépôt est un projet Maven multi-modules écrit principalement en Kotlin.

| Module | Rôle |
|---|---|
| Common | Entités, modèles et interfaces partagés |
| Business | Services métier, validation et accès au pilote de communication |
| Persistence | Accès aux données avec JPA |
| RestServer | API REST avec Jersey, serveur Jetty et sécurité JWT |
| JSF | Interface web Jakarta Faces / PrimeFaces |
| ibr-utils | Utilitaires et annotations partagés |

Les fabriques `BusinessFactory` et `PersistenceFactory` centralisent respectivement les services métier et les services de persistance.

## Prérequis

- Git
- JDK 23
- Maven
- MySQL, avec une base de données accessible par l'application
- Pour l'interface JSF : un conteneur compatible, par exemple Tomcat 10.1 avec les dépendances nécessaires

## Installation

Récupérez le dépôt :

```bash
git clone https://github.com/IbraxTheKing/ThermoBox.git
cd ThermoBox
```

Configurez les variables d'environnement décrites ci-dessous, puis compilez le projet :

```bash
mvn clean install
```

Cette commande compile les modules et exécute les tests configurés. Elle ne démarre pas automatiquement l'API ni l'interface web.

## Configuration

### Variables d'environnement

| Variable | Obligatoire | Description |
|---|---|---|
| `THERMOBOX_DB_URL` | Non | URL JDBC (défaut : `jdbc:mysql://localhost:3306/thermobox`) |
| `THERMOBOX_DB_USER` | Oui | Utilisateur de la base |
| `THERMOBOX_DB_PASSWORD` | Oui | Mot de passe de la base |
| `THERMOBOX_JWT_SECRET` | Recommandé | Clé de signature JWT d'au moins 32 octets. Si elle est absente, une clé aléatoire est générée au démarrage et les jetons ne survivent pas au redémarrage |
| `THERMOBOX_JWT_TTL_MINUTES` | Non | Durée de vie d'un jeton JWT en minutes (défaut : 60) |

Exemple de configuration sous Linux/macOS :

```bash
export THERMOBOX_DB_URL='jdbc:mysql://localhost:3306/thermobox'
export THERMOBOX_DB_USER='votre_utilisateur'
export THERMOBOX_DB_PASSWORD='votre_mot_de_passe'
export THERMOBOX_JWT_SECRET='remplacer-par-une-cle-secrete-de-32-octets-minimum'
export THERMOBOX_JWT_TTL_MINUTES='60'
```

Adaptez les valeurs à votre environnement. Ne publiez jamais vos vrais identifiants ni votre clé JWT dans le dépôt.

Attention à la base de données : le fichier `Persistence/src/main/resources/META-INF/persistence.xml` configure actuellement la génération du schéma avec l'action `create`. Vérifiez ce réglage avant un environnement partagé ou de production.

## Démarrage

### API REST

Après avoir configuré les variables et compilé le projet, lancez la classe principale `ax.ibr.thermobox.restserver.MainKt` du module `RestServer` depuis votre IDE, avec le classpath Maven du module.

Le serveur écoute par défaut sur :

```text
http://localhost:8080/
```

Les ressources REST sont déclarées sous le préfixe `/api`. Par exemple, les ressources associant températures et salles utilisent `/api/salletemps`.

La plupart des routes nécessitent un jeton JWT transmis dans l'en-tête HTTP suivant :

```http
Authorization: Bearer <votre_jeton>
```

Les routes publiques, notamment celles d'authentification, font exception. Consultez les classes de ressources du module `RestServer` pour connaître précisément les routes, paramètres et méthodes disponibles.

### Interface

Thème « verre bleu » : surfaces translucides et teintées sur un fond bleu brumeux, en mode clair et en mode sombre. Le bouton lune / soleil change de mode ; le choix est gardé dans un cookie `thermobox-theme`, et tant qu'il n'a pas servi, la page suit le réglage du système. PrimeFaces utilise `saga` en mode clair et `vela` en mode sombre. La police Manrope est servie par l'application (`resources/fonts`, licence OFL).

## Photos des salles

Chaque élément (salle, étage, bâtiment) peut avoir une photo, ajoutée ou retirée par un administrateur depuis sa page. Elle est enregistrée dans la table `salle_image`, à part de `salle`, pour que charger les salles ne charge jamais les images. Le fichier envoyé (10 Mo au plus) est décodé, redressé selon son orientation EXIF, réduit à 1600 px et réenregistré en JPEG : un fichier qui n'est pas une image est refusé, et les métadonnées (position GPS d'une photo de téléphone…) disparaissent. Les photos sont servies sur `/media/salle/{id}`, aux seuls utilisateurs connectés.

## Données de démonstration

- `DatabaseSeeder` (`Business/src/test/kotlin/DBPopulate.kt`) crée 3 bâtiments, leurs étages et salles, 27 boîtiers (dont 2 non rattachés), un utilisateur par rôle (`admin`, `gestionnaire`, `viewer`, mot de passe `password123`) et 3 jours d'historique. Il se lance avec les mêmes variables d'environnement que l'application, et peut être relancé sans doublon.
- `SimulatedProtocolDriver` mesure ensuite tous les boîtiers chaque minute : chaque boîtier suit la consigne de sa salle, et de nouveaux boîtiers non rattachés s'annoncent toutes les deux minutes.

## Développement

Les derniers changements portent notamment sur :

- la centralisation des fabriques métier et de persistance ;
- la sécurisation de l'API REST par JWT ;
- la configuration SQL via des variables d'environnement ;
- l'ajout des entités `Etage` et `Batiment`, qui héritent de `Salle` ;
- l'ajout de `SalleSalleAttr` et le renforcement des validations métier ;
- l'implémentation progressive des services JPA.

La persistance des nouvelles associations doit encore être vérifiée et complétée.

Pour suivre les changements et consulter les commits :

- https://github.com/IbraxTheKing/ThermoBox/commits

## Licence

MIT
