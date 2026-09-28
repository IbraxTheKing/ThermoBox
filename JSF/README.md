# Thermobox – front JSF (Jakarta Faces 4 + PrimeFaces 14 + Kotlin)

## Pages

| URL | Accès | Bean | Équivalent REST |
|---|---|---|---|
| `/login.xhtml`, `/register.xhtml` | public | `LoginBean`, `RegisterBean` | `GET /users/username/{u}`, `POST /users` |
| `/app/index.xhtml` | connecté | `DashboardBean` | `/salletemps/room/{id}/measured/latest` + `/consigne` |
| `/app/salles.xhtml` | connecté (CRUD : ADMIN) | `SalleBean` | `/salles` |
| `/app/salle.xhtml?id=…` | connecté (consigne : ADMIN/GESTIONNAIRE) | `SalleDetailBean` | `/salletemps/room/{id}/…` |
| `/app/temperatures.xhtml` | connecté (CRUD : ADMIN) | `TemperatureBean` | `/temperatures` |
| `/app/profil.xhtml` | propriétaire | `ProfilBean` | `PUT/DELETE /users/{id}` (allowOwner) |
| `/admin/users.xhtml` | ADMIN | `UserBean` | `/users` |

`AuthFilter` protège `/app/*` (connecté) et `/admin/*` (ADMIN) ; chaque action sensible
revérifie le rôle côté serveur (`loginBean.require(...)`).

## Installation

1. Copier `src/` dans un module **war** qui dépend de tes modules métier.
2. Fusionner `pom-snippet.xml` : le plugin Kotlin **all-open** est obligatoire (proxies CDI).
3. Déployer sur un serveur Jakarta EE 10 (Payara 6, GlassFish 7, WildFly 27+) ou Tomcat 10.1 + Mojarra + Weld.

## ⚠️ Hypothèses à vérifier sur tes entités

Je n'avais que les resources REST, donc les noms suivants sont supposés :

- `Salle()` constructeur sans argument, `var id: Long?`, `var name: String`
- `Temperature` : `var value: Float`, `var date: LocalDateTime` ; `Consigne(value, date)` / `Mesurer(value, date)`
- `User()` sans argument, `var username`, `var password`, `var role` (enum ou String : `ADMIN`, `GESTIONNAIRE`, `USER`)
- **Mot de passe** : `LoginBean.passwordMatches` compare en clair. Si ton `UserService` hashe
  (BCrypt…), remplace par la vérification correspondante.
- L'envoi de consigne est réservé à ADMIN/GESTIONNAIRE (l'annotation est commentée dans la resource REST).
