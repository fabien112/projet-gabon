#!/usr/bin/env bash
# DataExpert — déploiement production (VPS)
#
# Après un git clone :
#   cp .env.example .env
#   nano .env
#   bash deploy.sh
#
# Le script enchaîne : construction des images, publication (si registre),
# pull, puis démarrage de toute la stack (web + api + PostgreSQL en volume).
#
# Commandes :
#   bash deploy.sh            # pipeline complet
#   bash deploy.sh build      # construire api + web
#   bash deploy.sh publish    # pousser les images (DOCKER_REGISTRY requis)
#   bash deploy.sh pull       # récupérer postgres, pgAdmin, et les images publiées
#   bash deploy.sh up         # démarrer la stack déjà construite ou tirée
#   bash deploy.sh down       # arrêter (les volumes PostgreSQL sont conservés)
#   bash deploy.sh status
#   bash deploy.sh logs       # logs api (ou : bash deploy.sh logs web)
#   bash deploy.sh backup     # pg_dump dans ./backups

set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.prod.yml)

usage() {
  sed -n '2,21p' "$0" | sed 's/^# \{0,1\}//'
}

die() {
  echo "ERREUR: $*" >&2
  exit 1
}

require_docker() {
  command -v docker >/dev/null 2>&1 || die "Docker est introuvable. Installez Docker Engine 24+."
  docker compose version >/dev/null 2>&1 || die "Docker Compose v2 est introuvable (plugin « docker compose »)."
  docker info >/dev/null 2>&1 || die "Le démon Docker ne répond pas. Démarrez-le, ou ajoutez cet utilisateur au groupe docker."
}

ensure_env() {
  if [[ ! -f .env ]]; then
    cp .env.example .env
    echo
    echo "Fichier .env créé depuis .env.example."
    echo "Renseignez au minimum : POSTGRES_PASSWORD, PGADMIN_PASSWORD, DSS_HOST, DSS_USERNAME, DSS_PASSWORD."
    echo "Puis relancez : bash deploy.sh"
    exit 1
  fi
}

load_env() {
  local line key val
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ "$line" =~ ^[[:space:]]*# ]] && continue
    [[ -z "${line//[[:space:]]/}" ]] && continue
    [[ "$line" =~ ^([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]] || continue
    key="${BASH_REMATCH[1]}"
    val="${BASH_REMATCH[2]}"
    if [[ "$val" =~ ^\"(.*)\"$ ]] || [[ "$val" =~ ^\'(.*)\'$ ]]; then
      val="${BASH_REMATCH[1]}"
    fi
    export "$key=$val"
  done < .env
}

is_placeholder() {
  case "${1:-}" in
    ""|change_me|change_me_in_production|change_me_pgadmin|changeme|votre_user_dss|votre_mot_de_passe)
      return 0
      ;;
  esac
  return 1
}

require_secret() {
  local name="$1"
  local value="${!name:-}"
  if is_placeholder "$value"; then
    die "$name est vide ou encore un exemple. Éditez .env puis relancez."
  fi
}

validate_env() {
  require_secret POSTGRES_PASSWORD
  require_secret PGADMIN_PASSWORD
  require_secret DSS_HOST
  require_secret DSS_USERNAME
  require_secret DSS_PASSWORD
  require_secret APP_LOGIN_PASSWORD
  require_secret APP_SUPERADMIN_PASSWORD
}

resolve_images() {
  local tag="${IMAGE_TAG:-latest}"
  local registry="${DOCKER_REGISTRY:-}"
  registry="${registry%/}"
  if [[ -n "$registry" ]]; then
    export API_IMAGE="${registry}/dataexpert-api:${tag}"
    export WEB_IMAGE="${registry}/dataexpert-web:${tag}"
  else
    export API_IMAGE="dataexpert-api:${tag}"
    export WEB_IMAGE="dataexpert-web:${tag}"
  fi
}

prepare() {
  ensure_env
  load_env
  validate_env
  resolve_images
  require_docker
}

build_images() {
  echo
  echo "=== 1/4 Construction des images ==="
  echo "  API : $API_IMAGE"
  echo "  Web : $WEB_IMAGE"
  "${COMPOSE[@]}" build --pull api web
}

publish_images() {
  echo
  echo "=== 2/4 Publication ==="
  if [[ -z "${DOCKER_REGISTRY:-}" ]]; then
    echo "DOCKER_REGISTRY est vide : pas de push."
    echo "Les images locales seront déployées sur cette machine."
    echo "Pour un registre : DOCKER_REGISTRY=docker.io/votrecompte dans .env, puis docker login."
    return 0
  fi
  echo "Push $API_IMAGE"
  docker push "$API_IMAGE"
  echo "Push $WEB_IMAGE"
  docker push "$WEB_IMAGE"
}

pull_images() {
  echo
  echo "=== 3/4 Récupération des images ==="
  "${COMPOSE[@]}" pull db pgadmin
  if [[ -n "${DOCKER_REGISTRY:-}" ]]; then
    echo "Pull des images applicatives publiées."
    "${COMPOSE[@]}" pull api web
  else
    echo "Images applicatives déjà présentes localement (pas de registre)."
  fi
}

wait_healthy() {
  local i db api web
  echo
  echo "Attente de PostgreSQL, de l'API et du frontend (3 min max)..."
  for i in $(seq 1 36); do
    db="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' dss-db 2>/dev/null || echo starting)"
    api="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' dss-api 2>/dev/null || echo starting)"
    web="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' dss-web 2>/dev/null || echo starting)"
    echo "  db=$db  api=$api  web=$web"
    if [[ "$db" == "healthy" && "$api" == "healthy" && "$web" == "healthy" ]]; then
      return 0
    fi
    if [[ "$api" == "unhealthy" && "$i" -gt 6 ]]; then
      break
    fi
    sleep 5
  done
  echo
  echo "La stack n'est pas saine. Derniers logs API :"
  docker logs --tail 80 dss-api || true
  return 1
}

print_summary() {
  local port ip
  port="${WEB_PORT:-80}"
  ip="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
  echo
  echo "=== Stack démarrée ==="
  echo "  Application : http://localhost:${port}/"
  if [[ -n "$ip" ]]; then
    echo "  Réseau      : http://${ip}:${port}/"
  fi
  echo "  pgAdmin     : http://127.0.0.1:${PGADMIN_PORT:-5050}/  (localhost du VPS — tunnel SSH)"
  echo "  PostgreSQL  : volume Docker dss_pg_data (aucun port exposé)"
  echo "  Images      : $API_IMAGE , $WEB_IMAGE"
  echo
  echo "Logs : bash deploy.sh logs"
  echo "État : bash deploy.sh status"
}

deploy_stack() {
  echo
  echo "=== 4/4 Déploiement ==="
  "${COMPOSE[@]}" up -d --pull never --remove-orphans
  wait_healthy
  "${COMPOSE[@]}" ps
  print_summary
}

backup_db() {
  local dir stamp out db user
  dir="${1:-backups}"
  mkdir -p "$dir"
  stamp="$(date +%Y%m%d-%H%M%S)"
  db="${POSTGRES_DB:-dss}"
  user="${POSTGRES_USER:-dss_user}"
  out="$dir/dss-backup-${stamp}.sql"
  echo "Sauvegarde $db -> $out"
  docker exec -T dss-db pg_dump -U "$user" -d "$db" > "$out"
  echo "Terminé : $out"
}

cmd="${1:-all}"
if [[ $# -gt 0 ]]; then
  shift
fi

case "$cmd" in
  -h|--help|help)
    usage
    ;;
  build)
    prepare
    build_images
    ;;
  publish)
    prepare
    publish_images
    ;;
  pull)
    prepare
    pull_images
    ;;
  up|deploy)
    prepare
    deploy_stack
    ;;
  all)
    prepare
    build_images
    publish_images
    pull_images
    deploy_stack
    ;;
  down)
    require_docker
    ensure_env
    load_env
    resolve_images
    "${COMPOSE[@]}" down
    echo "Arrêté. Les volumes PostgreSQL sont conservés."
    echo "Pour effacer les données : docker compose -f docker-compose.yml -f docker-compose.prod.yml down -v"
    ;;
  status|ps)
    require_docker
    ensure_env
    load_env
    resolve_images
    "${COMPOSE[@]}" ps
    ;;
  logs)
    require_docker
    ensure_env
    load_env
    resolve_images
    "${COMPOSE[@]}" logs -f "${1:-api}"
    ;;
  backup)
    require_docker
    ensure_env
    load_env
    backup_db "$@"
    ;;
  *)
    usage
    die "Commande inconnue : $cmd"
    ;;
esac
