#!/usr/bin/env bash
# HTTPS certificate renewal – cron twice a day. Certbot only renews when the certificate is close to expiry;
# Nginx reloads to use it.
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

compose run --rm certbot renew --webroot -w /var/www/certbot --quiet
compose exec -T nginx nginx -s reload
log "certificate check done"
