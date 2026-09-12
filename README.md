# ThermoBox

## C'est quoi ?

**ThermoBox** est une application qui sert à récupérer, stocker et partager des données de température.

## À quoi ça sert ?

Ce projet permet de :
- 📡 **Recevoir** des données de température depuis des capteurs (via MQTT)
- 💾 **Stocker** ces données dans une base de données
- 🌐 **Partager** les données via une API web (serveur REST)

## Comment c'est organisé ?

Le projet est découpé en plusieurs parties :

- **Common** : Les classes communes utilisées partout
- **Business** : La logique du programme (traitement des données)
- **Persistence** : L'accès à la base de données
- **Io** : Les entrées/sorties (MQTT, fichiers...)
- **RestServer** : L'API web pour consulter les données
- **ibr-utils** : Des outils pratiques réutilisables

## Technologies utilisées

- **Langage** : Kotlin et Java
- **MQTT** : Pour recevoir les données des capteurs
- **Maven** : Pour construire le projet

## Licence

MIT License
