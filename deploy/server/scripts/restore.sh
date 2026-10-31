#!/usr/bin/env bash
# Restores a database backup (design 12.3). REPLACES all data in the database.
#   sudo bash /opt/thuhina/current/scripts/restore.sh <file name in S3 db/ | path on this server> --yes
# List the backups:  aws s3 ls s3://<backup bucket>/db/
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

SOURCE="${1:?usage: restore.sh <backup file> --yes}"
[ "${2:-}" = "--yes" ] || { echo "This replaces ALL data in the database. Run again with --yes to continue."; exit 1; }

if [ -f "$SOURCE" ]; then
  FILE="$SOURCE"
else
  FILE="$APP_DIR/backups/$(basename "$SOURCE")"
  aws s3 cp "s3://$BACKUP_BUCKET/db/$(basename "$SOURCE")" "$FILE" --only-show-errors
fi

DB=$(env_get POSTGRES_DB)
DB_USER=$(env_get POSTGRES_USER)

log "restoring $(basename "$FILE") – stopping the API"
compose stop api
compose exec -T db pg_restore -U "$DB_USER" -d "$DB" --clean --if-exists --no-owner < "$FILE"
compose start api
wait_for_api
log "restore finished"
