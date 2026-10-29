#!/usr/bin/env bash
# Refresh real KTEL schedules end to end, from the live source to the clients.
#
#   scripts/refresh-ktel-schedules.sh                      # all TicketWeb tenants
#   scripts/refresh-ktel-schedules.sh --tenant arg         # one tenant
#   SKIP_FETCH=1 scripts/refresh-ktel-schedules.sh         # reuse the last fetch
#   SKIP_EXISTING=1 scripts/refresh-ktel-schedules.sh      # top up only the
#                                                          # tenants that failed
#
# Why this script exists: the shipped release carries real stops but its
# schedules are a frozen one-week snapshot, and every trip is
# `date_specific_bookable`, so a stale release cannot be re-packed into a fresh
# one. The dates have to be fetched again.
#
# The stages are:
#
#   1. fetch    bounded, read-only, paced collection of public route executions
#   2. import   normalize into a working copy of the reviewed ingestion database
#   3. release  compile and cut a release stamped dataMode=real, guard it
#   4. sync     copy that one release into the web, iOS and Android clients
#
# Nothing here seeds or falls back to demonstration data: stage 3 delegates to
# scripts/release-production.sh, which fails closed if the dataset is empty or
# still carries the demonstration operator.
#
# Only the ~26 operators present on TicketWeb can gain schedules this way. The
# other federation members publish no machine-readable timetable, so they keep
# their stop and contact records and no departures, which is the honest result.
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"

PY="${PY:-$ROOT/.venv/bin/python}"
[ -x "$PY" ] || PY="$(command -v python3)"

START_DATE="${START_DATE:-$(date +%F)}"
DAYS="${DAYS:-7}"
MAX_ORIGINS="${MAX_ORIGINS:-12}"
MAX_QUERIES="${MAX_QUERIES:-140}"
MIN_INTERVAL="${MIN_INTERVAL:-1.5}"

# The reviewed ingestion database is the input. It holds every crawled stop,
# including the candidates an execution still has to promote, which the compiled
# public artifact does not.
SOURCE_DB="${SOURCE_DB:-$ROOT/artifacts/ktel-ticketweb/ci-production/ingest.db}"
WORK_DIR="${WORK_DIR:-$ROOT/artifacts/ktel-ticketweb/refresh-$(date +%Y%m%d)}"
WORK_DB="$WORK_DIR/ingest.db"
FETCH_DIR="${FETCH_DIR:-$ROOT/artifacts/ktel-ticketweb/public-executions}"

mkdir -p "$WORK_DIR"

echo "== 1. fetch =="
if [ "${SKIP_FETCH:-0}" = "1" ]; then
  echo "  SKIP_FETCH=1, reusing $FETCH_DIR"
else
  # Paced on purpose. The recorded refresh policy for this source is "bounded
  # executions daily", so the run stays slow and small rather than fast.
  # A tenant that already produced a file is left alone when topping up, so a
  # transient 500 on one operator does not cost another full pass over all 26.
  SKIP_FLAG=()
  [ "${SKIP_EXISTING:-0}" = "1" ] && SKIP_FLAG=(--skip-existing)

  PYTHONPATH=server/ktel-staging "$PY" scripts/fetch-public-ktel-executions.py \
    "${SKIP_FLAG[@]+"${SKIP_FLAG[@]}"}" \
    --start-date "$START_DATE" \
    --days "$DAYS" \
    --max-origins "$MAX_ORIGINS" \
    --max-queries-per-tenant "$MAX_QUERIES" \
    --min-interval "$MIN_INTERVAL" \
    --output "$FETCH_DIR" \
    "$@"
fi

echo "== 2. import =="
[ -f "$SOURCE_DB" ] || { echo "missing reviewed ingestion database: $SOURCE_DB" >&2; exit 1; }
cp "$SOURCE_DB" "$WORK_DB"
# SQLite side files would otherwise carry the source's open transaction state.
rm -f "$WORK_DB-wal" "$WORK_DB-shm"
"$PY" scripts/import-public-ktel-executions.py --db "$WORK_DB" --input "$FETCH_DIR"

echo "== import result =="
sqlite3 "$WORK_DB" <<SQL
.mode column
.headers on
SELECT service_date, COUNT(*) AS trips
  FROM ktel_trips
 WHERE publication_state = 'published' AND service_date >= '$START_DATE'
 GROUP BY service_date
 ORDER BY service_date;
SELECT COUNT(DISTINCT operator_id) AS operators_with_fresh_trips
  FROM ktel_trips
 WHERE publication_state = 'published' AND service_date >= '$START_DATE';
SQL

FRESH="$(sqlite3 "$WORK_DB" "SELECT COUNT(*) FROM ktel_trips WHERE publication_state='published' AND service_date >= '$START_DATE'")"
if [ "$FRESH" -eq 0 ]; then
  echo "no trips on or after $START_DATE; refusing to cut a release" >&2
  exit 1
fi

echo "== 3. release =="
ODIVRELO_RELEASE_DATASET="$WORK_DB" bash scripts/release-production.sh

echo "== 4. sync clients =="
bash scripts/web-sync-packs.sh
# The national stops pack is one file of about 68MB, over Cloudflare Pages'
# per-file limit, so the web copy is split the same way deploy-web.yml splits
# it. Doing it here keeps the local client the same shape as the deployed one,
# which is also the shape the web tests read.
"$PY" scripts/split-web-stops-pack.py apps/web/public/data
bash scripts/ios-sync-packs.sh
bash scripts/android-sync-packs.sh

echo
echo "Refreshed. $FRESH published trips on or after $START_DATE."
echo "Working database: $WORK_DB"
