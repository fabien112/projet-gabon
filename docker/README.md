# Stack Docker DataExpert

## Services

| Service | Image / build | Port | Rôle |
|---------|---------------|------|------|
| **web** | `dss-frontend/Dockerfile` | `${WEB_PORT:-80}` | Vue SPA + nginx → proxy `/api` |
| **api** | `dss-integration/Dockerfile` | interne 8080 | Spring Boot, sync DSS, Flyway |
| **db** | `postgres:16-alpine` | *(interne)* | PostgreSQL — **aucun port exposé** ; accès via `db:5432` sur `dss-net` |
| **pgadmin** | `dpage/pgadmin4:8.14` | `127.0.0.1:${PGADMIN_PORT}` | Admin BD (localhost uniquement) |

## Démarrage production (VPS)

```bash
cp .env.example .env
# Éditer : POSTGRES_PASSWORD, PGADMIN_PASSWORD, DSS_*, APP_*
# Optionnel : DOCKER_REGISTRY + IMAGE_TAG pour publier les images

bash deploy.sh
```

Le script construit les images, les publie si un registre est configuré, les récupère, puis démarre la stack. PostgreSQL est stocké dans le volume Docker `dss_pg_data`.

## Fichiers

| Fichier | Description |
|---------|-------------|
| `docker-compose.yml` | Stack complète |
| `docker-compose.prod.yml` | pgAdmin localhost, limites mémoire |
| `docker/pgadmin/servers.json` | Serveur PG pré-configuré dans pgAdmin |
| `deploy.sh` | Pipeline complet : build, publish, pull, up |
| `scripts/docker-backup-db.sh` | Sauvegarde `pg_dump` |

## Prérequis client

- Docker Engine 24+ ou Docker Desktop
- Accès réseau LAN au serveur DSS (`DSS_HOST`)
- Ports ouverts : `WEB_PORT` (80), optionnel `PGADMIN_PORT` (5050)

## Migration H2 → PostgreSQL

Après sync locale en JAR :
```powershell
.\scripts\migrate-h2-to-postgres.ps1
```

## Sauvegarde

```bash
bash deploy.sh backup
# -> backups/dss-backup-YYYYMMDD-HHMMSS.sql
```

## Vérification

```bash
bash deploy.sh status
bash deploy.sh logs
curl -fsS http://localhost/healthz
docker compose exec api curl -k https://VOTRE_DSS_HOST/
```

## Arrêt

```bash
bash deploy.sh down          # conserve les volumes
docker compose -f docker-compose.yml -f docker-compose.prod.yml down -v
# ⚠ la commande ci-dessus supprime les données PostgreSQL
```

Voir aussi : [DEPLOY.md](../DEPLOY.md)
