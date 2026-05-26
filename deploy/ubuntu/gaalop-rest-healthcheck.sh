#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="gaalop-rest.service"
HEALTH_URL="http://127.0.0.1:8080/api/v1/health"
TIMEOUT_SECONDS=5

if ! systemctl is-active --quiet "$SERVICE_NAME"; then
  systemctl restart "$SERVICE_NAME"
  exit 0
fi

if ! curl --fail --silent --show-error --max-time "$TIMEOUT_SECONDS" "$HEALTH_URL" >/dev/null; then
  systemctl kill --signal=SIGKILL "$SERVICE_NAME" || true
  systemctl restart "$SERVICE_NAME"
fi
