#!/usr/bin/env bash
# Restores a backup (design 12.3). REPLACES the current data.
#   database:        sudo bash /opt/thuhina/current/scripts/restore.sh <name>.dump --yes
#   uploaded files:  sudo bash /opt/thuhina/current/scripts/restore.sh <name>-files.tar.gz --yes
# <name> is a file name in S3 (db/ or files/) or a path on this server. Restore both from the same night.
# List the backups:  aws s3 ls s3://<backup bucket>/db/   and   aws s3 ls s3://<backup bucket>/files/
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

SOURCE="${1:?usage: restore.sh <backup file> --yes}"
[ "${2:-}" = "--yes" ] || { echo "This replaces the current data. Run again with --yes to continue."; exit 1; }

case "$SOURCE" in
  *-files.tar.gz) KIND=files ;;
  *.dump)         KIND=db ;;
  *) echo "Expected a .dump (database) or -files.tar.gz (uploaded files) backup."; exit 1 ;;
esac

if [ -f "$SOURCE" ]; then
  FILE="$SOURCE"
else
  FILE="$APP_DIR/backups/$(basename "$SOURCE")"
  aws s3 cp "s3://$BACKUP_BUCKET/$KIND/$(basename "$SOURCE")" "$FILE" --only-show-errors
fi

if [ "$KIND" = files ]; then
  log "restoring uploaded files from $(basename "$FILE")"
  compose exec -T api sh -c 'find /data/files -mindepth 1 -delete && tar -xzf - -C /data/files' < "$FILE"
  log "files restore finished"
  exit 0
fi

DB=$(env_get POSTGRES_DB)
DB_USER=$(env_get POSTGRES_USER)

log "restoring $(basename "$FILE") – stopping the API"
compose stop api
compose exec -T db pg_restore -U "$DB_USER" -d "$DB" --clean --if-exists --no-owner < "$FILE"
compose start api
wait_for_api
log "database restore finished"
