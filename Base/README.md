# Module Base (déploiement)

`Base` ne contient pas de code. Il sert seulement à fabriquer les fichiers livrés, avec le profil
Maven `deploy` :

```bash
mvn -Pdeploy -DskipTests package
```

Résultat, dans `deploy/` à la racine du dépôt :

| Fichier | Fabriqué par | Contenu |
|---|---|---|
| `base.jar` | `Base` (maven-shade) | Les modules du projet : `ibr-utils`, `Common`, `Persistence`, `Business`. Aucune bibliothèque tierce. |
| `RestServer.jar` | `RestServer` (maven-shade) | Le serveur, exécutable, avec toutes les bibliothèques tierces. Son manifeste pointe vers `base.jar` (`Class-Path`). |
| `JsfClient.war` | `Base` (maven-antrun) | La page « l'interface JSF a été supprimée », voir plus bas. |

Les deux jars vont **ensemble, dans le même dossier** : `java -jar RestServer.jar` charge `base.jar` à
côté de lui. C'est ce couple que publient les versions GitHub et qu'installe le système de mises à jour
(`updates/<version>/RestServer.jar` et `base.jar` dans le dossier de données).

Pourquoi deux fichiers : le code du projet change à chaque version, les bibliothèques rarement ;
`base.jar` reste petit et le contrôle d'une mise à jour (`Business/update/Artifacts`) y lit la version
de Java demandée et la présence du système de mise à jour.

## Arborescence

```
Base/
├── pom.xml                     shade des modules ax.ibr:*, copie vers deploy/, fabrication du WAR
└── src/main/retired-jsf/       contenu de JsfClient.war
    ├── index.html              page en français : JSF supprimé, aller sur Thermobox-Ng-Client
    └── WEB-INF/web.xml         page d'accueil index.html, toute adresse inconnue y renvoie
```

## `JsfClient.war`

L'interface JSF a été supprimée en 26.12. Une interface JSF 26.11 qui se met à jour télécharge pourtant
le `JsfClient.war` de la nouvelle version et exige qu'il contienne `WEB-INF/web.xml`. Les versions
publient donc ce WAR, réduit à une page statique : après la mise à jour, Tomcat sert cette page, qui
renvoie les utilisateurs vers [Thermobox-Ng-Client](https://github.com/IbraxTheKing/Thermobox-Ng-Client).
Le serveur REST ne le télécharge jamais.

## Version inscrite

Les workflows de publication passent `-Dthermobox.version=26.12.0` : Maven l'écrit dans
`thermobox-version.properties` (`Business`), lu par `BuildInfo`. Sans cette option, la version est
`dev` et le serveur traite toute mise à jour comme fonctionnelle.
