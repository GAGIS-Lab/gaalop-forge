#!/usr/bin/env bash
set -euo pipefail

JAR_SOURCE="${1:-./gaalop-rest-1.0.0.jar}"
APP_DIR="/home/GACRAC"
APP_USER="GACRAC"

if [[ ! -f "$JAR_SOURCE" ]]; then
  echo "Jar not found: $JAR_SOURCE" >&2
  exit 1
fi

if ! id "$APP_USER" >/dev/null 2>&1; then
  echo "User not found: $APP_USER" >&2
  echo "Create the user first, or edit APP_USER in this script." >&2
  exit 1
fi

mkdir -p "$APP_DIR/logs" "$APP_DIR/compile-history"
cp "$JAR_SOURCE" "$APP_DIR/gaalop-rest-1.0.0.jar"
cp gaalop-rest.service /etc/systemd/system/gaalop-rest.service
cp gaalop-rest-healthcheck.service /etc/systemd/system/gaalop-rest-healthcheck.service
cp gaalop-rest-healthcheck.timer /etc/systemd/system/gaalop-rest-healthcheck.timer
cp gaalop-rest-healthcheck.sh /usr/local/bin/gaalop-rest-healthcheck.sh
chmod +x /usr/local/bin/gaalop-rest-healthcheck.sh
chown -R "$APP_USER:$APP_USER" "$APP_DIR"

systemctl daemon-reload
systemctl enable --now gaalop-rest.service
systemctl enable --now gaalop-rest-healthcheck.timer

systemctl status gaalop-rest.service --no-pager
