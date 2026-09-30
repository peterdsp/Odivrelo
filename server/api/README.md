# Public API service

The read-only public API and the private administrative review surface.
Implements version 1 of `data/schemas/CONTRACT-v1.md`.

The Python package is called `publicapi` and carries no product name. Identity
is read from `brand.json` at import time, so a rename is a one-file change.

## Design rules this service holds to

- **The public surface reads only the compiled public database**, opened
  read-only, one connection per request. The ingestion database is never opened
  by a public route. There is no module-level connection and no mutable global
  state, so an atomic release swap under a running process is safe: in-flight
  requests finish against the file they opened.
- **Two gates are re-applied on every read.** Every query filters
  `publication_state='published'` and joins the owning source for
  `rights_status='allowed'`, even though the compiler already applied both. A
  regression in the compiler cannot become a rights leak in a response.
- **The admin surface is a separate router in a separate module.** With
  `ADMIN_ENABLED` off it is not mounted, not imported, and absent from the
  served OpenAPI document. A public deployment does not merely refuse admin
  requests: it has no admin routes.
- **Nothing is reimplemented.** Compilation, GTFS export, release generation,
  manifest verification and rollback are the compiled-data layer's
  `ktel_publish`, `ktel_gtfs` and `ktel_release` modules, in
  `server/ktel-staging/`, called as they stand.
- **There is one generator of offline packs, and packs carry the contract
  shape.** `publicapi/packs.py` builds every pack from the same functions in
  `publicapi/repository.py` that the endpoints serve from, and is passed to
  `ktel_release.generate_public_release` as its payload provider. A pack holds
  exactly what the matching `/v1/...` endpoint would have returned, so an
  offline client and an online client cannot disagree. The release mechanism is
  not forked: content addressing, digests, the trailing manifest, the retained
  previous manifest and `verify_release` stay where they are.

## How the compiled-data layer is imported

`server/ktel-staging` carries no packaging metadata, so there is nothing to
`pip install -e`. `publicapi/_staging.py` is a small bootstrap that puts that
directory on `sys.path` once, before anything imports from it, and raises with a
clear message if it is not there. It also maps `brand.json` onto the `PRODUCT_*`
variables that the layer's `branding` module reads at import time, so a rename
reaches the release directory name, the GTFS feed publisher and the attribution
rows.

Override the location with `<PREFIX>_KTEL_STAGING_PATH` if the staging tree is
installed elsewhere.

## Running locally

```bash
# 1. Build a demonstration release into a gitignored artifacts directory.
scripts/api-seed-demo.sh

# 2. Point the service at it. The script prints these three lines for you.
export PORAVIA_PUBLIC_DB_PATH="$PWD/artifacts/public.db"
export PORAVIA_RELEASE_DIR="$PWD/artifacts/releases/poravia"
export PORAVIA_DATA_MODE=demo

# 3. Run it.
scripts/api-run.sh                  # 127.0.0.1:8080
scripts/api-run.sh --reload         # development reload

# 4. Check it.
curl -fsS localhost:8080/healthz
curl -fsS localhost:8080/readyz
curl -fsS 'localhost:8080/v1/places?q=aloria'
```

`Procfile` carries the same command for a platform that reads one.

## Environment variables

Names only. No value in this file, in a log line, or in any error message.

`<PREFIX>` is `brand.json.envPrefix`. Every variable also accepts the documented
legacy `HODOMAP_` spelling; the branded name wins when both are set. The service
validates everything at startup and refuses to run with a clear message listing
every problem at once.

### Required

| Variable | Meaning |
| --- | --- |
| `<PREFIX>_PUBLIC_DB_PATH` | The compiled read-only public database. |
| `<PREFIX>_RELEASE_DIR` | The release directory holding `manifest.json` and `packs/`. Its basename must equal `brand.json.slug`, because that is what the release generator writes. |
| `<PREFIX>_DATA_MODE` | `real` or `demo`. **No default.** Serving invented timetables as real data is the most damaging mistake this service can make, so it must be stated. |

### Optional

| Variable | Default | Meaning |
| --- | --- | --- |
| `<PREFIX>_ADMIN_ENABLED` | `false` | Mount the administrative router. Keep it off on the public origin. |
| `<PREFIX>_CORS_ALLOWED_ORIGINS` | empty | Comma-separated explicit allowlist. CORS is off when empty. `*` is refused at startup. |
| `<PREFIX>_HSTS_MAX_AGE` | `63072000` | `0` omits the header, for a plain-HTTP mount behind a terminating proxy. |
| `<PREFIX>_RELEASE_JSON_MAX_AGE` | `60` | Freshness window for release-scoped JSON. Packs are always immutable. |
| `<PREFIX>_LOG_LEVEL` | `INFO` | `DEBUG`, `INFO`, `WARNING`, `ERROR` or `CRITICAL`. |
| `<PREFIX>_COMMIT` | `unknown` | Reported in `/v1/meta`. |
| `<PREFIX>_BUILT_AT` | `unknown` | Reported in `/v1/meta`. |
| `<PREFIX>_KTEL_STAGING_PATH` | repo path | The directory holding the compiled-data layer package. |
| `BRAND_JSON_PATH` | repo root | Where `brand.json` lives. |

### Required only when the admin surface is enabled

| Variable | Meaning |
| --- | --- |
| `ADMIN_API_TOKEN` | Bearer token, at least 32 characters. The service **refuses to start** with the admin surface enabled and no token. Compared in constant time and never logged. |
| `<PREFIX>_INGEST_DB_PATH` | The ingestion database the review and publish routes act on. |
| `<PREFIX>_ADMIN_DB_PATH` | This service's own operational store: audit trail, corrections, takedowns. |

### Server process

`PORT`, `HOST`, `WEB_CONCURRENCY`, `KEEP_ALIVE`, `GRACEFUL_TIMEOUT` and
`FORWARDED_ALLOW_IPS` are read by `scripts/api-run.sh`, not by the application.

## Endpoints

Public, all `GET`:

| Path | Purpose |
| --- | --- |
| `/v1/meta` | Release and product metadata, coverage headline, attribution. |
| `/v1/places` | Place disambiguation, case- and accent-insensitive across `el` and `en`. |
| `/v1/journeys` | Journey search for a service date. |
| `/v1/journeys/{journey_id}` | Journey detail: stops, geometry, provenance, purchase, restrictions. |
| `/v1/operators` | Operators carrying published, rights-cleared content. |
| `/v1/operators/{operator_id}` | One operator. |
| `/v1/stops/{stop_id}` | Station page, including next departures for a date. |
| `/v1/coverage` | Coverage, freshness and an explicit statement of what is not covered. |
| `/v1/sources` | Source registry with rights status and licence per source. |
| `/v1/offline/manifest` | The generated release manifest, byte-identical to the file on disk. |
| `/v1/offline/packs/{filename}` | A content-addressed pack, served byte-for-byte. |
| `/v1/gtfs` | The reviewed GTFS zip for the current release. |
| `/openapi.json` | The served OpenAPI 3.1 document. |

Operations: `/healthz`, `/readyz`.

Private, under `/admin`, all requiring `Authorization: Bearer`:

| Method and path | Purpose |
| --- | --- |
| `GET /admin/review/queue` | Entities awaiting a decision. |
| `POST /admin/review/{entity_kind}/{entity_id}` | Record a review decision. |
| `GET /admin/audit` | The administrative audit trail. |
| `POST /admin/corrections` | Record a correction report. |
| `GET /admin/corrections` | List correction reports. |
| `POST /admin/corrections/{correction_id}` | Resolve a correction report. |
| `POST /admin/takedown` | Withdraw an entity on request. |
| `GET /admin/takedowns` | List takedown records. |
| `GET /admin/releases` | Release state as this process sees it. |
| `POST /admin/releases/publish` | Compile and publish a new release. |
| `POST /admin/releases/rollback` | Restore the previous release. |

## Caching

- **Content-addressed packs and the GTFS feed**: strong `ETag` equal to the
  manifest digest, and `Cache-Control: public, max-age=31536000, immutable`. The
  file name contains the digest, so the bytes can never change under a name.
- **Release-scoped JSON**: strong `ETag` over the response body and a short
  `max-age` with `must-revalidate`, so a client revalidates cheaply after a
  publish. Every one of these endpoints answers `304` to `If-None-Match`.
- **Admin**: `Cache-Control: no-store` on every response.

## Health checks

`GET /healthz` is process liveness. It touches no database, because a bad
release must take a process out of rotation rather than have it killed.

`GET /readyz` is release readiness and returns `503` unless all of these hold:

| Check | Fails when |
| --- | --- |
| `publicDatabase` | the file is missing or cannot be opened |
| `integrity` | `PRAGMA quick_check` does not return `ok` |
| `publishedRelease` | the database carries no published release |
| `dataMode` | `DATA_MODE` is `real` but a published stop fails the real-data coordinate gate |
| `manifest` | `ktel_release.verify_release` rejects the manifest, a pack is missing, or a pack's digest or size does not match |
| `releaseAgreement` | the manifest and the database name different releases |

Point a load balancer at `/readyz` and a process supervisor at `/healthz`.

## Deployment

1. Run the public origin with `ADMIN_ENABLED` unset or `false`. The admin router
   is then not mounted at all.
2. If an administrative surface is needed, run a **second** process, on a
   separate origin that is not publicly routable, with `ADMIN_ENABLED=true`,
   `ADMIN_API_TOKEN`, `<PREFIX>_INGEST_DB_PATH` and `<PREFIX>_ADMIN_DB_PATH`.
3. Terminate TLS in front and forward `X-Forwarded-*`. Set `FORWARDED_ALLOW_IPS`
   to the proxy address, never `*` on a public origin.
4. Keep `<PREFIX>_CORS_ALLOWED_ORIGINS` to the exact web origins. Never `*`; the
   service refuses it.
5. Publishing a release writes a new content-addressed pack set and replaces the
   manifest last, so a reader never sees a manifest pointing at a half-written
   file. Reload is not required; the next request opens the new database.
6. Bundle the same release into the clients rather than generating a second one:
   ```bash
   scripts/web-sync-packs.sh        # -> apps/web/public/data/
   scripts/android-sync-packs.sh    # -> apps/android/src/main/assets/release/
   ```
   Both verify the release before and after copying, and both are byte-for-byte
   what `/v1/offline/manifest` and `/v1/offline/packs/{filename}` serve. The
   copied directories are build output and are gitignored.

## Offline packs

`data/schemas/CONTRACT-v1.md` fixes the permitted logical pack names, and
`publicapi/packs.py` refuses to publish any other. A release carries `meta`,
`coverage`, `sources`, `places`, `operators`, `stops`, `gtfs`, and one
`journeys-<serviceDate>` pack per materialised service date.

A release materialises a journey pack for every service date it declares: the
service date of each date-specific journey, the template date of each
calendar-backed journey, and every date an `added` calendar exception names.
Further recurrences of a weekday calendar are resolved by `/v1/journeys` on
request and are deliberately not materialised, because a year of packs per
calendar is not a release. A client asking offline for a date with no pack has no
cached answer and must say so rather than report no service.

## Release rollback

Two independent mechanisms, for two different situations.

### A bad release that was just published

```bash
curl -fsS -X POST -H "Authorization: Bearer $ADMIN_API_TOKEN" \
  https://admin.internal/admin/releases/rollback
```

This restores the previous manifest **and** swaps the previous compiled database
back with it. Rolling back the manifest alone would leave the API serving rows
from the newer release behind an older manifest, which is the mixed snapshot the
release contract forbids. The swap is two-way, so the rollback can itself be
rolled forward. Confirm with `/readyz` and `/v1/offline/manifest`.

### A release that is gone, corrupt, or from further back

Use a snapshot. A snapshot always captures the compiled database and the
manifest that describes it together, with every pack the manifest lists and the
release id all three agree on.

```bash
# Take one. Writes ops/backups/release-<timestamp>.tar.gz by default.
scripts/api-backup.sh create

# See what a snapshot holds, without unpacking it.
scripts/api-backup.sh describe ops/backups/release-20260930T021617Z.tar.gz

# List what is on disk.
scripts/api-backup.sh list
```

**Restore procedure**

1. Take the process out of rotation. `/readyz` will fail during the swap and a
   load balancer should stop sending traffic before, not because of, the restore.
2. Identify the snapshot: `scripts/api-backup.sh describe <archive>` prints the
   release id, when it was taken, and how many packs it holds.
3. Restore it:
   ```bash
   scripts/api-backup.sh restore ops/backups/release-20260930T021617Z.tar.gz
   ```
   Everything is verified in a temporary directory first: the archived database
   against its recorded digest, the manifest against its packs, and the release
   id against the snapshot metadata. A corrupt archive is refused before
   anything live is touched. Each file is then written to a sidecar and renamed
   into place, so a reader sees either the old file or the new one.
4. The command fails non-zero unless the restored database **and** the restored
   manifest both carry the snapshot's release id. A restore that cannot
   reproduce the release id is not a restore.
5. Confirm and return to rotation:
   ```bash
   curl -fsS localhost:8080/readyz | grep '"status": *"ready"'
   curl -fsS localhost:8080/v1/offline/manifest | grep releaseId
   ```

A restored release serves byte-identical responses with identical ETags, which is
covered by `tests/test_backup.py::test_a_restored_release_serves_identical_responses`.

## Logging

One JSON object per line on stdout, bounded. The formatter caps every field, caps
the whole record, and replaces any field whose name looks like a credential.
Request bodies are never logged and neither is the `Authorization` header. Each
request gets an `X-Request-Id`, echoed if the client supplies one.

## Where the contract's presentation-only fields live

The v1 contract exposes facts the staging schema had no column for: a third
display language, the boarding bay, reviewed step-free boarding, boarding
instructions, the purchase action and journey restrictions. Migration
`0002_public_attributes.sql` adds one nullable JSON object column,
`public_attributes`, to `ktel_operators`, `ktel_stop_places`, `ktel_stops` and
`ktel_trips`. Reviewed imports write it, the publication compiler copies it
verbatim, and `publicapi/attributes.py` is a strict typed reader on the way out:
an unexpected shape yields the safe default rather than leaking a raw blob into a
public response.

Service calendars and their exceptions are inserted by `publicapi/demo_seed.py`,
because the normalized snapshot importer has no calendar section yet. They carry
their real `source_id`, start as candidates, and reach `published` only through
`ktel_ingest.review_entity`, like every other entity.

## Tests

```bash
cd server/api
PYTHONPATH=. ../../.venv/bin/python -m pytest tests -q
```

The suite seeds a real demonstration release once per session and asserts against
it, so every test runs against an artifact produced the way production produces
one.

The compiled-data layer keeps its own suite, which must stay green:

```bash
cd server/ktel-staging
PYTHONPATH=. ../../.venv/bin/python -m unittest discover -s tests
```

## Dependencies

`requirements.txt` for runtime, `requirements-dev.txt` for tests. Both pin exact
versions.

```bash
.venv/bin/pip install -r server/api/requirements-dev.txt
```
