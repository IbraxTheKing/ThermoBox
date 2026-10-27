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

# Même chose avec Docker (Linux ; sous Windows / macOS : BASE_URL=http://host.docker.internal:8080)
docker run --rm -i --network host -v "$PWD/loadtest:/loadtest" grafana/k6:0.54.0 \
  run -e USERS=100 /loadtest/thermobox.js

# Une charge fixe : 150 utilisateurs pendant 10 minutes
k6 run -e USERS=100 -e SCENARIO=steady -e VUS=150 -e DURATION=10m loadtest/thermobox.js

# Le débit maximal, sans temps de lecture
k6 run -e USERS=100 -e SCENARIO=steady -e VUS=50 -e THINK=0 loadtest/thermobox.js
```

| Option (`-e`) | Défaut | Rôle |
|---|---|---|
| `BASE_URL` | `http://127.0.0.1:8080` | Le serveur testé |
| `USERS` | `admin` | Comptes, séparés par des virgules, ou un nombre N pour `usera`… (les N premiers du jeu huge) |
| `PASSWORD` | `password123` | Leur mot de passe |
| `SCENARIO` | `ramp` | `ramp` (paliers) ou `steady` (charge fixe) |
| `VUS`, `DURATION` | `50`, `5m` | Pour `steady` |
| `THINK` | `2` | Secondes de lecture entre deux pages (`0` : enchaîner sans pause) |
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
curl -i http://127.0.0.1:8080/api/v2/server      # 26.12 et plus : {"name": "ThermoBox", "version": …}
curl -i http://127.0.0.1:8080/api/server         # 26.11 et plus : la version, même sans /api/v2
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
| `server_gc_ms_per_interval` | Temps passé par le ramasse-miettes entre deux relevés |

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
- **les connexions à MySQL** : MySQL en accepte 151 par défaut (`max_connections`). En mode
  `THERMOBOX_DB_ACCESS=jdbc`, le serveur ouvre une connexion par requête en cours, sans plafond : au-delà
  de 151 requêtes simultanées, MySQL répond « Too many connections ». En mode `jpa`, EclipseLink limite
  son pool (32 connexions par défaut) et les requêtes attendent leur tour ;
- **les threads de Jetty** (200 par défaut) : au-delà, les requêtes attendent dans la file.

Répétez le test après chaque changement important du serveur, sur la même base, pour comparer.
