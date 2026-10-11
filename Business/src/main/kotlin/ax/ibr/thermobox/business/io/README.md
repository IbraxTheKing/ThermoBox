# Créer un driver de protocole

Le driver est la classe qui discute avec les boîtiers (annonce des boîtiers, réception des températures, envoi des consignes). Tous les drivers héritent de `ProtocolDriver` (package `ax.ibr.thermobox.business.protocols`). Il en existe deux pour l'instant : `MqttDriver` et `SimulatedProtocolDriver`.

## Important : on ne crée jamais un driver soi-même

Le driver est un singleton géré par la `BusinessFactory`. Partout dans le code, on le récupère avec :

```kotlin
val driver = BusinessFactory.getDriver()
```

Il ne faut pas écrire `MqttDriver()` ou `SimulatedProtocolDriver()` dans le reste de l'application. Une deuxième instance ouvrirait une deuxième connexion au broker (ou lancerait un deuxième planificateur pour le driver simulé), et chaque message serait enregistré deux fois en base. Le seul endroit où on instancie un driver, c'est dans la `BusinessFactory`.

## Écrire le driver

Il faut hériter de `ProtocolDriver` et implémenter `listen()`, `listenToRooms()` et `listenToTemperatures()`. `sendConsigne()` est `open` et doit être redéfinie pour envoyer réellement la consigne.

```kotlin
class MonDriver : ProtocolDriver() {

    override fun listen() {
        listenToRooms()
        listenToTemperatures()
    }

    override fun listenToRooms() {
        // à chaque boîtier qui s'annonce (ne fait rien s'il existe déjà) :
        // registerBoitier(name)
    }

    override fun listenToTemperatures() {
        // à chaque mesure reçue, uniquement pour un Boitier :
        // val t = Mesurer(valeur, LocalDateTime.now())
        // salleTempAttrService.add(SalleTempAttr(boitier, t))
    }

    override fun sendConsigne(s: Salle, c: Consigne) {
        super.sendConsigne(s, c)
        // envoi de la consigne au boîtier
    }
}
```

`ProtocolDriver` fournit déjà `salleService`, `temperatureService` et `salleTempAttrService` (récupérés eux aussi depuis la `BusinessFactory`), ainsi que `registerBoitier()` et `getBoitiers()`. Il n'y a pas besoin d'en créer d'autres.

Les mesures vont toujours sur un `Boitier`, jamais directement sur une salle : c'est la salle qui fait la moyenne de ses boîtiers (rattachés via `SalleSalleAttr`). La température s'enregistre avec un seul `salleTempAttrService.add(...)` : le lien la persiste en cascade, l'enregistrer aussi avec `temperatureService` la dupliquerait.

Dans les callbacks de réception, un message mal formé (salle inconnue, valeur illisible…) doit simplement être ignoré. Une exception levée à cet endroit peut couper l'écoute.

## L'appel à `super.sendConsigne()`

Une consigne se règle au niveau d'une salle : `ClimatService.envoyerConsigne()` l'enregistre sur la salle puis appelle `sendConsigne()` pour chacun de ses boîtiers.

L'appel à `super.sendConsigne()` doit être fait en premier, avant tout envoi. C'est lui qui valide la consigne (les limites de valeur sont dans `ConsigneRules`) :

| Cas | Exception |
|---|---|
| Le boîtier n'a pas d'id | `NullException` |
| Le boîtier n'existe pas en base | `DontExistException` |
| Valeur ≥ 100 °C ou ≤ -10 °C | `ImpossibleValueException` |
| Valeur ≥ 50 °C | `TooHotException` |

Si on oublie cet appel, des consignes invalides peuvent partir vers les boîtiers.

## Brancher le driver

Pour utiliser le nouveau driver, on modifie la `BusinessFactory` pour qu'elle crée `MonDriver` au lieu du driver actuel. Le reste du code n'a pas à changer, puisqu'il passe par `getDriver()` :

```kotlin
BusinessFactory.getDriver().listen()
BusinessFactory.getDriver().sendConsigne(boitier, consigne)
```
