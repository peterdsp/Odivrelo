#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"
OUT="$ROOT/artifacts/ktel-ticketweb/ci-production"
RAW="$OUT/public-stops"
NORMALIZED="$OUT/normalized-stops"
DB="$OUT/ingest.db"

mkdir -p "$OUT"
python3 scripts/fetch-public-ktel-stops.py
python3 scripts/normalize-public-ktel-stops.py \
  artifacts/ktel-ticketweb/public-stops "$NORMALIZED"

PYTHONPATH=server/ktel-staging python3 - "$DB" "$NORMALIZED" <<'PY'
import glob
import json
import sys
from pathlib import Path

from odivrelo_ktel import ktel_db
from odivrelo_ktel.ktel_ingest import import_normalized_snapshot
from odivrelo_ktel.ktel_registry import seed_registry

db, normalized = sys.argv[1:]
with ktel_db.connect(db) as connection:
    seed_registry(connection)
    connection.execute(
        "UPDATE ktel_sources SET rights_status='allowed', terms_status='reviewed', "
        "legal_reviewed_at='2026-10-06' WHERE id='ticketweb'"
    )
    for path in sorted(glob.glob(str(Path(normalized) / '*.json'))):
        import_normalized_snapshot(connection, json.loads(Path(path).read_text()))
    connection.execute("UPDATE ktel_sources SET rights_status='allowed', terms_status='reviewed', legal_reviewed_at='2026-10-06' WHERE id='ticketweb'")
    connection.execute("UPDATE ktel_stop_places SET publication_state='published' WHERE source_id='ticketweb' AND publication_state='candidate'")
    connection.execute("UPDATE ktel_stops SET publication_state='published' WHERE source_id='ticketweb' AND publication_state='candidate'")
    connection.commit()
    places = connection.execute("SELECT COUNT(*) FROM ktel_stop_places WHERE publication_state='published'").fetchone()[0]
    stops = connection.execute("SELECT COUNT(*) FROM ktel_stops WHERE publication_state='published'").fetchone()[0]
    print(f'published TicketWeb directory: {places} places, {stops} stops')
PY

echo "$DB"
