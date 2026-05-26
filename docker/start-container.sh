#!/usr/bin/env sh
set -eu

java -jar /app/gaalop-rest.jar \
  --server.port=18080 \
  --gaalop.maxima.command=/app/tools/maxima/bin/maxima &

JAVA_PID="$!"

trap 'kill "$JAVA_PID" 2>/dev/null || true; wait "$JAVA_PID" 2>/dev/null || true' INT TERM EXIT

nginx -g 'daemon off;' &
NGINX_PID="$!"

wait "$NGINX_PID"
