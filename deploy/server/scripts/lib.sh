#!/usr/bin/env bash
# Shared helpers for the server scripts. /etc/<project>-deploy.env is written by the Terraform start-up script
# (AWS_REGION, ENV_PARAM_PATH, BACKUP_BUCKET, RELEASES_BUCKET, APP_DIR).

set -euo pipefail

DEPLOY_ENV=$(ls /etc/*-deploy.env | head -n 1)
# shellcheck disable=SC1090
source "$DEPLOY_ENV"
export AWS_REGION AWS_DEFAULT_REGION="$AWS_REGION"
export HOME="${HOME:-/root}"     # not set when SSM runs the script (docker login stores its token there)

PROJECT=$(basename "$APP_DIR")   # thuhina
ENV_FILE="$APP_DIR/.env"         # written by deploy.sh from SSM Parameter Store (mode 600)
CURRENT="$APP_DIR/current"       # symlink to the active release folder

log() { echo "[$(date '+%F %T')] $*"; }

# docker compose for this app, whatever the current directory is.
compose() {
  docker compose --project-name "$PROJECT" --project-directory "$CURRENT" \
    -f "$CURRENT/compose.yml" --env-file "$ENV_FILE" "$@"
}

# One value from .env (not "source": JAVA_OPTS contains spaces).
env_get() {
  grep -m1 "^$1=" "$ENV_FILE" | cut -d= -f2-
}

# Waits until the API reports UP (asked from the web container – the API port is not published).
wait_for_api() {
  local i
  for i in $(seq 1 60); do
    if compose exec -T web wget -qO- http://api:8080/api/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
      log "api is UP"
      return 0
    fi
    sleep 5
  done
  log "api did not become healthy in 5 minutes – last log lines:"
  compose logs --tail 80 api || true
  return 1
}
