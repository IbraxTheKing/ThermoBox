# Créer un driver de protocole

Le driver est la classe qui discute avec les boîtiers (annonce des boîtiers, réception des températures, envoi des consignes). Tous les drivers héritent de `ProtocolDriver` (package `ax.ibr.thermobox.business.protocols`). Il en existe deux pour l'instant : `MqttDriver` et `SimulatedProtocolDriver`.

## Important : on ne crée jamais un driver soi-même

Le driver est un singleton géré par la `BusinessFactory`. Partout dans le code, on le récupère avec :

```kotlin
val driver = BusinessFactory.getDriver()
```

Il ne faut pas écrire `MqttDriver()` ou `SimulatedProtocolDriver()` dans le reste de l'application. Une deuxième instance ouvrirait une deuxième connexion au broker (ou lancerait un deuxième planificateur pour le driver simulé), et chaque message serait enregistré deux fois en base. Le seul endroit où on instancie un driver, c'est dans la `BusinessFactory`.

## Écrire le driver

Il faut hériter de `ProtocolDriver` et implémenter `listen()`, `listenToRooms()` et `listenToTemperatures()`. `sendSetpoint()` est `open` et doit être redéfinie pour envoyer réellement la consigne.

```kotlin
class MonDriver : ProtocolDriver() {

    override fun listen() {
        listenToRooms()
        listenToTemperatures()
    }

    override fun listenToRooms() {
        // à chaque boîtier qui s'annonce (ne fait rien s'il existe déjà) :
        // registerDevice(name)
    }

    override fun listenToTemperatures() {
        // à chaque mesure reçue, uniquement pour un Device :
        // val t = Measurement(value, LocalDateTime.now())
        // roomTempAttrService.add(RoomTempAttr(device, t))
    }

    override fun sendSetpoint(s: Room, c: Setpoint) {
        super.sendSetpoint(s, c)
        // envoi de la consigne au boîtier
    }
}
```

`ProtocolDriver` fournit déjà `roomService`, `temperatureService` et `roomTempAttrService` (récupérés eux aussi depuis la `BusinessFactory`), ainsi que `registerDevice()` et `getDevices()`. Il n'y a pas besoin d'en créer d'autres.

Les mesures vont toujours sur un `Device`, jamais directement sur une salle : c'est la salle qui fait la moyenne de ses boîtiers (rattachés via `RoomRoomAttr`). La température s'enregistre avec un seul `roomTempAttrService.add(...)` : le lien la persiste en cascade, l'enregistrer aussi avec `temperatureService` la dupliquerait.

Dans les callbacks de réception, un message mal formé (salle inconnue, valeur illisible…) doit simplement être ignoré. Une exception levée à cet endroit peut couper l'écoute. Une mesure hors des limites des mesures (`SetpointRules.acceptsMeasurement(value)` faux) est une erreur de capteur : elle est ignorée aussi.

## L'appel à `super.sendSetpoint()`

Une consigne se règle au niveau d'une salle : `ClimateService.sendSetpoint()` l'enregistre sur la salle puis appelle `ProtocolDriver.sendSetpoint()` pour chacun de ses boîtiers.

L'appel à `super.sendSetpoint()` doit être fait en premier, avant tout envoi. C'est lui qui valide la consigne (`SetpointRules`, avec les limites choisies par un administrateur, `GET /api/v2/server/limits`, -10 à 50 °C par défaut) :

| Cas | Exception |
|---|---|
| Le boîtier n'a pas d'id | `NullException` |
| Le boîtier n'existe pas en base | `DontExistException` |
| Valeur sous le minimum des consignes | `ImpossibleValueException` |
| Valeur au-dessus du maximum des consignes | `TooHotException` |

Si on oublie cet appel, des consignes invalides peuvent partir vers les boîtiers.

## Brancher le driver

Pour utiliser le nouveau driver, on ajoute une valeur à `ConfigDriver.DriverType` (lue dans `THERMOBOX_DRIVER`) et le cas correspondant dans la `BusinessFactory`, qui crée alors `MonDriver`. Le reste du code n'a pas à changer, puisqu'il passe par `getDriver()` :

```kotlin
BusinessFactory.getDriver().listen()
BusinessFactory.getDriver().sendSetpoint(device, setpoint)
```

## Topics MQTT (`MqttDriver`)

| Topic | Sens | Contenu |
| :--- | :--- | :--- |
| `thermobox/rooms` | boîtier → serveur | le nom du boîtier qui s'annonce |
| `thermobox/temperatures/<id>` | boîtier → serveur | la température mesurée (°C) |
| `thermobox/setpoints/<id>` | serveur → boîtier | la consigne (°C, message retenu) |

Les topics des boîtiers installés avant 26.11 restent servis : le serveur écoute aussi `thermobox/salles` et publie chaque consigne aussi sur `thermobox/consignes/<id>`.
