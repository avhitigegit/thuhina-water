#!/usr/bin/env bash
# Deploys one image tag on this server (plan section 9). Run as root by GitHub Actions through SSM,
# from the unpacked release folder:   bash <release>/scripts/deploy.sh <image tag>
#   1. .env from SSM Parameter Store   2. activate this release   3. pull images, start containers
#   4. wait for the API (Flyway migrates here)   5. HTTPS certificate on the first run
#   6. smoke test   7. cron: nightly backup + certificate renewal   8. tidy up old releases / images
set -euo pipefail

TAG="${1:?usage: deploy.sh <image tag>}"
RELEASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=lib.sh
source "$RELEASE_DIR/scripts/lib.sh"
exec > >(tee -a "/var/log/$PROJECT-deploy.log") 2>&1

log "=== deploy $TAG ==="

# 1. Settings → .env (secrets never leave the server; the file is readable by root only)
umask 077
tmp=$(mktemp)
aws ssm get-parameters-by-path --path "$ENV_PARAM_PATH" --recursive --with-decryption \
  --query 'Parameters[].[Name,Value]' --output text |
  while IFS=$'\t' read -r name value; do echo "${name##*/}=$value"; done > "$tmp"
{
  echo "APP_VERSION=$TAG"
  echo "APP_DIR=$APP_DIR"
} >> "$tmp"
grep -q '^POSTGRES_PASSWORD=' "$tmp" || { log "settings not found under $ENV_PARAM_PATH"; exit 1; }
mv "$tmp" "$ENV_FILE"
chmod 600 "$ENV_FILE"
umask 022

# 2. Activate this release
ln -sfn "$RELEASE_DIR" "$CURRENT"
mkdir -p "$APP_DIR/letsencrypt" "$APP_DIR/acme" "$APP_DIR/backups"
DOMAIN=$(env_get APP_DOMAIN)
CERT="$APP_DIR/letsencrypt/live/$DOMAIN/fullchain.pem"
if [ -f "$CERT" ]; then export NGINX_CONF=https; else export NGINX_CONF=http; fi

# 3. Pull and start (the server pulls from ECR with its own role – no password stored)
aws ecr get-login-password | docker login --username AWS --password-stdin "$(env_get ECR_REGISTRY)" > /dev/null
compose pull --quiet
compose up -d --remove-orphans
compose up -d --force-recreate --no-deps nginx   # picks up the new Nginx config

# 4. Wait for the API
wait_for_api

# 5. First run on a new server: get the certificate, then switch Nginx to HTTPS
if [ ! -f "$CERT" ]; then
  log "requesting the HTTPS certificate for $DOMAIN"
  compose run --rm certbot certonly --webroot -w /var/www/certbot -d "$DOMAIN" \
    --agree-tos --register-unsafely-without-email --non-interactive
  export NGINX_CONF=https
  compose up -d --force-recreate --no-deps nginx
fi

# 6. Smoke test – the login page over HTTPS with a valid certificate, and HTTP → HTTPS
sleep 3
curl -fsS -o /dev/null --resolve "$DOMAIN:443:127.0.0.1" "https://$DOMAIN/login"
code=$(curl -s -o /dev/null -w '%{http_code}' -H "Host: $DOMAIN" http://127.0.0.1/login)
[ "$code" = "301" ] || { log "HTTP did not redirect to HTTPS (got $code)"; exit 1; }
log "smoke test passed"

# 7. Scheduled jobs (server time is Asia/Colombo)
cat > "/etc/cron.d/$PROJECT" <<CRON
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
0 23 * * *    root /bin/bash $CURRENT/scripts/backup.sh     >> /var/log/$PROJECT-backup.log 2>&1
17 3,15 * * * root /bin/bash $CURRENT/scripts/renew-cert.sh >> /var/log/$PROJECT-cert.log 2>&1
CRON
chmod 644 "/etc/cron.d/$PROJECT"
cat > "/etc/logrotate.d/$PROJECT" <<ROTATE
/var/log/$PROJECT-*.log {
    weekly
    rotate 8
    compress
    missingok
    notifempty
}
ROTATE
systemctl restart cron

# 8. Keep the last 3 releases; remove images unused for a week
ls -1dt "$APP_DIR"/releases/*/ | tail -n +4 | xargs -r rm -rf
docker image prune -af --filter "until=168h" > /dev/null || true

log "=== deployed $TAG ==="
