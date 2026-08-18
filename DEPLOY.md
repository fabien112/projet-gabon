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
| **db** | PostgreSQL 16 + Flyway | interne (optionnel 5432) |
| **pgadmin** | Administration PostgreSQL | `PGADMIN_PORT` (5050) |

### Prérequis

- Docker Engine 24+ ou Docker Desktop
- Accès réseau au serveur DSS (`DSS_HOST`, ex. `192.168.10.21`)

### Installation

```powershell
cd DAHUA
copy .env.example .env
# Éditer .env : POSTGRES_PASSWORD, PGADMIN_PASSWORD, DSS_*, APP_*

docker compose up -d --build
```

### Accès

| URL | Usage |
|-----|-------|
| http://IP-SERVEUR/ | Application (rapport, sync, config) |
| http://IP-SERVEUR:5050/ | pgAdmin (`PGADMIN_EMAIL` / `PGADMIN_PASSWORD`) |

Dans pgAdmin, le serveur **DataExpert PostgreSQL** est pré-configuré (`host=db`).  
Mot de passe Postgres : `POSTGRES_PASSWORD` du `.env`.

### Commandes utiles

```powershell
docker compose ps
docker compose logs -f api
docker compose restart api
docker compose down          # arrêt
docker compose down -v       # arrêt + suppression volumes (⚠ données)
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
```powershell
docker compose exec api curl -k https://192.168.10.21/
```

**Flyway / base vide**  
Au 1er démarrage, Flyway crée le schéma. Optionnel : activer  
`DSS_STARTUP_FULL_SYNC_ENABLED=true` pour importer l'historique DSS.

**Logs API**
```powershell
docker compose logs -f api
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
