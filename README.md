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

### Interface web JSF

Le module `JSF` produit une archive WAR nommée `thermobox.war`.

Après compilation, déployez cette archive dans un conteneur compatible Jakarta Faces et Jakarta Servlet, par exemple Tomcat 10.1. Le point d'entrée de l'interface est `index.xhtml`.

## Déploiement

Le profil Maven `deploy` produit les trois livrables dans le dossier `deploy/` à la racine :

```bash
mvn -Pdeploy -DskipTests clean package
```

| Fichier | Contenu |
|---|---|
| `base.jar` | `ibr-utils`, `Common`, `Persistence` et `Business` réunis (module `Base`), avec `persistence.xml` |
| `RestServer.jar` | Le serveur REST, exécutable, avec Jersey, Jetty et les bibliothèques tierces. Il charge `base.jar` posé à côté de lui |
| `JsfServer.war` | L'interface JSF, avec `base.jar` dans `WEB-INF/lib` à la place des quatre modules séparés |

- API REST : placer `RestServer.jar` et `base.jar` dans le même dossier, puis lancer `java -jar RestServer.jar` avec les variables d'environnement ci-dessus.
- Interface JSF : copier `JsfServer.war` dans le dossier `webapps` de Tomcat. L'application répond alors sur `/JsfServer` (renommer le fichier pour changer ce chemin).

Sans `-Pdeploy`, le build reste celui du développement (`thermobox.war`, jars par module).

## Rôles et sécurité

Les autorisations sont vérifiées côté serveur, dans l'API comme dans l'interface JSF. Côté API, les annotations des ressources définissent les rôles autorisés pour les opérations protégées.

- `ADMIN` : opérations d'administration, dont certaines suppressions.
- `GESTIONNAIRE` : gestion des données et des consignes selon les routes.
- `VIEWER` : consultation lorsque les droits de la route le permettent.

Un jeton JWT doit être transmis dans l'en-tête `Authorization` avec le préfixe `Bearer`. Une clé stable configurée via `THERMOBOX_JWT_SECRET` est nécessaire si les jetons doivent rester valides après redémarrage.

L'interface JSF utilise les mêmes jetons (même `JwtService`, dans `Business`), transmis dans un cookie `HttpOnly` et `SameSite=Strict`. Chaque page de `/app` et `/admin` vérifie le jeton et relit l'utilisateur associé.

## Bâtiments, étages, salles et boîtiers

Les quatre héritent de `Salle` et sont reliés par `SalleSalleAttr` (parent → enfant) : un bâtiment contient des étages ou directement des salles, un étage des salles, une salle des boîtiers, et chaque élément n'a qu'un parent direct.

Dans l'interface JSF (« Bâtiments & salles »), un administrateur range les éléments par glisser-déposer : le serveur vérifie ces règles avant que l'arbre ne bouge à l'écran.

## Tableau de bord

Les salles y sont regroupées par bâtiment, avec une recherche (nom de salle, d'étage ou de bâtiment, sans tenir compte des accents) et un filtre par bâtiment. Chaque utilisateur peut épingler des salles en favoris : ils sont enregistrés en base (table `favori`) et le suivent donc d'un appareil à l'autre. Un clic n'importe où sur une carte ouvre le détail de la salle.

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
