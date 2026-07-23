#!/usr/bin/env sh
set -eu

JAVA_PID=""
NGINX_PID=""

stop_processes() {
  if [ -n "$JAVA_PID" ]; then
    kill "$JAVA_PID" 2>/dev/null || true
  fi
  if [ -n "$NGINX_PID" ]; then
    kill "$NGINX_PID" 2>/dev/null || true
  fi
  wait 2>/dev/null || true
}

trap stop_processes INT TERM EXIT

java -jar /app/gaalop-rest.jar \
  --server.port=18080 \
  --gaalop.maxima.command=/app/tools/maxima/bin/maxima &
JAVA_PID="$!"

nginx -g 'daemon off;' &
NGINX_PID="$!"

while kill -0 "$JAVA_PID" 2>/dev/null && kill -0 "$NGINX_PID" 2>/dev/null; do
  sleep 1
done

if ! kill -0 "$JAVA_PID" 2>/dev/null; then
  wait "$JAVA_PID" || exit $?
  exit 1
fi

wait "$NGINX_PID"
