# Créer un driver de protocole

Le driver est la classe qui discute avec les boîtiers (réception des salles et des températures, envoi des consignes). Tous les drivers héritent de `ProtocolDriver` (package `ax.ibr.thermobox.business.protocols`). Il en existe deux pour l'instant : `MqttDriver` et `SimulatedProtocolDriver`.

## Important : on ne crée jamais un driver soi-même

Le driver est un singleton géré par la `BusinessFactory`. Partout dans le code, on le récupère avec :

```kotlin
val driver = BusinessFactory().getDriver()
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
        // à chaque salle reçue :
        // if (salleService.getByName(name) == null) salleService.update(Salle(name))
    }

    override fun listenToTemperatures() {
        // à chaque mesure reçue :
        // val t = Mesurer(valeur, LocalDateTime.now())
        // temperatureService.update(t)
        // salleTempAttrService.update(SalleTempAttr(salle, t))
    }

    override fun sendConsigne(s: Salle, c: Consigne) {
        super.sendConsigne(s, c)
        // envoi de la consigne au boîtier
    }
}
```

`ProtocolDriver` fournit déjà `salleService`, `temperatureService` et `salleTempAttrService` (récupérés eux aussi depuis la `BusinessFactory`). Il n'y a pas besoin d'en créer d'autres.

Dans les callbacks de réception, un message mal formé (salle inconnue, valeur illisible…) doit simplement être ignoré. Une exception levée à cet endroit peut couper l'écoute.

## L'appel à `super.sendConsigne()`

Il doit être fait en premier, avant tout envoi. C'est lui qui valide la consigne :

| Cas | Exception |
|---|---|
| La salle n'a pas d'id | `NullException` |
| La salle n'existe pas en base | `DontExistException` |
| Valeur ≥ 100 °C ou ≤ -10 °C | `ImpossibleValueException` |
| Valeur ≥ 50 °C | `TooHotException` |

Si on oublie cet appel, des consignes invalides peuvent partir vers les boîtiers.

## Brancher le driver

Pour utiliser le nouveau driver, on modifie la `BusinessFactory` pour qu'elle crée `MonDriver` au lieu du driver actuel. Le reste du code n'a pas à changer, puisqu'il passe par `getDriver()` :

```kotlin
BusinessFactory().getDriver().listen()
BusinessFactory().getDriver().sendConsigne(salle, consigne)
```
