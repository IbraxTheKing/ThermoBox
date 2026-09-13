# ThermoBox

Un système complet de gestion et de suivi des températures dans des salles. Le projet combine une API REST en Kotlin avec une couche de persistance JPA et une interface web de visualisation.

## 🎯 Objectif

ThermoBox est conçu comme un **projet d'étude** pour explorer :
- L'architecture multi-modules avec Maven
- La conception d'une API REST sécurisée (Jakarta/Quarkus stack)
- La gestion d'entités avec polymorphisme JPA
- Le système de rôles et d'authentification
- L'intégration MQTT pour la réception de données
- Une interface web réactive pour la supervision

## 📊 Ce que ça fait

### Côté serveur (API REST)
- Reçoit des **mesures de température** depuis des capteurs (potentiellement via MQTT)
- Stocke les données avec horodatage
- Gère les **consignes** (consignes de chauffage/climatisation) par salle
- Expose une API REST pour consulter l'historique sur différentes périodes
- Supporte l'authentification et les rôles (ADMIN, GESTIONNAIRE, VIEWER)

### Côté client (Web)
- Interface de supervision moderne avec graphiques
- Affichage de la température mesurée et de la consigne actuelle
- Visualisation de l'historique (1h, 24h, 7 jours)
- Contrôle des consignes via slider/input numérique
- Liste des salles avec aperçu temps réel

## 🏗️ Architecture

Le projet est organisé en modules Maven. Chaque module a une responsabilité claire :

```
ThermoBox/
├── ibr-utils/           # Utilitaires réutilisables
│   ├── Annotations de sécurité (@RequiresAuth, @RequiresRole)
│   ├── Classes d'exception personnalisées
│   └── Interfaces de services CRUD
│
├── Common/              # Modèle de données commun
│   ├── Entités JPA (User, Salle, Temperature, Mesurer, Consigne, SalleTempAttr)
│   ├── Énumérations (UserType)
│   └── Interfaces de services
│
├── Business/            # Logique métier
│   ├── Implémentations des services
│   ├── Drivers de protocole (MQTT, simulation)
│   └── Factory pour créer les services
│
├── Persistence/         # Accès aux données
│   ├── Implémentations JPA des services de persistance
│   ├── Requêtes HQL personnalisées
│   └── Factory pour créer les DAOs
│
├── Io/                  # Entrées/sorties (À développer)
│
├── RestServer/          # API REST
│   ├── Ressources JAX-RS (@Path)
│   ├── Filtre de sécurité (SecurityFilter)
│   └── Configuration serveur
│
└── iaSlopTest/          # Client web
    └── Interface HTML/CSS/JS avec Chart.js
```

### Flux de données

```
Capteurs MQTT
    ↓
Business Layer (MQTT Driver)
    ↓
Persistence (JPA)
    ↓
REST API (JSON)
    ↓
Web Client (Chart.js)
```

## 🔐 Système d'authentification et de rôles

Trois rôles existent :

| Rôle | Permissions |
|------|-----------|
| **ADMIN** | Accès total (création/modification/suppression de ressources) |
| **GESTIONNAIRE** | Peut consulter et modifier les consignes |
| **VIEWER** | Lecture seule |

Les endpoints sont protégés via deux annotations :

```kotlin
@RequiresAuth(roles = ["ADMIN", "GESTIONNAIRE"])  // Doit avoir au moins un rôle
fun updateConsigne() { ... }

@RequiresRole("ADMIN")  // Doit avoir exactement ce rôle
fun deleteUser() { ... }
```

Le filtre de sécurité `SecurityFilter` intercepte toutes les requêtes et valide les permissions.

## 🌡️ Modèle de données

### Entités principales

**User**
```kotlin
@Entity
class User {
    var id: Long?
    var username: String?
    var password: String?        // TODO: chiffrer
    var type: UserType?         // ADMIN, GESTIONNAIRE, VIEWER
}
```

**Salle**
Représente une pièce ou une zone à superviser.

**Temperature** (classe parent avec polymorphisme)
```kotlin
@Entity
@DiscriminatorColumn
abstract class Temperature {
    var id: Long?
    var value: Float
    var date: LocalDateTime
}

// Sous-classes :
@Entity
@DiscriminatorValue("Mesure")
class Mesurer(value: Float, date: LocalDateTime) : Temperature

@Entity
@DiscriminatorValue("Consigne")
class Consigne(value: Float, date: LocalDateTime) : Temperature
```

Le polymorphisme JPA permet de stocker deux types de données dans une même table en les différenciant par une colonne discriminante.

**SalleTempAttr**
Table de liaison qui associe une température (mesure ou consigne) à une salle.

## 📡 API REST

L'API est accessible via les endpoints suivants (préfixe `/api`) :

### Salles
```
GET    /salles              → Liste toutes les salles
GET    /salles/{id}         → Détail d'une salle
GET    /salles/name/{name}  → Cherche une salle par nom
POST   /salles              → Crée une salle (ADMIN)
PUT    /salles/{id}         → Modifie une salle (ADMIN)
DELETE /salles/{id}         → Supprime une salle (ADMIN)
```

### Températures
```
GET    /temperatures              → Liste toutes les températures
GET    /temperatures/{id}         → Détail d'une température
GET    /temperatures/type/{type}  → Mesures ou consignes (type: "mesurer" ou "consigne")
GET    /temperatures/average/{type} → Moyenne des températures
POST   /temperatures              → Crée une température (ADMIN)
PUT    /temperatures/{id}         → Modifie une température (ADMIN)
DELETE /temperatures/{id}         → Supprime une température (ADMIN)
```

### Salles - Historique et consignes
```
GET    /salletemps/room/{id}/current         → Dernières mesures + consigne
GET    /salletemps/room/{id}/consigne        → Dernière consigne
GET    /salletemps/room/{id}/temperatures    → Mesures sur période
                                               ?start=2026-09-13T00:00:00Z
                                               &end=2026-09-13T23:59:59Z
GET    /salletemps/room/{id}/consignes       → Consignes sur période
PUT    /salletemps/room/{id}/consigne?value={value} → Met à jour la consigne
```

## 🚀 Configuration et lancement

### Prérequis
- Java 23+
- Maven 3.8+
- (Optionnel) Base de données configurée via `persistence.xml`

### Compiler
```bash
mvn clean package
```

### Lancer le serveur REST
```bash
cd RestServer
mvn exec:java  # Si configuré avec exec-maven-plugin
```

Par défaut, l'API tourne sur `http://localhost:8080/api`

### Ouvrir le client web
Ouvre `iaSlopTest/thermobox-client (3).html` dans le navigateur et entre l'URL de l'API.

## 💡 Architecture technique détaillée

### Couche Business

Le pattern Factory (`BusinessFactory`) crée les services métier et délègue à la couche Persistence :

```kotlin
class SalleServiceImpl : SalleService {
    private val salleService = PersistenceFactory().getSalleDataService()
    
    override fun getByName(name: String): Salle? 
        = salleService.getByName(name)
}
```

Les services métier **décorrent** les implémentations JPA. C'est simple mais maintient une séparation.

### MQTT et ProtocolDriver

Une interface générique `ProtocolDriver` permet de brancher différentes sources :

```kotlin
interface ProtocolDriver {
    fun receiveTemperature(roomId: Long, value: Float)
    fun sendConsigne(roomId: Long, value: Float)
}

class MqttSendReceiver : ProtocolDriver { ... }
class SimulatedProtocolDriver : ProtocolDriver { ... }  // Pour les tests
```

### Persistance JPA

Les entités sont mappées via `persistence.xml`. Chaque service JPA hérite d'une interface CRUD générique :

```kotlin
interface SalleDataService : CrudService<Salle> {
    fun getByName(name: String): Salle?
}

class SalleDataServiceJPAImpl(
    pu: String,
    em: EntityManager,
    entityClass: Class<Salle>
) : SalleDataService
```

## 📋 À développer / Améliorations futures

- [ ] Implémentation JDBC comme alternative à JPA
- [ ] Authentification JWT réelle (actuellement le `SecurityFilter` ne retourne que `null`)
- [ ] Chiffrement des mots de passe (actuellement stockés en clair)
- [ ] Tests unitaires pour le module Business (dossier vide)
- [ ] Intégration MQTT complète et testée
- [ ] WebSocket pour les mises à jour temps réel
- [ ] Pagination et filtres avancés dans les endpoints
- [ ] Validation des données d'entrée

## 🛠️ Contribuer

Quelques points si tu veux développer le projet :

1. **Branches** : Crée une branche pour chaque feature
2. **Code** : Respecte le style Kotlin du projet (inspection IntelliJ)
3. **Tests** : Ajoute des tests dans le module Business (JUnit 5 + Mockito/MockK)
4. **Documentation** : Documente les classes public avec KDoc
5. **Commits** : Messages clairs et concis

## 📦 Dépendances principales

- **Kotlin 2.4.10** : Langage principal
- **Jakarta Persistence 3.2** : ORM JPA
- **Hibernate** (implicite via JPA)
- **Jakarta WS-RS 4.0** (JAX-RS moderne) : REST API
- **Eclipse Paho MQTT 1.2.5** : Client MQTT
- **JUnit 5.13** : Tests unitaires
- **Mockito / MockK** : Mocking pour tests
- **Chart.js 4.4** : Graphiques côté client

## 📄 Licence

MIT

---

## Notes perso

- Le projet est jeune (6 jours) et en cours d'itération
- La structure multi-modules est un bon exercice, même si pour une petite app on pourrait tout fusionner
- Le système de rôles est basique mais extensible

