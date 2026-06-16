#!/usr/bin/env bash
set -euo pipefail

IMAGE="${IMAGE:?IMAGE is required}"
DB_URL="${DB_URL:?DB_URL is required}"
DB_USERNAME="${DB_USERNAME:?DB_USERNAME is required}"
DB_PASSWORD="${DB_PASSWORD:?DB_PASSWORD is required}"
GEMINI_API_KEY="${GEMINI_API_KEY:?GEMINI_API_KEY is required}"
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD_HASH="${ADMIN_PASSWORD_HASH:-}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-}"

APP_NAME="inu-backend"
BLUE_NAME="${APP_NAME}-blue"
GREEN_NAME="${APP_NAME}-green"
BLUE_PORT="8081"
GREEN_PORT="8082"
PUBLIC_PORT="8080"
NGINX_CONFIG="/etc/nginx/conf.d/inu-backend.conf"

docker_cmd() {
  sudo docker "$@"
}

ensure_nginx() {
  if command -v nginx >/dev/null 2>&1; then
    return
  fi

  if command -v apt-get >/dev/null 2>&1; then
    sudo apt-get update
    sudo apt-get install -y nginx
    return
  fi

  echo "nginx is not installed and apt-get is unavailable." >&2
  exit 1
}

current_port() {
  if [ -f "$NGINX_CONFIG" ]; then
    grep -Eo '127\.0\.0\.1:[0-9]+' "$NGINX_CONFIG" | head -n 1 | cut -d ':' -f 2 || true
  fi
}

container_for_port() {
  case "$1" in
    "$BLUE_PORT") echo "$BLUE_NAME" ;;
    "$GREEN_PORT") echo "$GREEN_NAME" ;;
    *) echo "" ;;
  esac
}

health_check() {
  local port="$1"
  local label="$2"
  local i=1

  while [ "$i" -le 45 ]; do
    if curl -fsS "http://127.0.0.1:${port}/actuator/health" >/dev/null; then
      echo "${label} health check passed on port ${port}."
      return 0
    fi
    sleep 2
    i=$((i + 1))
  done

  echo "${label} health check timed out on port ${port}." >&2
  return 1
}

write_nginx_config() {
  local target_port="$1"

  sudo tee "$NGINX_CONFIG" >/dev/null <<EOF
server {
    listen ${PUBLIC_PORT};
    server_name _;

    client_max_body_size 50m;

    location / {
        proxy_pass http://127.0.0.1:${target_port};
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 120s;
    }
}
EOF
}

reload_nginx() {
  sudo nginx -t
  if sudo systemctl is-active --quiet nginx; then
    sudo systemctl reload nginx
  else
    sudo systemctl enable --now nginx
  fi
}

rollback_to_port() {
  local rollback_port="$1"
  local target_name="$2"

  echo "Rolling back nginx to port ${rollback_port}." >&2
  write_nginx_config "$rollback_port"
  reload_nginx
  docker_cmd rm -f "$target_name" >/dev/null 2>&1 || true
}

run_backend() {
  local name="$1"
  local port="$2"

  docker_cmd rm -f "$name" >/dev/null 2>&1 || true
  docker_cmd run -d \
    -p "127.0.0.1:${port}:8080" \
    --name "$name" \
    --restart unless-stopped \
    -e SPRING_DATASOURCE_URL="$DB_URL" \
    -e SPRING_DATASOURCE_USERNAME="$DB_USERNAME" \
    -e SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
    -e SPRING_JPA_HIBERNATE_DDL_AUTO=validate \
    -e SPRING_FLYWAY_ENABLED=true \
    -e SPRING_FLYWAY_BASELINE_ON_MIGRATE=true \
    -e GEMINI_API_KEY="$GEMINI_API_KEY" \
    -e ADMIN_USERNAME="$ADMIN_USERNAME" \
    -e ADMIN_PASSWORD_HASH="$ADMIN_PASSWORD_HASH" \
    -e ADMIN_PASSWORD="$ADMIN_PASSWORD" \
    -e SPRING_PROFILES_ACTIVE=prod \
    "$IMAGE"
}

ensure_nginx

ACTIVE_PORT="$(current_port)"
if [ "$ACTIVE_PORT" = "$BLUE_PORT" ]; then
  TARGET_NAME="$GREEN_NAME"
  TARGET_PORT="$GREEN_PORT"
elif [ "$ACTIVE_PORT" = "$GREEN_PORT" ]; then
  TARGET_NAME="$BLUE_NAME"
  TARGET_PORT="$BLUE_PORT"
else
  ACTIVE_PORT=""
  TARGET_NAME="$BLUE_NAME"
  TARGET_PORT="$BLUE_PORT"
fi

ACTIVE_NAME="$(container_for_port "$ACTIVE_PORT")"

echo "Pulling image ${IMAGE}."
docker_cmd pull "$IMAGE"

echo "Starting ${TARGET_NAME} on 127.0.0.1:${TARGET_PORT}."
run_backend "$TARGET_NAME" "$TARGET_PORT"

if ! health_check "$TARGET_PORT" "$TARGET_NAME"; then
  docker_cmd logs --tail=200 "$TARGET_NAME" || true
  docker_cmd rm -f "$TARGET_NAME" >/dev/null 2>&1 || true
  exit 1
fi

PREVIOUS_CONFIG=""
if [ -f "$NGINX_CONFIG" ]; then
  PREVIOUS_CONFIG="$(sudo cat "$NGINX_CONFIG")"
fi

echo "Switching nginx public port ${PUBLIC_PORT} to ${TARGET_NAME}:${TARGET_PORT}."
write_nginx_config "$TARGET_PORT"

LEGACY_WAS_RUNNING="false"
if docker_cmd ps --filter "name=^${APP_NAME}$" --filter "status=running" --format "{{.Names}}" | grep -q "^${APP_NAME}$"; then
  echo "Stopping legacy ${APP_NAME} container so nginx can bind public port ${PUBLIC_PORT}."
  docker_cmd stop "$APP_NAME"
  LEGACY_WAS_RUNNING="true"
fi

if ! reload_nginx; then
  echo "nginx reload failed." >&2
  if [ -n "$PREVIOUS_CONFIG" ]; then
    echo "$PREVIOUS_CONFIG" | sudo tee "$NGINX_CONFIG" >/dev/null
    reload_nginx || true
  elif [ "$LEGACY_WAS_RUNNING" = "true" ]; then
    sudo systemctl stop nginx >/dev/null 2>&1 || true
    docker_cmd start "$APP_NAME" >/dev/null 2>&1 || true
  fi
  docker_cmd rm -f "$TARGET_NAME" >/dev/null 2>&1 || true
  exit 1
fi

if ! health_check "$PUBLIC_PORT" "public nginx"; then
  if [ -n "$ACTIVE_PORT" ]; then
    rollback_to_port "$ACTIVE_PORT" "$TARGET_NAME"
  elif [ "$LEGACY_WAS_RUNNING" = "true" ]; then
    sudo systemctl stop nginx >/dev/null 2>&1 || true
    docker_cmd start "$APP_NAME" >/dev/null 2>&1 || true
    docker_cmd rm -f "$TARGET_NAME" >/dev/null 2>&1 || true
  else
    docker_cmd rm -f "$TARGET_NAME" >/dev/null 2>&1 || true
  fi
  exit 1
fi

if [ -n "$ACTIVE_NAME" ]; then
  echo "Stopping previous ${ACTIVE_NAME}."
  docker_cmd rm -f "$ACTIVE_NAME" >/dev/null 2>&1 || true
fi

if [ "$LEGACY_WAS_RUNNING" = "true" ]; then
  echo "Removing legacy ${APP_NAME} container after successful nginx cutover."
  docker_cmd rm -f "$APP_NAME" >/dev/null 2>&1 || true
fi

docker_cmd image prune -f
echo "Deployment completed: ${TARGET_NAME} is serving through nginx on port ${PUBLIC_PORT}."
