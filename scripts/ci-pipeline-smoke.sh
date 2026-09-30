#!/usr/bin/env bash
# Prove the whole data path runs: migrate, seed, import, review, publish,
# generate packs, verify. Produces artifacts/releases/poravia/.
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"
PY="${PY:-python3}"
# Resolve a relative interpreter against the repository root, since the script
# changes directory below.
case "$PY" in /*) ;; *) [ -x "$ROOT/$PY" ] && PY="$ROOT/$PY" ;; esac
ARTIFACTS="$ROOT/artifacts"
rm -rf "$ARTIFACTS/ingest.db" "$ARTIFACTS/public.db" "$ARTIFACTS/releases"
mkdir -p "$ARTIFACTS/releases"

export PORAVIA_KTEL_DB_PATH="$ARTIFACTS/ingest.db"
export PORAVIA_KTEL_PUBLIC_DB_PATH="$ARTIFACTS/public.db"

FIXTURE="$ROOT/data/fixtures/aloria-demo-snapshot.json"
if [ ! -f "$FIXTURE" ]; then
  echo "FAIL: demonstration fixture missing at $FIXTURE" >&2
  exit 1
fi

cd server/ktel-staging
PYTHONPATH=. "$PY" - "$FIXTURE" "$ARTIFACTS/releases" <<'PYCODE'
import json, os, sys
from hodomap_ktel import ktel_db, ktel_release
from hodomap_ktel.ktel_ingest import import_normalized_snapshot, review_entity
from hodomap_ktel.ktel_registry import seed_registry

fixture_path, releases_dir = sys.argv[1], sys.argv[2]
ingest = os.environ["PORAVIA_KTEL_DB_PATH"]
public = os.environ["PORAVIA_KTEL_PUBLIC_DB_PATH"]

conn = ktel_db.connect(ingest)
ktel_db.migrate(conn)
seed_registry(conn)

payload = json.loads(open(fixture_path, encoding="utf-8").read())
for snapshot in (payload if isinstance(payload, list) else [payload]):
    summary = import_normalized_snapshot(conn, snapshot)
    print("imported:", summary)

for kind, table in (
    ("stop_place", "ktel_stop_places"),
    ("stop", "ktel_stops"),
    ("line", "ktel_lines"),
    ("journey_pattern", "ktel_journey_patterns"),
    ("service_calendar", "ktel_service_calendars"),
    ("trip", "ktel_trips"),
):
    for row in conn.execute(
        f"SELECT id FROM {table} WHERE publication_state='candidate'"
    ).fetchall():
        try:
            review_entity(conn, entity_kind=kind, entity_id=row["id"],
                          action="publish", reviewer="ci")
        except ValueError as exc:
            # A quarantined coordinate must NOT become publishable. Refusal is
            # the correct behaviour, so record it and carry on.
            print(f"  review refused {kind} {row['id']}: {exc}")
conn.commit()
conn.close()

result = ktel_release.generate_public_release(releases_dir, ingest, public)
print("release:", result["releaseId"], "packs:", len(result["files"]))
manifest = ktel_release.verify_release(os.path.join(releases_dir, "poravia"))
assert manifest["releaseId"] == result["releaseId"], "manifest release mismatch"
print("verified", len(manifest["files"]), "packs against the manifest")
PYCODE
echo "Pipeline smoke passed."
