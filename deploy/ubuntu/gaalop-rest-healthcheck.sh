#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="gaalop-rest.service"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/gaalop/api/v1/health}"
TIMEOUT_SECONDS=5
STARTUP_GRACE_SECONDS=90
MAX_ATTEMPTS=3

log() {
  printf 'gaalop-rest-healthcheck: %s\n' "$*" >&2
}

# Read the activation identity as well as state so a concurrent stop/restart
# cannot cause this probe to restart a different service invocation.
read_service_state() {
  local properties key value
  properties=$(systemctl show "$SERVICE_NAME" --property=ActiveState \
    --property=ActiveEnterTimestampMonotonic --property=InvocationID) || return 1
  active_state=""
  active_since=""
  invocation_id=""
  while IFS='=' read -r key value; do
    case "$key" in
      ActiveState) active_state="$value" ;;
      ActiveEnterTimestampMonotonic) active_since="$value" ;;
      InvocationID) invocation_id="$value" ;;
    esac
  done <<< "$properties"
  if [[ "$active_state" == "active" ]]; then
    [[ "$active_since" =~ ^[0-9]+$ && -n "$invocation_id" ]]
  else
    [[ -n "$active_state" ]]
  fi
}

if ! read_service_state; then
  log "Cannot read service state; leaving $SERVICE_NAME unchanged."
  exit 1
fi

# systemd already handles process failures. Do not undo an intentional stop or
# interfere with an activation/deactivation already in progress.
if [[ "$active_state" != "active" ]]; then
  exit 0
fi

read -r uptime_seconds _ < /proc/uptime
service_age=$(( ${uptime_seconds%%.*} - active_since / 1000000 ))
if (( service_age < STARTUP_GRACE_SECONDS )); then
  exit 0
fi
initial_active_since="$active_since"
initial_invocation_id="$invocation_id"

for ((attempt = 1; attempt <= MAX_ATTEMPTS; attempt++)); do
  if http_status=$(curl --silent --show-error --output /dev/null \
    --write-out '%{http_code}' --connect-timeout "$TIMEOUT_SECONDS" \
    --max-time "$TIMEOUT_SECONDS" "$HEALTH_URL"); then
    case "$http_status" in
      2??) exit 0 ;;
      5??) reason="HTTP $http_status from $HEALTH_URL" ;;
      *)
        log "HTTP $http_status from $HEALTH_URL; check the health URL, context path and access configuration. Service will not be restarted."
        exit 1
        ;;
    esac
  else
    reason="curl exit $? contacting $HEALTH_URL"
  fi
  log "Health probe $attempt/$MAX_ATTEMPTS failed: $reason."
  if (( attempt < MAX_ATTEMPTS )); then
    sleep 2
  fi
done

if ! read_service_state; then
  log "Cannot recheck service state; leaving $SERVICE_NAME unchanged."
  exit 1
fi
if [[ "$active_state" != "active" || "$active_since" != "$initial_active_since" ||
      "$invocation_id" != "$initial_invocation_id" ]]; then
  log "Service changed during the health probes; skipping restart."
  exit 0
fi

log "Restarting $SERVICE_NAME after $MAX_ATTEMPTS failed health probes: $reason."
# try-restart also respects a stop occurring after the final state check.
systemctl try-restart "$SERVICE_NAME"
