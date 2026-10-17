# RestServer Module 🌐

Le module `RestServer` expose les fonctionnalités de ThermoBox via une API RESTful sécurisée, construite avec **Jersey** et tournant sur un serveur **Jetty**.

## 🛠 Fonctionnalités principales
- **API Endpoints** : Ressources pour la gestion des salles, des températures et des utilisateurs (ex: `/api/salletemps`).
- **Sécurité JWT** : Gestion complète de l'authentification par jetons JSON Web Tokens.
- **Contrôle d'accès granulaire** : Gestion des rôles (`ADMIN`, `GESTIONNAIRE`, `VIEWER`) et permissions basées sur la propriété des ressources (Owner).
- **Intégration Matérielle** : Capacité à envoyer des consignes directement aux boîtiers de capteurs via le pilote de communication.

## 📖 Documentation de l'API

Toutes les routes sont préfixées par `/api`. Les requêtes nécessitent généralement un header `Authorization: Bearer <token>`, sauf pour les points d'entrée publics.

### 🔐 Authentification (`/api/auth`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `POST` | `/login` | Échange des identifiants contre un JWT. | Public |
| `POST` | `/logout` | Révoque le jeton actuel. | Authentifié |
| `GET` | `/me` | Récupère les informations de l'utilisateur connecté. | Authentifié |

### 🏢 Salles (`/api/salles`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Liste toutes les salles. | Authentifié |
| `GET` | `/{id}` | Récupère une salle par son ID. | Authentifié |
| `GET` | `/name/{name}` | Récupère une salle par son nom. | Authentifié |
| `POST` | `/` | Ajoute une nouvelle salle. | ADMIN |
| `PUT` | `/{id}` | Met à jour une salle existante. | ADMIN |
| `DELETE` | `/{id}` | Supprime une salle. | ADMIN |

### 🌡 Supervision Thermique (`/api/salletemps`)
*C'est le cœur du système de supervision.*

| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Liste toutes les associations salle-température. | Authentifié |
| `GET` | `/{id}` | Récupère une association par son ID. | Authentifié |
| `GET` | `/room/{id}` | Liste les capteurs d'une salle spécifique. | Authentifié |
| `GET` | `/room/{id}/current` | Températures actuelles d'une salle. | Authentifié |
| `GET` | `/room/{id}/consigne` | Consigne actuelle d'une salle. | Authentifié |
| `GET` | `/room/{id}/temperatures?start=...&end=...\` | Historique des températures sur une période. | Authentifié |
| `GET` | `/room/{id}/consignes?start=...&end=...\` | Historique des consignes sur une période. | Authentifié |
| `GET` | `/room/{id}/average?start=...&end=...\` | Moyenne des températures sur une période. | Authentifié |
| `POST` | `/` | Ajoute une nouvelle association (capteur). | ADMIN, GESTIONNAIRE |
| `PUT` | `/{id}` | Met à jour une association existante. | ADMIN, GESTIONNAIRE |
| `DELETE` | `/{id}` | Supprime une association. | ADMIN |
| `PUT` | `/room/{id}/consigne?value=...\` | **Envoie une nouvelle consigne** à la salle (déclenche le pilote matériel). | ADMIN, GESTIONNAIRE |
| `GET` | `/room/{id}/measured/latest` | Récupère la dernière mesure réelle. | Authentifié |

### 📊 Températures (`/api/temperatures`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Liste toutes les mesures (consignes et réelles). | ADMIN, GESTIONNAIRE, VIEWER |
| `GET` | `/{id}` | Récupère une mesure par son ID. | ADMIN, GESTIONNAIRE, VIEWER |
| `GET` | `/type/{type}` | Filtre par type (`consigne` ou `mesurer`). | ADMIN, GESTIONNAIRE, VIEWER |
| `GET` | `/average/{type}` | Moyenne pour un type spécifique. | ADMIN, GESTIONNAIRE, VIEWER |
| `POST` | `/` | Ajoute une mesure manuellement. | ADMIN |
| `PUT` | `/{id}` | Met à jour une mesure existante. | ADMIN |
| `DELETE` | `/{id}` | Supprime une mesure. | ADMIN |

### 👥 Utilisateurs (`/api/users`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/{id}` | Récupère un utilisateur par son ID. | ADMIN, Owner |
| `GET` | `/` | Liste tous les utilisateurs. | ADMIN |
| `POST` | `/` | Inscription d'un nouvel utilisateur (par défaut : VIEWER). | Public |
| `PUT` | `/{id}` | Met à jour un profil utilisateur. | ADMIN, Owner |
| `DELETE` | `/{id}` | Supprime un utilisateur. | ADMIN, Owner |
| `GET` | `/username/{username}` | Recherche un utilisateur par son nom d'utilisateur. | ADMIN |

## 🚀 Exemple de requête (Envoi de consigne)
Pour envoyer une nouvelle température cible à la salle ID `5` :

```bash
curl -X PUT "http://localhost:8080/api/salletemps/room/5/consigne?value=21.5" \
     -H "Authorization: Bearer <votre_token>" \
     -H "Content-Type: application/json"
```
