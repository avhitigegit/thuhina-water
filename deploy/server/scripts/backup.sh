#!/usr/bin/env bash
# Nightly backup (design 12.3) – cron 23:00, also safe to run by hand:
#   sudo bash /opt/thuhina/current/scripts/backup.sh
# 1. database: pg_dump (custom format)   2. uploaded files (company logo, quotation attachments): tar.gz
# Each run makes new dated files → kept 3 days on the server → copied to S3 (deleted there after 7 days by the
# bucket's lifecycle rule), so the last 7 nights can be restored.
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

DB=$(env_get POSTGRES_DB)
DB_USER=$(env_get POSTGRES_USER)
NAME="$PROJECT-$(env_get APP_ENV)-$(date +%Y%m%d-%H%M)"
DB_FILE="$APP_DIR/backups/$NAME.dump"
FILES_FILE="$APP_DIR/backups/$NAME-files.tar.gz"

log "database backup → $(basename "$DB_FILE")"
compose exec -T db pg_dump -U "$DB_USER" -d "$DB" -Fc > "$DB_FILE"
[ -s "$DB_FILE" ] || { log "database backup file is empty"; exit 1; }
aws s3 cp "$DB_FILE" "s3://$BACKUP_BUCKET/db/$(basename "$DB_FILE")" --only-show-errors

log "files backup → $(basename "$FILES_FILE")"
compose exec -T api tar -czf - -C /data/files . > "$FILES_FILE"
[ -s "$FILES_FILE" ] || { log "files backup is empty"; exit 1; }
aws s3 cp "$FILES_FILE" "s3://$BACKUP_BUCKET/files/$(basename "$FILES_FILE")" --only-show-errors

find "$APP_DIR/backups" \( -name '*.dump' -o -name '*-files.tar.gz' \) -mtime +2 -delete

log "backup done (database $(du -h "$DB_FILE" | cut -f1), files $(du -h "$FILES_FILE" | cut -f1)) and copied to S3"
