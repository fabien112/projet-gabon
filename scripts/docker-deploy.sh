#!/usr/bin/env bash
# Conservé pour compatibilité. Le point d'entrée production est ../deploy.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

args=()
for arg in "$@"; do
  case "$arg" in
    --prod) ;;
    --build-only) args+=(build) ;;
    --down) args+=(down) ;;
    *) args+=("$arg") ;;
  esac
done

if [[ ${#args[@]} -eq 0 ]]; then
  exec bash "$ROOT/deploy.sh" all
fi
exec bash "$ROOT/deploy.sh" "${args[@]}"
