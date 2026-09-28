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
