# RestServer Module 🌐

Le module `RestServer` expose les fonctionnalités de ThermoBox via une API RESTful sécurisée, construite avec **Jersey** et tournant sur un serveur **Jetty**.

## 🛠 Fonctionnalités principales
- **API Endpoints** : Ressources pour la gestion des salles, des températures et des utilisateurs (ex: `/api/v2/roomtemps`).
- **Sécurité JWT** : Gestion complète de l'authentification par jetons JSON Web Tokens.
- **Contrôle d'accès granulaire** : Gestion des rôles (`ADMIN`, `MANAGER`, `VIEWER`) et permissions basées sur la propriété des ressources (Owner).
- **Intégration Matérielle** : Capacité à envoyer des consignes directement aux boîtiers de capteurs via le pilote de communication.

## 📖 Documentation de l'API

L'API principale est en anglais et préfixée par `/api/v2`. Les requêtes nécessitent généralement un header `Authorization: Bearer <token>`, sauf pour les points d'entrée publics.

L'ancienne API (chemins `/api/...` sans `v2`, noms français) reste disponible pour les clients existants : voir [Ancienne API](#-ancienne-api-api-dépréciée).

### 🔐 Authentification (`/api/v2/auth`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `POST` | `/login` | Échange des identifiants contre un JWT. | Public |
| `POST` | `/logout` | Révoque le jeton actuel. | Authentifié |
| `GET` | `/me` | Récupère les informations de l'utilisateur connecté. | Authentifié |

### 🏢 Salles (`/api/v2/rooms`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Liste toutes les salles. | Authentifié |
| `GET` | `/{id}` | Récupère une salle par son ID. | Authentifié |
| `GET` | `/name/{name}` | Récupère une salle par son nom. | Authentifié |
| `POST` | `/` | Ajoute une nouvelle salle. | ADMIN |
| `PUT` | `/{id}` | Met à jour une salle existante. | ADMIN |
| `DELETE` | `/{id}` | Supprime une salle. | ADMIN |

### 🌡 Supervision Thermique (`/api/v2/roomtemps`)
*C'est le cœur du système de supervision.*

| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Liste toutes les associations salle-température. | Authentifié |
| `GET` | `/{id}` | Récupère une association par son ID. | Authentifié |
| `GET` | `/room/{id}` | Liste les capteurs d'une salle spécifique. | Authentifié |
| `GET` | `/room/{id}/current` | Températures actuelles d'une salle. | Authentifié |
| `GET` | `/room/{id}/setpoint` | Consigne actuelle d'une salle. | Authentifié |
| `GET` | `/room/{id}/temperatures?start=...&end=...` | Historique des températures sur une période. | Authentifié |
| `GET` | `/room/{id}/setpoints?start=...&end=...` | Historique des consignes sur une période. | Authentifié |
| `GET` | `/room/{id}/average?start=...&end=...` | Moyenne des températures sur une période. | Authentifié |
| `POST` | `/` | Ajoute une nouvelle association (capteur). | ADMIN, MANAGER |
| `PUT` | `/{id}` | Met à jour une association existante. | ADMIN, MANAGER |
| `DELETE` | `/{id}` | Supprime une association. | ADMIN |
| `PUT` | `/room/{id}/setpoint?value=...` | **Envoie une nouvelle consigne** à la salle (déclenche le pilote matériel). | ADMIN, MANAGER |
| `GET` | `/room/{id}/measured/latest` | Récupère la dernière mesure réelle. | Authentifié |

### 📊 Températures (`/api/v2/temperatures`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Liste toutes les mesures (consignes et réelles). | ADMIN, MANAGER, VIEWER |
| `GET` | `/{id}` | Récupère une mesure par son ID. | ADMIN, MANAGER, VIEWER |
| `GET` | `/type/{type}` | Filtre par type (`setpoint` ou `measurement`). | ADMIN, MANAGER, VIEWER |
| `GET` | `/average/{type}` | Moyenne pour un type spécifique. | ADMIN, MANAGER, VIEWER |
| `POST` | `/` | Ajoute une mesure manuellement. | ADMIN |
| `POST` | `/type/{type}` | Ajoute une mesure (`measurement`) ou une consigne (`setpoint`) : `{"value": 21.5, "date": "…"}`. | ADMIN |
| `PUT` | `/{id}` | Met à jour une mesure existante. | ADMIN |
| `DELETE` | `/{id}` | Supprime une mesure. | ADMIN |

### 👥 Utilisateurs (`/api/v2/users`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/{id}` | Récupère un utilisateur par son ID. | ADMIN, Owner |
| `GET` | `/` | Liste tous les utilisateurs. | ADMIN |
| `POST` | `/` | Inscription d'un nouvel utilisateur (par défaut : VIEWER). | Public |
| `PUT` | `/{id}` | Met à jour un profil utilisateur. | ADMIN, Owner |
| `DELETE` | `/{id}` | Supprime un utilisateur. | ADMIN, Owner |
| `GET` | `/username/{username}` | Recherche un utilisateur par son nom d'utilisateur. | ADMIN |
| `GET` | `/{id}/access` | Accès de l'utilisateur (tout, ou seulement certains éléments). | ADMIN, Owner |
| `PUT` | `/{id}/access` | Remplace les accès (`{"restricted": true, "elements": [ids]}`). | ADMIN |

### 🗂 Groupes (`/api/v2/groups`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Liste les groupes avec leurs membres et éléments. | ADMIN |
| `GET` | `/{id}` | Un groupe. | ADMIN |
| `POST` | `/` | Crée un groupe (`{"name", "description", "members": [ids], "elements": [ids]}`). | ADMIN |
| `PUT` | `/{id}` | Remplace un groupe. | ADMIN |
| `DELETE` | `/{id}` | Supprime un groupe (ses membres gardent leurs propres accès). | ADMIN |

### 🌳 Hiérarchie (`/api/v2/hierarchy`)
*Bâtiment → (Étage) → Salle → Boîtier, tel que l'utilisateur connecté a le droit de le voir. Chaque élément porte son type (`BUILDING`, `FLOOR`, `ROOM`, `DEVICE`) ; les dates sont des chaînes ISO-8601 (`2026-10-21T14:05:00`).*

| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/tree` | Tous les éléments visibles : parent, température (moyenne des boîtiers), consigne (héritée ou non), photo, favori. Les boîtiers seulement pour MANAGER et ADMIN. | Authentifié |
| `GET` | `/elements/{id}` | Un élément et ce que sa page affiche : ancêtres, enfants, mesure de chaque boîtier, salles contenues, consignes propres en dessous. | Authentifié |
| `POST` | `/elements` | Crée un élément (`{"kind": "ROOM", "name": "…", "parentId": 4}`). | ADMIN |
| `PUT` | `/elements/{id}` | Renomme et déplace (`{"name": "…", "parentId": 4}`, `null` pour détacher). | ADMIN |
| `PUT` | `/elements/{id}/parent` | Déplace seulement (`{"parentId": 4}` ou `null`). | ADMIN |

La suppression d'un élément reste `DELETE /api/v2/rooms/{id}`.

### 🌡 Climat (`/api/v2/climate`)
*Températures à n'importe quel niveau : un étage ou un bâtiment agrège les boîtiers qu'il contient. Périodes en instants ISO-8601 : `?start=2026-10-20T08:00:00Z&end=2026-10-21T08:00:00Z`.*

| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/{id}/history?start=…&end=…` | Courbes de la période : une par boîtier (salle), une par salle (étage, bâtiment), la moyenne et les consignes. Un VIEWER n'a que la moyenne. | Authentifié |
| `GET` | `/averages?ids=3,7&start=…&end=…` | Courbe moyenne de plusieurs éléments à la fois (`{"3": [points], "7": [points]}`). | Authentifié |
| `PUT` | `/{id}/setpoint` | Enregistre la consigne d'un bâtiment, d'un étage ou d'une salle et l'envoie à ses boîtiers (`{"value": 21.5, "force": false}`) ; ceux qui ont leur propre consigne la gardent, sauf `force`. | ADMIN, MANAGER |

### ⭐ Favoris (`/api/v2/favorites`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Ids des salles épinglées par l'utilisateur. | Authentifié |
| `PUT` | `/{id}` | Épingle une salle. | Authentifié |
| `DELETE` | `/{id}` | Retire une salle des favoris. | Authentifié |

### 🖼 Photos (`/api/v2/rooms/{id}/image`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v2/rooms/{id}/image` | La photo (JPEG), avec `ETag`. | Authentifié |
| `PUT` | `/api/v2/rooms/{id}/image` | Remplace la photo : le corps est l'image (JPEG, PNG, GIF ou BMP, 10 Mo max). | ADMIN |
| `DELETE` | `/api/v2/rooms/{id}/image` | Retire la photo. | ADMIN |

### 🎨 Réglages (`/api/v2/settings`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Réglages d'affichage communs à tous (`{"style": "glass"}` ou `"panel"`). | Authentifié |
| `PUT` | `/` | Change le style pour tout le monde. | ADMIN |

### 🏷 Serveur (`/api/v2/server`)
| Méthode | Chemin | Description | Accès |
| :--- | :--- | :--- | :--- |
| `GET` | `/` | Nom et version du serveur, adresse et origine du logo (`{"name": "ThermoBox", "version": "…", "logo": "/api/v2/server/logo?v=…", "logoSource": "custom" \| "site" \| "default"}`). | Public |
| `GET` | `/logo` | Le logo du serveur, dans son format. Avec `?size=192`, un PNG d'au plus 192 px quand le logo peut être redimensionné (PNG, JPEG, GIF, logo ThermoBox) ; un logo SVG, ICO ou WebP est servi tel quel. Avec `ETag`. | Public |
| `PUT` | `/logo` | Remplace le logo : le corps est l'image (PNG, JPEG ou GIF, gardée en PNG de 1024 px au plus ; ou SVG sans script), 5 Mo max. | ADMIN |
| `DELETE` | `/logo` | Revient au logo automatique. | ADMIN |

Le logo affiché est, dans l'ordre :
1. celui donné par un administrateur (`PUT /api/v2/server/logo`), gardé dans `branding/` du dossier de données (`THERMOBOX_DATA_DIR`) ;
2. sinon celui du site auquel appartient le serveur : un serveur joint à `thermobox.example.com` reprend le logo de `example.com` (icônes déclarées par sa page d'accueil, la plus grande d'abord, sinon `/favicon.ico`). Le nom vient de la requête (`X-Forwarded-Host` derrière un proxy, sinon `Host`). Rien n'est cherché pour une adresse IP, `localhost` ou un nom local (`.local`, `.lan`…). Seul HTTPS vers des adresses publiques est utilisé ; le logo trouvé est gardé un jour (une heure si rien n'est trouvé) ;
3. sinon le logo de ThermoBox.

### ⬆ Mises à jour (`/api/v2/updates`)
Voir la section « Mises à jour depuis l'interface » du README principal.

## 🕰 Ancienne API (`/api`, dépréciée)

Les chemins de ThermoBox 26.10 restent servis tels quels pour les clients existants (le client Angular actuel, des scripts) : tout chemin sous `/api/` qui n'est pas sous `/api/v2/` est l'ancienne API. Le serveur réécrit la requête vers l'API anglaise et traduit le JSON dans les deux sens (`LegacyApi`, dans `filters`) : un ancien client reçoit les mêmes clés et les mêmes valeurs qu'avant. Seuls les textes libres (messages d'erreur, intitulés des contrôles de mise à jour) sont désormais en anglais.

Les nouveaux clients doivent utiliser `/api/v2`. Correspondances :

| Ancienne API | API v2 |
| :--- | :--- |
| `/api/salles`, `/api/salletemps`, `/api/hierarchie`, `/api/climat`, `/api/favoris`, `/api/reglages` | `/api/v2/rooms`, `/api/v2/roomtemps`, `/api/v2/hierarchy`, `/api/v2/climate`, `/api/v2/favorites`, `/api/v2/settings` |
| `/api/auth`, `/api/users`, `/api/groups`, `/api/temperatures`, `/api/updates` | même chemin sous `/api/v2` |
| `…/arbre`, `…/historique`, `…/moyennes`, `…/consigne`, `…/consignes`, `…/acces` | `…/tree`, `…/history`, `…/averages`, `…/setpoint`, `…/setpoints`, `…/access` |
| `/api/temperatures/type/consigne`, `…/mesurer` | `/api/v2/temperatures/type/setpoint`, `…/measurement` |
| Clés `salle`, `salles`, `consigne`, `consignes`, `consignesPropres`, `boitiers`, `boitiersVisibles`, `noeuds`, `tout`, `rattache`, `derniereMesure`, `favori`, `heritee`, `ancetres`, `enfants`, `mesures`, `moyenne`, `conservees`, `remplacees`, `echecs` | `room`, `rooms`, `setpoint`, `setpoints`, `ownSetpoints`, `devices`, `devicesVisible`, `nodes`, `all`, `attached`, `lastMeasurement`, `favorite`, `inherited`, `ancestors`, `children`, `measurements`, `average`, `kept`, `replaced`, `failures` |
| `kind` : `BATIMENT`, `ETAGE`, `SALLE`, `BOITIER` | `BUILDING`, `FLOOR`, `ROOM`, `DEVICE` |
| `type` d'un utilisateur : `GESTIONNAIRE` | `MANAGER` |
| `type` d'une version : `CORRECTIVE`, `FONCTIONNELLE` (`typeLabel` : `Corrective`, `Fonctionnelle`) | `PATCH`, `FEATURE` (`Patch`, `Feature`) |
| `state` : `EN_ATTENTE`, `EN_COURS`, `IGNOREE`, `ECHEC`, `REDEMARRAGE`, `TERMINEE` | `PENDING`, `RUNNING`, `SKIPPED`, `FAILED`, `RESTARTING`, `DONE` |
| `level` : `AVERTISSEMENT`, `BLOQUANT` | `WARNING`, `BLOCKING` |
| `style` : `verre`, `panneau` | `glass`, `panel` |


## 🚀 Exemple de requête (Envoi de consigne)
Pour envoyer une nouvelle température cible à la salle ID `5` :

```bash
curl -X PUT "http://localhost:8080/api/v2/roomtemps/room/5/setpoint?value=21.5" \
     -H "Authorization: Bearer <votre_token>" \
     -H "Content-Type: application/json"
```
