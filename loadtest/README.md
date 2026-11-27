# Test de charge

`thermobox.js` simule des utilisateurs du client Angular avec [k6](https://k6.io) : chacun se connecte,
ouvre l'aperçu (arbre, favoris, réglages, mini-courbes de 24 h), puis la page d'un élément avec ses
courbes sur 7 jours, lit chaque page environ 2 s, et recommence. Le scénario par défaut fait monter le
nombre d'utilisateurs par paliers pour trouver la limite du serveur.

## Préparer

1. Un serveur de test, jamais la production (le test le charge volontairement jusqu'à la saturation).
2. Une base réaliste : `DatabaseSeeder huge` (voir `Business/README.md`) crée 960 boîtiers, 4 millions de
   mesures et 500 comptes `usera`, `userb`… (mot de passe `password123`) aux accès limités, plus proches
   d'un vrai site que les 3 comptes de démonstration.
3. k6 : installé (`brew install k6`, `winget install k6`…), ou par Docker sans rien installer.

## Lancer

```bash
# Paliers de 10, 50, 100, 200 puis 400 utilisateurs (2 min chacun, ~13 min en tout),
# répartis sur les 100 premiers comptes du jeu huge
k6 run -e USERS=100 loadtest/thermobox.js

# Même chose avec Docker (Linux ; sous Windows / macOS : BASE_URL=https://host.docker.internal:8443)
docker run --rm -i --network host -v "$PWD/loadtest:/loadtest" grafana/k6:0.54.0 \
  run -e USERS=100 /loadtest/thermobox.js

# Une charge fixe : 150 utilisateurs pendant 10 minutes
k6 run -e USERS=100 -e SCENARIO=steady -e VUS=150 -e DURATION=10m loadtest/thermobox.js

# Le débit maximal, sans temps de lecture
k6 run -e USERS=100 -e SCENARIO=steady -e VUS=50 -e THINK=0 loadtest/thermobox.js
```

| Option (`-e`) | Défaut | Rôle |
|---|---|---|
| `BASE_URL` | `https://127.0.0.1:8443` | Le serveur testé |
| `USERS` | `admin` | Comptes, séparés par des virgules, ou un nombre N pour `usera`… (les N premiers du jeu huge) |
| `PASSWORD` | `password123` | Leur mot de passe |
| `SCENARIO` | `ramp` | `ramp` (paliers), `steady` (charge fixe) ou `enterprise` (une journée d'entreprise, voir [plus bas](#scénario-entreprise)) |
| `VUS`, `DURATION` | `50`, `5m` | Pour `steady` |
| `THINK` | `2` (`20` pour `enterprise`) | Secondes de lecture entre deux pages (`0` : enchaîner sans pause) |
| `MONITOR_USER`, `MONITOR_PASSWORD` | `admin`, `PASSWORD` | Compte ADMIN qui relève la charge du serveur (voir plus bas) |
| `MONITOR_EVERY` | `5` | Secondes entre deux relevés ; `MONITOR=0` pour ne rien relever |

Les comptes se connectent une fois, au début (`setup`) : la connexion est volontairement lente (BCrypt),
et 400 connexions simultanées mesureraient surtout BCrypt. Plusieurs utilisateurs simulés partagent un
compte quand il y en a moins qu'eux. Les jetons durent `THERMOBOX_JWT_TTL_MINUTES` (60 min) : pour un
test plus long, augmentez-le sur le serveur de test.

## Le serveur répond 404

`/api/v2/server answers 404` alors que le serveur tourne : ce n'est pas un serveur 26.12 qui répond. Le
test le précise : une version plus ancienne de ThermoBox (avec son numéro), ou autre chose.

```bash
curl -ik https://127.0.0.1:8443/api/v2/server      # 26.12 et plus : {"name": "ThermoBox", "version": …}
curl -ik https://127.0.0.1:8443/api/server         # 26.11 et plus : la version, même sans /api/v2
docker compose logs rest | grep "REST server started"           # version réellement lancée
docker compose exec rest cat /var/lib/thermobox/updates/current  # version installée depuis l'interface
```

Avec Docker, **une version installée depuis l'interface passe avant celle de l'image** (elle est dans le
volume `thermobox_data`, voir DOCKER.md) : reconstruire l'image ne suffit pas. Pour tester le code
compilé, revenez aux fichiers de l'image :

```bash
docker compose exec rest rm /var/lib/thermobox/updates/current
docker compose up -d --build --force-recreate rest
```

Si le navigateur obtient bien la réponse de ThermoBox mais pas k6 : sous Windows, `localhost` peut
désigner d'abord `::1` (IPv6), où un autre programme peut écouter sur le même port, alors que le
navigateur passe par `127.0.0.1`. C'est pourquoi le test vise `127.0.0.1` par défaut ; le message
d'erreur donne l'adresse et l'en-tête `Server` de ce qui a répondu. `netstat -ano | findstr :8080`
(Windows) ou `ss -ltnp | grep 8080` (Linux) liste les programmes qui écoutent sur le port.

Sous Windows, **NVIDIA Broadcast** écoute sur `127.0.0.1:8080` (il répond « Cannot GET … ») : quittez-le
pendant les tests, ou mettez ThermoBox sur un autre port (`REST_PORT=8090` dans `.env`, puis
`-e BASE_URL=http://127.0.0.1:8090`).

Si `curl` ne trouve rien de ThermoBox, un autre programme écoute sur ce port (serveur de développement,
autre conteneur) : changez `REST_PORT` dans `.env` ou passez `-e BASE_URL=…`.

## Mesurer la charge du serveur (processeur, mémoire)

**Le serveur REST**, par le test lui-même : un utilisateur de plus, à part, lit
`GET /api/v2/server/metrics` toutes les 5 s pendant tout le test. Le résumé de k6 donne alors, à côté des
temps de réponse :

| Mesure | Sens |
|---|---|
| `server_cpu_process_percent` | Processeur utilisé par le serveur, en % des processeurs dont il dispose |
| `server_cpu_system_percent` | Processeur de toute la machine (ou du conteneur) |
| `server_heap_used_mb` | Mémoire Java utilisée (Mo) ; la limite est affichée au début du test |
| `server_threads` | Threads du serveur (Jetty en ajoute quand les requêtes s'accumulent) |
| `server_gc_ms_per_interval` | Temps pendant lequel le ramasse-miettes a arrêté le serveur entre deux relevés (5 s) : au-delà de quelques centaines de ms, la mémoire freine le serveur |

Il faut un compte ADMIN : `admin` avec le mot de passe `PASSWORD` par défaut (`MONITOR_USER`,
`MONITOR_PASSWORD` sinon). Si le compte ne convient pas, le test continue et prévient qu'il ne mesure pas
la charge. Avec `--out csv=resultats.csv`, ces relevés sont dans le fichier avec leur heure : on peut
tracer le processeur et la mémoire en face du nombre d'utilisateurs.

**MySQL** (et les conteneurs en général), par Docker : dans une deuxième fenêtre, pendant le test,

```bash
powershell -ExecutionPolicy Bypass -File loadtest\docker-stats.ps1     # Windows
sh loadtest/docker-stats.sh                                         # Linux, macOS
```

écrit `docker-stats.csv` toutes les 5 s : processeur et mémoire de `rest` et de `mysql`. Ctrl+C l'arrête.
Docker compte le processeur par cœur : 200 % = deux cœurs pleins.

## Lire le résultat

k6 affiche à la fin les temps de réponse par type de page (`page:overview`, `page:element`,
`page:curves`) et le taux d'erreurs. Les seuils (moins de 1 % d'erreurs, 95 % des pages en moins de 2 s,
des courbes en moins de 3 s) marquent le test en échec s'ils sont dépassés, sans l'arrêter.

Avec `ramp`, la limite est le palier où les temps de réponse s'envolent ou les erreurs apparaissent ;
pour la voir palier par palier, enregistrez les mesures puis regroupez-les par minute :

```bash
k6 run -e USERS=100 --out csv=resultats.csv loadtest/thermobox.js
```

Pendant le test, regardez aussi le serveur : `docker stats` (processeur, mémoire de `rest` et `mysql`),
et `docker compose logs -f rest` (erreurs). Ce qui sature en premier :

- **le processeur** de `rest` (calculs des courbes, JSON) ou de `mysql` (requêtes) ;
- **les connexions à MySQL** : le serveur en utilise au plus `THERMOBOX_DB_POOL_SIZE` à la fois (20),
  les autres opérations attendent leur tour (30 s au plus, puis une erreur 500). MySQL en accepte 151
  (`max_connections`) : un pool plus grand n'a de sens qu'avec un MySQL qui suit ;
- **les threads de Jetty** (200 par défaut) : au-delà, les requêtes attendent dans la file.

Une réponse autre que 200 est affichée pendant le test, une fois par type d'erreur et par utilisateur
simulé, avec le message du serveur (ou l'erreur réseau, statut 0) : c'est la cause à chercher dans
`docker compose logs rest`.

Répétez le test après chaque changement important du serveur, sur la même base, pour comparer.

## Scénario entreprise

Les autres scénarios cherchent la limite du serveur. Celui-ci joue une journée de travail d'une
entreprise entière, pour savoir si un serveur donné la tient : **lancez-le sur la machine qui servira
vraiment**, les chiffres d'un PC de bureau ne disent rien d'un petit serveur.

Ce qu'il fait, pendant une heure par défaut :

| Qui | Défaut | Ce qu'il fait |
|---|---|---|
| Les salariés (`EMPLOYEES`) | 1000, atteints en 10 min (`RAMP`) | Chacun lit une page toutes les 20 s environ (`THINK`) : aperçu, page d'un élément, courbes ; son client demande les notifications toutes les 2 min, comme les applications |
| L'arrivée du matin (`LOGINS`) | 5 connexions/s pendant 3 min (`LOGIN_PEAK`) | Des connexions en plus des jetons du début : c'est BCrypt, lent exprès, qui coûte |
| Les gestionnaires (`SETPOINTS`) | 20 consignes/min | Les comptes MANAGER parmi `USERS` (un sur dix dans le jeu `huge`) changent la consigne d'une de leurs salles, entre 19 et 23 °C |
| Les boîtiers (`devices.mjs`, à part) | tous, une mesure toutes les 30 s | Les mesures arrivent par MQTT, comme sur un vrai site : k6 ne parle pas MQTT, d'où ce second programme |

### Préparer

1. Une base à la taille de l'entreprise, par exemple deux fois `huge`, trois mois de mesures et 2000
   comptes (≈ 1 920 boîtiers, 25 millions de mesures ; plusieurs dizaines de minutes à remplir) :
   ```
   DatabaseSeeder huge buildings=20 days=90 users=2000
   ```
2. Le serveur écoute les boîtiers : dans `.env`, `THERMOBOX_DRIVER=mqtt` (le pilote simulé ajoute
   sinon ses propres mesures), puis le broker avec `docker compose --profile mqtt up -d`.
3. Des jetons qui tiennent tout le test : ils sont pris au début, `THERMOBOX_JWT_TTL_MINUTES=120` dans
   `.env` pour un test d'une heure (60 par défaut).
4. `THERMOBOX_DB_ACCESS=jdbc` (le défaut), et les réglages de mémoire et de pool que vous comptez
   garder : c'est eux qu'on mesure.

### Lancer

Deux fenêtres, en même temps :

```bash
# 1. Les boîtiers (Node 18 ou plus, rien à installer) : arrêtez-le avec Ctrl+C à la fin du test
node loadtest/devices.mjs --duration 70m

# 2. Les salariés, l'arrivée du matin, les gestionnaires (2000 comptes du jeu)
k6 run -e USERS=2000 -e SCENARIO=enterprise loadtest/thermobox.js

# Plus gros : 3000 personnes, 5000 boîtiers (les mêmes envoient plusieurs fois par période)
node loadtest/devices.mjs --boxes 5000 --duration 70m
k6 run -e USERS=2000 -e SCENARIO=enterprise -e EMPLOYEES=3000 loadtest/thermobox.js
```

`devices.mjs` (options `--name valeur`, ou la variable en majuscules) :

| Option | Défaut | Rôle |
|---|---|---|
| `--base` | `https://127.0.0.1:8443` | Le serveur REST, pour la liste de ses boîtiers |
| `--user`, `--password` | `admin`, `password123` | Un compte ADMIN qui voit tous les boîtiers |
| `--broker` | `mqtt://127.0.0.1:1883` | Le broker que le serveur écoute (`--mqtt-user`, `--mqtt-password` s'il en demande) |
| `--every` | `30` | Secondes entre deux mesures d'un boîtier |
| `--boxes` | tous | Boîtiers qui envoient ; plus que le serveur n'en a : les mêmes plusieurs fois, pour un site plus grand |
| `--connections` | `50` | Connexions MQTT sur lesquelles les boîtiers sont répartis |
| `--duration` | `1h` | Durée |

### Lire le résultat

- **k6** marque le test en échec au-delà de ce qu'un salarié supporte un jour normal (et non à
  saturation) : pages en plus de 2 s, courbes en plus de 3 s, notifications en plus de 2 s,
  connexion en plus de 5 s, consigne en plus de 1 s (p95), plus de 1 % d'erreurs.
- **`devices.mjs`** dit chaque minute combien de mesures sont parties et en combien de temps le
  serveur en a enregistré une (une valeur marquée envoyée à un boîtier, puis relue par l'API) : un
  délai qui grandit d'une minute à l'autre veut dire que le serveur ne suit plus les boîtiers, même
  si les pages restent rapides.
- La mémoire Java (`server_heap_used_mb`) sur une heure : si elle monte sans redescendre, quelque chose
  s'accumule. `--out csv=entreprise.csv` garde les relevés avec leur heure, pour la tracer.
- MySQL (`docker-stats`, plus haut) : c'est souvent lui qui manque de mémoire le premier sur une grande
  base (`MYSQL_BUFFER_POOL`).

## Résultats de référence

Mesurés en 26.12 sur un PC de bureau (Ryzen 7 5800X, 16 threads, Windows, Docker Desktop ; k6 sur la
même machine), base `DatabaseSeeder huge` (960 boîtiers, 4 millions de mesures), 100 comptes limités,
réglages par défaut de `.env.example` (`rest` 1 Go dont 768 Mo pour Java, `mysql` 1,5 Go, pool de 20,
`THERMOBOX_DB_ACCESS=jdbc`).

| Test | Requêtes/s | Temps de réponse (p95) | Erreurs | Serveur |
|---|---|---|---|---|
| `ramp` : jusqu'à 400 utilisateurs, 2 s de lecture | 208 en moyenne (~600 au dernier palier) | 20 ms | 0 % | 19 % du processeur au plus (~3 cœurs), 500 Mo de Java |
| `steady` : 150 utilisateurs, 2 s de lecture | 155 | 41 ms | 0 % | 11 % du processeur, 150 Mo de Java |
| `steady` : 50 utilisateurs sans pause (`THINK=0`) | 770 | 130 ms | 0 % | 30 % du processeur (~5 cœurs), 320 Mo de Java |
| Même test, `THERMOBOX_DB_ACCESS=jpa` | 35 | 3,7 s | 0 % | 9 % du processeur : le serveur attend |

Sans pause, ~130 parcours complets par seconde (aperçu, page d'un élément, courbes de 7 jours) : de
l'ordre de 500 utilisateurs actifs en même temps, quand chacun lit ses pages quelques secondes.

Pour comparaison, le premier test de 26.12, avant les optimisations : des pages en 36 s (p95),
3 Go de mémoire Java. Ce qui a compté, dans l'ordre :

1. **Index des courbes** (V6) : une journée d'un boîtier lisait tout son historique (4 321 lignes pour 114).
2. **Hiérarchie en mémoire** (`HierarchyCache`) : droits, arbre et courbes sans relire les éléments un par un.
3. **Moyennes horaires** (V7) : une semaine d'un boîtier en 168 lignes ; les mini-courbes de l'aperçu
   dès 12 heures (5 fois plus de requêtes par seconde sur `/climate/averages`).
4. **Mémoire par requête** : les mini-courbes lues par petits lots, les dates en nombre. Lire 50 salles
   d'un coup gardait assez de données pour occuper le ramasse-miettes : 28 requêtes/s au lieu de 109.
5. **Connexions gardées ouvertes** : 8 sur 20 seulement l'étaient, et le serveur rouvrait des centaines
   de connexions par seconde (22 % d'erreurs sans pause).
6. **JDBC par défaut** : 22 fois le débit de JPA sur le même test.

Ces chiffres valent pour cette machine. Pour dimensionner un serveur plus petit, limitez les conteneurs
à ce qu'il aura (`cpus: 2` sur `rest` et `mysql` dans `docker-compose.yml`, `REST_MEMORY`…) et relancez
`ramp`.
