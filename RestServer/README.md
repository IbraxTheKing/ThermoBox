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
| `POST` | `/type/{type}` | Ajoute une mesure (`mesurer`) ou une consigne (`consigne`) : `{"value": 21.5, "date": "…"}`. | ADMIN |
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
| `GET` | `/{id}/acces` | Accès de l'utilisateur (tout, ou seulement certains éléments). | ADMIN, Owner |
| `PUT` | `/{id}/acces` | Remplace les accès (`{"restricted": true, "elements": [ids]}`). | ADMIN |

### 🗂 Groupes (`/api/groups`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Liste les groupes avec leurs membres et éléments. | ADMIN |
| `GET` | `/{id}` | Un groupe. | ADMIN |
| `POST` | `/` | Crée un groupe (`{"name", "description", "members": [ids], "elements": [ids]}`). | ADMIN |
| `PUT` | `/{id}` | Remplace un groupe. | ADMIN |
| `DELETE` | `/{id}` | Supprime un groupe (ses membres gardent leurs propres accès). | ADMIN |

### 🌳 Hiérarchie (`/api/hierarchie`)
*Bâtiment → (Étage) → Salle → Boîtier, tel que l'utilisateur connecté a le droit de le voir. Chaque élément porte son type (`BATIMENT`, `ETAGE`, `SALLE`, `BOITIER`) ; les dates sont des chaînes ISO-8601 (`2026-10-21T14:05:00`).*

| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/arbre` | Tous les éléments visibles : parent, température (moyenne des boîtiers), consigne (héritée ou non), photo, favori. Les boîtiers seulement pour GESTIONNAIRE et ADMIN. | Authentifié |
| `GET` | `/elements/{id}` | Un élément et ce que sa page affiche : ancêtres, enfants, mesure de chaque boîtier, salles contenues, consignes propres en dessous. | Authentifié |
| `POST` | `/elements` | Crée un élément (`{"kind": "SALLE", "name": "…", "parentId": 4}`). | ADMIN |
| `PUT` | `/elements/{id}` | Renomme et déplace (`{"name": "…", "parentId": 4}`, `null` pour détacher). | ADMIN |
| `PUT` | `/elements/{id}/parent` | Déplace seulement (`{"parentId": 4}` ou `null`). | ADMIN |

La suppression d'un élément reste `DELETE /api/salles/{id}`.

### 🌡 Climat (`/api/climat`)
*Températures à n'importe quel niveau : un étage ou un bâtiment agrège les boîtiers qu'il contient. Périodes en instants ISO-8601 : `?start=2026-10-20T08:00:00Z&end=2026-10-21T08:00:00Z`.*

| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/{id}/historique?start=…&end=…` | Courbes de la période : une par boîtier (salle), une par salle (étage, bâtiment), la moyenne et les consignes. Un VIEWER n'a que la moyenne. | Authentifié |
| `GET` | `/moyennes?ids=3,7&start=…&end=…` | Courbe moyenne de plusieurs éléments à la fois (`{"3": [points], "7": [points]}`). | Authentifié |
| `PUT` | `/{id}/consigne` | Enregistre la consigne d'un bâtiment, d'un étage ou d'une salle et l'envoie à ses boîtiers (`{"value": 21.5, "force": false}`) ; ceux qui ont leur propre consigne la gardent, sauf `force`. | ADMIN, GESTIONNAIRE |

### ⭐ Favoris (`/api/favoris`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Ids des salles épinglées par l'utilisateur. | Authentifié |
| `PUT` | `/{id}` | Épingle une salle. | Authentifié |
| `DELETE` | `/{id}` | Retire une salle des favoris. | Authentifié |

### 🖼 Photos (`/api/salles/{id}/image`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/salles/{id}/image` | La photo (JPEG), avec `ETag`. | Authentifié |
| `PUT` | `/api/salles/{id}/image` | Remplace la photo : le corps est l'image (JPEG, PNG, GIF ou BMP, 10 Mo max). | ADMIN |
| `DELETE` | `/api/salles/{id}/image` | Retire la photo. | ADMIN |

### 🎨 Réglages (`/api/reglages`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Réglages d'affichage communs à tous (`{"style": "verre"}` ou `"panneau"`). | Authentifié |
| `PUT` | `/` | Change le style pour tout le monde. | ADMIN |


## 🚀 Exemple de requête (Envoi de consigne)
Pour envoyer une nouvelle température cible à la salle ID `5` :

```bash
curl -X PUT "http://localhost:8080/api/salletemps/room/5/consigne?value=21.5" \
     -H "Authorization: Bearer <votre_token>" \
     -H "Content-Type: application/json"
```
