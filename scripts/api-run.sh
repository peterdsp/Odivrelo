#!/usr/bin/env bash
# Run the public API service with production-shaped worker and timeout settings.
#
#   scripts/api-run.sh                  # bind 127.0.0.1:8080
#   PORT=9000 scripts/api-run.sh
#   scripts/api-run.sh --reload         # local development
#
# The service validates its environment at startup and refuses to run with a
# missing or invalid variable, so a misconfiguration fails here rather than
# halfway through serving traffic. Required variables are listed in
# server/api/README.md; this script never prints a value.
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"

PY="${PY:-$ROOT/.venv/bin/python}"
case "$PY" in /*) ;; *) [ -x "$ROOT/$PY" ] && PY="$ROOT/$PY" ;; esac
[ -x "$PY" ] || PY="$(command -v python3)"

PREFIX="$("$PY" -c 'import json;print(json.load(open("brand.json"))["envPrefix"])')"

HOST="${HOST:-127.0.0.1}"
PORT="${PORT:-8080}"
# One worker per core is wasteful for a read-only SQLite service whose requests
# are short and I/O bound. Two is enough to survive a slow client without
# multiplying the page cache.
WORKERS="${WEB_CONCURRENCY:-2}"
# Keep-alive shorter than any sane proxy idle timeout, so the proxy closes first.
KEEP_ALIVE="${KEEP_ALIVE:-15}"
GRACEFUL="${GRACEFUL_TIMEOUT:-20}"

EXTRA=()
RELOAD=0
for argument in "$@"; do
  case "$argument" in
    --reload) RELOAD=1 ;;
    *) EXTRA+=("$argument") ;;
  esac
done

cd "$ROOT/server/api"
export PYTHONPATH="${PYTHONPATH:+$PYTHONPATH:}."
export PYTHONUNBUFFERED=1

echo "Starting the ${PREFIX} public API on ${HOST}:${PORT}" >&2

if [ "$RELOAD" -eq 1 ]; then
  exec "$PY" -m uvicorn publicapi.app:build \
    --factory --host "$HOST" --port "$PORT" --reload \
    --timeout-keep-alive "$KEEP_ALIVE" \
    --no-server-header --log-level info "${EXTRA[@]+"${EXTRA[@]}"}"
fi

exec "$PY" -m uvicorn publicapi.app:build \
  --factory \
  --host "$HOST" \
  --port "$PORT" \
  --workers "$WORKERS" \
  --timeout-keep-alive "$KEEP_ALIVE" \
  --timeout-graceful-shutdown "$GRACEFUL" \
  --proxy-headers \
  --forwarded-allow-ips "${FORWARDED_ALLOW_IPS:-127.0.0.1}" \
  --no-server-header \
  --access-log \
  --log-level warning \
  "${EXTRA[@]+"${EXTRA[@]}"}"
