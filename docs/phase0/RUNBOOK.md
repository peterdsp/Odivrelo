# Phase 0 runbook: ingest to published release

Exact commands, in order. Verified end to end on 30 September 2026 against the
Aloria demonstration fixture. Every command is idempotent.

Prerequisites: Python 3.12 and the repository virtual environment.

```bash
cd /Users/peterdsp/git/Poravia
python3.12 -m venv .venv          # once
export PY=.venv/bin/python
export STAGING=server/ktel-staging
export ARTIFACTS="$PWD/artifacts"  # gitignored
mkdir -p "$ARTIFACTS"
```

All commands below run from `server/ktel-staging` with `PYTHONPATH=.`.

## 1. Migrate and seed the ingestion database

```bash
cd "$STAGING"
export PORAVIA_KTEL_DB_PATH="$ARTIFACTS/ingest.db"
export PORAVIA_KTEL_PUBLIC_DB_PATH="$ARTIFACTS/public.db"
PYTHONPATH=. ../../$PY scripts/ktel_pipeline.py migrate
PYTHONPATH=. ../../$PY scripts/ktel_pipeline.py seed
```

Seeding is idempotent and yields 64 operator rows, of which 62 carry a
federation number, plus five source records. Only `greek-nap-ktel`,
`openstreetmap` and `manual-review` are `rights_status = allowed`;
`poays-directory` is `unknown` and `ticketweb` is `permission_pending`. That is
the rights gate, and it is deliberate.

## 2. Import a normalized snapshot

```bash
PYTHONPATH=. ../../$PY scripts/ktel_pipeline.py import-normalized \
  --file ../../data/fixtures/aloria-demo-snapshot.json
```

Rows land as `candidate`. Nothing is public yet. Rows with invalid coordinates
are quarantined at import and counted in the run summary.

## 3. Review

```bash
PYTHONPATH=. ../../$PY scripts/ktel_pipeline.py review \
  --kind stop --id <entity-id> --action publish --reviewer <name>
```

Valid kinds: `stop_place`, `stop`, `line`, `journey_pattern`,
`service_calendar`, `trip`. Valid actions: `verify`, `publish`, `quarantine`,
`reject`, `withdraw`, `restore`. Every action writes a `ktel_review_events`
row, so the audit trail is the review. A stop whose `coordinate_status` is not
`valid` or `missing` cannot be published; the command refuses it.

## 4. Compile the public database

```bash
PYTHONPATH=. ../../$PY scripts/ktel_pipeline.py publish
```

This copies **only** rows that are both `publication_state = published` and
sourced from a `rights_status = allowed` source, into a temporary file, runs
`PRAGMA foreign_key_check`, checkpoints the WAL, keeps the previous public
database as `public-previous.db`, then atomically replaces the target. The
release id is a SHA-256 over the copied rows, so an unchanged input produces an
unchanged release id and the command becomes a no-op.

## 5. Generate the release packs and manifest

```bash
PYTHONPATH=. ../../$PY - <<'PY'
from poravia_ktel import ktel_release
import os
print(ktel_release.generate_public_release(
    os.environ["ARTIFACTS"] + "/releases",
    os.environ["PORAVIA_KTEL_DB_PATH"],
    os.environ["PORAVIA_KTEL_PUBLIC_DB_PATH"],
))
PY
```

Writes `artifacts/releases/poravia/packs/<name>-<digest16>.<ext>` and then
`artifacts/releases/poravia/manifest.json` **last**. Packs are content
addressed, so a name can never refer to different bytes. A GTFS zip is one of
the packs and is byte-reproducible.

## 6. Verify and roll back

```bash
PYTHONPATH=. ../../$PY -c "
from poravia_ktel import ktel_release
ktel_release.verify_release('$ARTIFACTS/releases/poravia')"
```

`verify_release` re-reads the manifest and recomputes every pack digest and
size. It raises on a missing pack, a corrupt pack, or a size mismatch.
`rollback_release` restores `manifest-previous.json`; packs are immutable so
nothing is deleted and the rolled-back manifest still verifies.

## 7. Query the compiled release

```bash
PYTHONPATH=. ../../$PY -c "
from poravia_ktel import ktel_api, ktel_db
with ktel_db.connect('$ARTIFACTS/public.db', read_only=True) as c:
    print(ktel_api.release_metadata(c))
    print(ktel_api.coverage_payload(c))
    print(ktel_api.trips_payload(c, service_date='2026-10-02')['total'])"
```

## 8. Tests

```bash
cd "$STAGING" && PYTHONPATH=. ../../$PY -m unittest discover -s tests -v
```

22 tests. They must all pass before a release is generated.

## Environment variables

| Name | Meaning | Legacy fallback |
|---|---|---|
| `PORAVIA_KTEL_DB_PATH` | ingestion database | `HODOMAP_KTEL_DB_PATH`, `SYRMOS_KTEL_DB_PATH` |
| `PORAVIA_KTEL_PUBLIC_DB_PATH` | compiled public database | `HODOMAP_KTEL_PUBLIC_DB_PATH`, `SYRMOS_KTEL_PUBLIC_DB_PATH` |
| `KTEL_TICKETWEB_TERMS_APPROVED` | must equal `1` before any TicketWeb request is attempted | — |

The legacy names are read as a fallback so an existing Raspberry Pi deployment
keeps working until it migrates. They are listed in the legacy allowlist in
`docs/beta/BRAND-DECISION.md`.

`KTEL_TICKETWEB_TERMS_APPROVED` is **not set anywhere in the repository** and
must stay unset until written terms approval exists.

## What this runbook does not do

It does not fetch operator data, deploy anything, or publish a real timetable.
The only dataset it compiles is the labelled Aloria demonstration fixture. See
`NAP-DATASET-EVIDENCE.md` for why no real corridor is available.
