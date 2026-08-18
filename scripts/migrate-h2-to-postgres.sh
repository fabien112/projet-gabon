#!/usr/bin/env bash
# Migration H2 -> PostgreSQL — passage en production DataExpert
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

ENV_FILE="${ENV_FILE:-.env}"
[[ -f "$ENV_FILE" ]] || ENV_FILE=".env.example"

# shellcheck disable=SC1090
source /dev/null 2>/dev/null || true
export $(grep -v '^#' "$ENV_FILE" | xargs -0 2>/dev/null || grep -v '^#' "$ENV_FILE" | xargs)

POSTGRES_DB="${POSTGRES_DB:-dss}"
POSTGRES_USER="${POSTGRES_USER:-dss_user}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-${DB_PASSWORD:-}}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
H2_PATH="${H2_PATH:-$ROOT/dist-client/data/dss}"

if [[ -z "$POSTGRES_PASSWORD" ]]; then
  read -rsp "Mot de passe PostgreSQL ($POSTGRES_USER): " POSTGRES_PASSWORD
  echo
fi

if [[ ! -f "${H2_PATH}.mv.db" ]]; then
  echo "Base H2 introuvable : ${H2_PATH}.mv.db"
  exit 1
fi

echo "=== DataExpert : migration H2 -> PostgreSQL ==="
echo "Source H2 : $H2_PATH"
echo "Cible PG  : ${POSTGRES_USER}@localhost:${POSTGRES_PORT}/${POSTGRES_DB}"
echo ""
read -rp "Sync terminée et JAR arrêté ? (o/N) " ok
[[ "$ok" =~ ^[oOyY] ]] || exit 0

echo "[1/3] PostgreSQL..."
docker compose --env-file "$ENV_FILE" up -d db
until docker inspect --format='{{.State.Health.Status}}' dss-db 2>/dev/null | grep -q healthy; do sleep 3; done

echo "[2/3] Migration..."
cd dss-integration
./mvnw -q -DskipTests compile exec:java \
  -Dexec.mainClass=com.company.dss.tools.H2ToPostgresMigrator \
  -Dexec.classpathScope=runtime \
  -Dexec.args="--h2-path ${H2_PATH} --pg-url jdbc:postgresql://localhost:${POSTGRES_PORT}/${POSTGRES_DB} --pg-user ${POSTGRES_USER} --pg-password ${POSTGRES_PASSWORD} ${DRY_RUN:-}"

echo "[3/3] Stack complète..."
cd "$ROOT"
docker compose --env-file "$ENV_FILE" exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
  "SELECT COUNT(*), MIN(slot_date), MAX(slot_date) FROM people_counting_hourly;"
docker compose --env-file "$ENV_FILE" up -d --build
echo "Terminé."
