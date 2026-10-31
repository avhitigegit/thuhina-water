#!/usr/bin/env bash
# Nightly database backup (design 12.3) – cron 23:00, also safe to run by hand:
#   sudo bash /opt/thuhina/current/scripts/backup.sh
# pg_dump (custom format) → kept 3 days on the server → copied to S3 (deleted there by the bucket's lifecycle rule).
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

DB=$(env_get POSTGRES_DB)
DB_USER=$(env_get POSTGRES_USER)
FILE="$APP_DIR/backups/$PROJECT-$(env_get APP_ENV)-$(date +%Y%m%d-%H%M).dump"

log "backup → $(basename "$FILE")"
compose exec -T db pg_dump -U "$DB_USER" -d "$DB" -Fc > "$FILE"
[ -s "$FILE" ] || { log "backup file is empty"; exit 1; }

aws s3 cp "$FILE" "s3://$BACKUP_BUCKET/db/$(basename "$FILE")" --only-show-errors
find "$APP_DIR/backups" -name '*.dump' -mtime +2 -delete

log "backup done ($(du -h "$FILE" | cut -f1)) and copied to S3"
