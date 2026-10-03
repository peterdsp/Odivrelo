#!/usr/bin/env bash
# Build a PRODUCTION release from an explicit, reviewed real dataset.
#
#   <PREFIX>_RELEASE_DATASET=/path/to/reviewed-ingest.db scripts/release-production.sh
#
# This path never runs the demonstration seeder and never falls back to it. It
# consumes a reviewed ingestion database that the acquisition and review pipeline
# already filled with real, published rows, compiles it, cuts a release stamped
# dataMode=real, and re-reads that release through the release guard. If the
# dataset is missing, empty, or still carries the demonstration operator, the run
# fails and nothing ships.
#
# Keep demonstration builds on scripts/api-seed-demo.sh. The two never share a
# default: there is no way to reach this script without naming a real dataset.
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"

PY="${PY:-$ROOT/.venv/bin/python}"
case "$PY" in /*) ;; *) [ -x "$ROOT/$PY" ] && PY="$ROOT/$PY" ;; esac
[ -x "$PY" ] || PY="$(command -v python3)"

PREFIX="$("$PY" -c 'import json;print(json.load(open("brand.json"))["envPrefix"])')"
SLUG="$("$PY" -c 'import json;print(json.load(open("brand.json"))["slug"])')"

# Read <PREFIX>_NAME, then the legacy PORAVIA_ and HODOMAP_ spellings.
setting() {
  local suffix="$1" fallback="$2" name value
  for name in "${PREFIX}" PORAVIA HODOMAP; do
    value="$(eval "printf '%s' \"\${${name}_${suffix}:-}\"")"
    if [ -n "$value" ]; then printf '%s' "$value"; return; fi
  done
  printf '%s' "$fallback"
}

ARTIFACTS="$(setting ARTIFACTS_DIR "$ROOT/artifacts")"
PUBLIC_DB="$(setting PUBLIC_DB_PATH "$ARTIFACTS/public.db")"
RELEASE_OUT="$(setting RELEASE_OUT_DIR "$ARTIFACTS/releases")"
DATASET="$(setting RELEASE_DATASET "")"

if [ -z "$DATASET" ]; then
  echo "FAIL: no reviewed real dataset." >&2
  echo "      Set ${PREFIX}_RELEASE_DATASET to the path of a reviewed ingestion" >&2
  echo "      database before building a production release. This path never" >&2
  echo "      falls back to the demonstration seeder." >&2
  exit 1
fi
if [ ! -f "$DATASET" ]; then
  echo "FAIL: ${PREFIX}_RELEASE_DATASET points at $DATASET, which does not exist." >&2
  exit 1
fi

COMMIT="${GITHUB_SHA:-$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)}"
BUILT_AT="${BUILD_TIME:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"

mkdir -p "$ARTIFACTS" "$RELEASE_OUT" "$(dirname "$PUBLIC_DB")"
# The public database is disposable and recompiled from the reviewed dataset.
rm -f "$PUBLIC_DB" "$PUBLIC_DB-wal" "$PUBLIC_DB-shm"

echo "Building the production release from a reviewed real dataset."
echo "  reviewed dataset:  $DATASET"
echo "  public database:   $PUBLIC_DB"
echo "  release directory: $RELEASE_OUT/$SLUG"

cd "$ROOT/server/api"
PYTHONPATH=. "$PY" -m publicapi.release_production \
  --ingest-db "$DATASET" \
  --public-db "$PUBLIC_DB" \
  --out-dir "$RELEASE_OUT" \
  --commit "$COMMIT" \
  --built-at "$BUILT_AT"

echo
echo "== asserting the release is genuinely real =="
PYTHONPATH=. "$PY" -m publicapi.release_guard \
  --release-dir "$RELEASE_OUT/$SLUG" --expect real

cd "$ROOT"
echo
echo "Production release ready at $RELEASE_OUT/$SLUG (dataMode real)."
