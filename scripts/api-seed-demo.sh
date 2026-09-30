#!/usr/bin/env bash
# Build the labelled demonstration release end to end.
#
#   scripts/api-seed-demo.sh
#
# Steps, in order:
#   1. create or migrate the ingestion database
#   2. seed the national operator and source registries
#   3. import data/fixtures/aloria-demo-snapshot.json (which declares its own
#      invented operator in a demoOperators block) and the rights-pending
#      companion fixture
#   4. insert the fixture's service calendars and their exceptions
#   5. publish every candidate entity except the ones the fixture holds back
#   6. compile the read-only public database and generate the release packs,
#      the GTFS feed and the trailing manifest
#   7. verify the manifest against the packs it describes
#
# Everything lands in a gitignored artifacts directory. No database, pack or
# manifest is ever committed.
#
# Environment, all optional (defaults shown). <PREFIX> is brand.json.envPrefix,
# and the legacy HODOMAP_ spelling is accepted for each one.
#   <PREFIX>_ARTIFACTS_DIR   artifacts
#   <PREFIX>_INGEST_DB_PATH  $ARTIFACTS_DIR/ingest.db
#   <PREFIX>_PUBLIC_DB_PATH  $ARTIFACTS_DIR/public.db
#   <PREFIX>_RELEASE_OUT_DIR $ARTIFACTS_DIR/releases
#   REVIEWER                 demo-seed
#   PY                       .venv/bin/python
#
# The release itself is written to $RELEASE_OUT_DIR/<brand slug>/, with
# manifest.json at its root and content-addressed files under packs/. That is
# the directory the API's <PREFIX>_RELEASE_DIR must point at, and the same
# directory the web client's data/ folder mirrors.
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"

PY="${PY:-$ROOT/.venv/bin/python}"
case "$PY" in /*) ;; *) [ -x "$ROOT/$PY" ] && PY="$ROOT/$PY" ;; esac
[ -x "$PY" ] || PY="$(command -v python3)"

PREFIX="$("$PY" -c 'import json;print(json.load(open("brand.json"))["envPrefix"])')"
SLUG="$("$PY" -c 'import json;print(json.load(open("brand.json"))["slug"])')"

# Read <PREFIX>_NAME, then HODOMAP_NAME, then the supplied default.
setting() {
  local suffix="$1" fallback="$2" branded legacy
  branded="$(eval "printf '%s' \"\${${PREFIX}_${suffix}:-}\"")"
  legacy="$(eval "printf '%s' \"\${HODOMAP_${suffix}:-}\"")"
  if [ -n "$branded" ]; then printf '%s' "$branded"
  elif [ -n "$legacy" ]; then printf '%s' "$legacy"
  else printf '%s' "$fallback"; fi
}

ARTIFACTS="$(setting ARTIFACTS_DIR "$ROOT/artifacts")"
INGEST_DB="$(setting INGEST_DB_PATH "$ARTIFACTS/ingest.db")"
PUBLIC_DB="$(setting PUBLIC_DB_PATH "$ARTIFACTS/public.db")"
RELEASE_OUT="$(setting RELEASE_OUT_DIR "$ARTIFACTS/releases")"
REVIEWER="${REVIEWER:-demo-seed}"

mkdir -p "$ARTIFACTS" "$RELEASE_OUT" "$(dirname "$INGEST_DB")" "$(dirname "$PUBLIC_DB")"

# A stale ingestion database would leave rows from an earlier fixture in the
# release, so the demonstration seed always starts from nothing.
rm -f "$INGEST_DB" "$INGEST_DB-wal" "$INGEST_DB-shm"
rm -f "$PUBLIC_DB" "$PUBLIC_DB-wal" "$PUBLIC_DB-shm"

echo "Seeding the demonstration dataset."
echo "  ingestion database: $INGEST_DB"
echo "  public database:    $PUBLIC_DB"
echo "  release directory:  $RELEASE_OUT/$SLUG"

cd "$ROOT/server/api"
PYTHONPATH=. "$PY" -m publicapi.demo_seed \
  --ingest-db "$INGEST_DB" \
  --public-db "$PUBLIC_DB" \
  --out-dir "$RELEASE_OUT" \
  --reviewer "$REVIEWER"

cd "$ROOT"
echo
echo "Demonstration release ready. Point the service at it with:"
echo "  export ${PREFIX}_PUBLIC_DB_PATH=$PUBLIC_DB"
echo "  export ${PREFIX}_RELEASE_DIR=$RELEASE_OUT/$SLUG"
echo "  export ${PREFIX}_DATA_MODE=demo"
echo
echo "This dataset is invented. Aloria does not exist and no row describes a"
echo "real departure. See data/fixtures/README.md before showing it anywhere."
