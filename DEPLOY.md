# DataExpert — déploiement

**Les deux modes coexistent** et restent maintenus :

| Mode | Usage |
|------|--------|
| **Docker** | Déploiement production (PostgreSQL, pgAdmin, services séparés) |
| **JAR** (`dist-client/`) | Secours sans Docker, dev local, sites sans virtualisation |

---

## Mode Docker (déploiement production)

### Architecture

```
                    ┌─────────────┐
  Navigateur ──────►│  web :80    │  nginx — Vue SPA + proxy /api
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │  api :8080  │  Spring Boot — REST, SSE, sync DSS, MQ
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
       ┌──────▼──────┐ ┌───▼───┐ ┌──────▼──────┐
       │ db :5432    │ │ pgadmin│ │ DSS Dahua   │
       │ PostgreSQL  │ │ :5050  │ │ (réseau LAN)│
       └─────────────┘ └───────┘ └─────────────┘
```

| Service | Rôle | Port exposé |
|---------|------|-------------|
| **web** | Frontend Vue + reverse-proxy nginx | `WEB_PORT` (80) |
| **api** | Backend Spring Boot + sync DSS | interne uniquement |
| **db** | PostgreSQL 16 + Flyway | **interne uniquement** (pas de port hôte) |
| **pgadmin** | Administration PostgreSQL | `127.0.0.1:5050` (localhost) |

### Prérequis

- Docker Engine 24+ (plugin Compose v2)
- Environ 2 Go de RAM libres le temps de construire les images
- Accès réseau au serveur DSS (`DSS_HOST`, ex. `192.168.10.21`)

### Installation sur le VPS

Prérequis : Docker Engine 24+ et le plugin Compose v2. Le VPS doit joindre le serveur DSS (`DSS_HOST`).

```bash
git clone <url-du-depot> dataexpert
cd dataexpert
cp .env.example .env
nano .env
bash deploy.sh
```

`bash deploy.sh` enchaîne les quatre étapes :

1. **Construction** des images `api` (Spring Boot) et `web` (Vue + nginx)
2. **Publication** vers `DOCKER_REGISTRY` si la variable est renseignée dans `.env` (sinon les images restent locales)
3. **Pull** de PostgreSQL, pgAdmin, et des images applicatives si un registre est configuré
4. **Déploiement** de toute la stack. Les données PostgreSQL sont dans le volume Docker `dss_pg_data` (aucun port publié sur l'hôte)

Avant le build, le script s'arrête si les mots de passe d'exemple sont encore dans `.env`.

Publication vers un registre (Docker Hub, GHCR, registre privé), puis redéploiement par pull :

```bash
docker login
# dans .env : DOCKER_REGISTRY=docker.io/votrecompte  et  IMAGE_TAG=1.0.0
bash deploy.sh
```

Sur une autre machine qui a déjà les images dans le registre :

```bash
bash deploy.sh pull
bash deploy.sh up
```

Autres commandes : `build`, `publish`, `down`, `status`, `logs`, `backup`.  
`bash deploy.sh down` arrête les conteneurs et **conserve** le volume PostgreSQL.

Détails : [docker/README.md](docker/README.md)

### Accès

| URL | Usage |
|-----|-------|
| http://IP-SERVEUR/ | Application (rapport, sync, config) |
| http://127.0.0.1:5050/ sur le VPS | pgAdmin (`PGADMIN_EMAIL` / `PGADMIN_PASSWORD`), via tunnel SSH |

Dans pgAdmin, le serveur **DataExpert PostgreSQL** est pré-configuré (`host=db`).  
Mot de passe Postgres : `POSTGRES_PASSWORD` du `.env`.

### Sécurité réseau

- **PostgreSQL** : aucun port publié sur l'hôte. Seuls `api` et `pgadmin` joignent `db:5432` via le réseau Docker `dss-net`.
- **pgAdmin** : bind `127.0.0.1` uniquement (administration depuis la machine hôte).
- **Application** : seul `WEB_PORT` (80) est exposé au réseau client.

Migration H2 ponctuelle : `docker-compose.migrate-local.yml` ouvre temporairement `127.0.0.1:5432`, puis le script referme le port.

### Commandes utiles

```bash
bash deploy.sh                  # build + publish + pull + déploiement
bash deploy.sh backup           # sauvegarde PostgreSQL -> backups/
bash deploy.sh status
bash deploy.sh logs             # logs API (logs web, logs db)
bash deploy.sh down             # arrêt, volumes conservés
```

Suppression des données PostgreSQL (irréversible) :

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml down -v
```

### Sync DSS : service séparé ou non ?

**Recommandation actuelle : non** — garder poll + sync + MQ dans le service **`api`** (1 seule instance).

| Critère | 1 service `api` | Worker séparé |
|---------|-----------------|---------------|
| Session DSS | 1 connexion, pas de conflit | Risque code **2004** si 2 comptes sur même user |
| Complexité | Faible | Queue, 2 déploiements, observabilité |
| SSE Sync/Rapport | Direct | Requiert bus ou polling |
| Scale horizontal API | ❌ (session DSS) | Possible avec compte DSS dédié worker |

Vous avez déjà eu un blocage **« The user has logged in »** avec 2 instances backend.  
Un worker séparé n'est pertinent que si :

1. Compte DSS **dédié** au worker (`DSS_WORKER_USERNAME`)
2. L'`api` n'appelle **plus** le DSS (sync déclenchée via file/DB)
3. Profil Spring `worker` (template commenté dans `docker-compose.yml`)

Pour l'instant : **`deploy.replicas: 1`** implicite, sync intégrée à `api`.

### Dépannage

**L'API ne joint pas le DSS**  
Vérifier que l'IP DSS est joignable depuis le conteneur :
```bash
docker compose exec api curl -k https://192.168.10.21/
```

**Flyway / base vide**  
Au 1er démarrage, Flyway crée le schéma. Optionnel : activer  
`DSS_STARTUP_FULL_SYNC_ENABLED=true` pour importer l'historique DSS.

**Logs API**
```bash
bash deploy.sh logs
```

---

## Mode JAR (alternative — conservé)

Sans Docker : Java 21 + H2 embarquée. Utile en secours ou chez un client sans virtualisation.

Pack prêt : dossier `dist-client/`

```
dist-client/
  dss-integration.jar
  web/                 # frontend buildé
  .env.example
  start.bat
  data/                # H2 créé au 1er lancement
```

1. Copier `dist-client` chez le client
2. `.env.example` → `.env` (DSS_HOST, user, mdp)
3. Double-clic `start.bat`
4. Ouvrir http://localhost:8080/

Rebuild :
```powershell
cd dss-frontend ; npm run build
cd ..\dss-integration ; .\mvnw.cmd -DskipTests package
# recopier jar + dist dans dist-client
```

---

## Migration H2 → PostgreSQL (après sync locale)

Quand la sync est terminée en JAR, avant le déploiement Docker :

```powershell
.\scripts\migrate-h2-to-postgres.ps1 -DryRun   # simulation
.\scripts\migrate-h2-to-postgres.ps1           # migration + docker compose up
```

Détails : `dss-integration/docs/MIGRATION_TO_POSTGRES.md`
