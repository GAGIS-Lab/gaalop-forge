#!/usr/bin/env bash
set -euo pipefail

# Run without root or a live service: systemctl, curl and sleep are all mocked.
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
HEALTHCHECK="$SCRIPT_DIR/../gaalop-rest-healthcheck.sh"
TEMP_ROOT=$(cd -- "${TMPDIR:-/tmp}" && pwd -P)
TEST_DIR=$(mktemp -d "$TEMP_ROOT/gaalop-healthcheck-test.XXXXXX")
cleanup() {
  case "$TEST_DIR" in
    "$TEMP_ROOT"/gaalop-healthcheck-test.*) rm -rf -- "$TEST_DIR" ;;
  esac
}
trap cleanup EXIT
mkdir "$TEST_DIR/bin"
export MOCK_DIR="$TEST_DIR"
export PATH="$TEST_DIR/bin:$PATH"

cat > "$TEST_DIR/bin/systemctl" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
case "$1" in
  show)
    count=$(cat "$MOCK_DIR/show-count")
    printf '%s\n' "$((count + 1))" > "$MOCK_DIR/show-count"
    if ((count == 0)); then
      cat "$MOCK_DIR/state"
    else
      cat "$MOCK_DIR/later-state"
    fi
    ;;
  try-restart) printf '%s\n' "$*" >> "$MOCK_DIR/restarts" ;;
  *) printf 'Unexpected systemctl command: %s\n' "$*" >&2; exit 99 ;;
esac
MOCK

cat > "$TEST_DIR/bin/curl" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
count=$(cat "$MOCK_DIR/curl-count")
printf '%s\n' "$((count + 1))" > "$MOCK_DIR/curl-count"
printf '%s\n' "${!#}" > "$MOCK_DIR/last-url"
read -r status result < <(sed -n "$((count + 1))p" "$MOCK_DIR/responses")
printf '%s' "$status"
exit "$result"
MOCK

cat > "$TEST_DIR/bin/sleep" <<'MOCK'
#!/usr/bin/env bash
exit 0
MOCK
chmod +x "$TEST_DIR/bin/"*

write_state() {
  printf 'ActiveState=%s\nActiveEnterTimestampMonotonic=%s\nInvocationID=%s\n' \
    "$1" "$2" "$3"
}

reset_case() {
  unset HEALTH_URL
  printf '0\n' > "$TEST_DIR/show-count"
  printf '0\n' > "$TEST_DIR/curl-count"
  : > "$TEST_DIR/restarts"
  : > "$TEST_DIR/responses"
  write_state active 1000000 initial > "$TEST_DIR/state"
  cp "$TEST_DIR/state" "$TEST_DIR/later-state"
}

passed=0
check_case() {
  local name="$1" expected_probes="$2" expected_restarts="$3" expected_exit="$4" actual_exit=0
  bash "$HEALTHCHECK" > "$TEST_DIR/output" 2>&1 || actual_exit=$?
  if [[ "$(cat "$TEST_DIR/curl-count")" != "$expected_probes" ||
        "$(wc -l < "$TEST_DIR/restarts" | tr -d '[:space:]')" != "$expected_restarts" ||
        "$actual_exit" != "$expected_exit" ]]; then
    printf 'FAIL: %s (probes=%s, restarts=%s, exit=%s)\n' "$name" \
      "$(cat "$TEST_DIR/curl-count")" "$(cat "$TEST_DIR/restarts")" "$actual_exit" >&2
    cat "$TEST_DIR/output" >&2
    exit 1
  fi
  printf 'PASS: %s\n' "$name"
  passed=$((passed + 1))
}

reset_case
printf '200 0\n' > "$TEST_DIR/responses"
check_case 'healthy service' 1 0 0
[[ "$(cat "$TEST_DIR/last-url")" == 'http://127.0.0.1:8080/gaalop/api/v1/health' ]]

reset_case
export HEALTH_URL='http://127.0.0.1:9090/custom/api/v1/health'
printf '200 0\n' > "$TEST_DIR/responses"
check_case 'configured health URL' 1 0 0
[[ "$(cat "$TEST_DIR/last-url")" == "$HEALTH_URL" ]]

for code in 301 401 403 404; do
  reset_case
  printf '%s 0\n' "$code" > "$TEST_DIR/responses"
  check_case "HTTP $code configuration error does not restart" 1 0 1
  grep -q 'Service will not be restarted' "$TEST_DIR/output"
done

reset_case
printf '503 0\n503 0\n200 0\n' > "$TEST_DIR/responses"
check_case 'temporary server failures recover' 3 0 0

reset_case
printf '000 7\n200 0\n' > "$TEST_DIR/responses"
check_case 'temporary transport failure recovers' 2 0 0

reset_case
printf '503 0\n503 0\n503 0\n' > "$TEST_DIR/responses"
check_case 'persistent server failures restart once' 3 1 0
grep -q 'HTTP 503' "$TEST_DIR/output"

reset_case
printf '000 7\n000 7\n000 7\n' > "$TEST_DIR/responses"
check_case 'persistent transport failures restart once' 3 1 0
grep -q 'curl exit 7' "$TEST_DIR/output"

for state in inactive activating deactivating; do
  reset_case
  write_state "$state" 0 '' > "$TEST_DIR/state"
  check_case "leave $state service unchanged" 0 0 0
done

reset_case
read -r uptime_seconds _ < /proc/uptime
write_state active "$(( ${uptime_seconds%%.*} * 1000000 ))" new > "$TEST_DIR/state"
check_case 'startup grace period' 0 0 0

reset_case
printf '503 0\n503 0\n503 0\n' > "$TEST_DIR/responses"
write_state active 2000000 replacement > "$TEST_DIR/later-state"
check_case 'concurrent restart is not restarted again' 3 0 0

reset_case
printf '503 0\n503 0\n503 0\n' > "$TEST_DIR/responses"
write_state inactive 1000000 initial > "$TEST_DIR/later-state"
check_case 'concurrent stop is not undone' 3 0 0

printf '%s healthcheck scenarios passed.\n' "$passed"
