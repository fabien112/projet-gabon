#!/usr/bin/env bash
# Sauvegarde PostgreSQL (volume Docker) -> ./backups/dss-backup-YYYYMMDD-HHMMSS.sql
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
exec bash "$ROOT/deploy.sh" backup "$@"
