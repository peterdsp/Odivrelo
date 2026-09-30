# Syrmos National KTEL Data Platform

> **Historical document.** This records a decision made under the product's
> former name, HodoMap, which was rejected on 30 September 2026 and replaced
> by Poravia, itself renamed **Odivrelo** on 1 October 2026. The text below is
> preserved as written, including the old names, because rewriting a dated
> decision would falsify the record. See [the brand decision](../docs/beta/BRAND-DECISION.md).


Status date: 26 July 2026
Implementation status: national registry and data platform foundation complete, national timetable population partial
Runtime target: Raspberry Pi, FastAPI, SQLite, nginx, systemd

## Outcome

Syrmos now has an isolated national coach data platform that can represent all
62 official intercity KTEL operators without forcing coach services into the
rail model.

The implementation includes:

- A dated registry of all 62 POAYS intercity operators.
- Two additional verified TicketWeb tenants that are not in the POAYS 62.
- Verified TicketWeb coverage metadata for 26 official operators.
- Explicit `unverified` status for the remaining 36 official operators.
- The observed 5,378 TicketWeb stop groups, 19,872 stop records, and 511
  web-active stop counts, labelled as raw inventory counts.
- An isolated `ktel.db` ingestion and review database.
- A disposable, atomically compiled `ktel-public.db`.
- Row-level source, rights, retention, validity, and publication controls.
- A normalized coach model for places, physical stops, lines, journey
  patterns, calendars, trips, stop times, fares, and geometry.
- A read-only API namespace under `/api/v1/coaches`.
- Immutable release-addressed JSON packs with SHA-256 hashes.
- A release gate that refuses API reads if the public DB and manifest differ.
- A bounded TicketWeb query planner that uses `reachableStops`, never all
  possible stop pairs.
- Conservative request budgets, pacing, jitter, response-size limits, and
  in-memory caching by default.
- Automatic coordinate quarantine.
- A normalized adapter import contract and explicit manual review workflow.
- A Greek NAP XLSX staging command.
- Raspberry Pi deployment, daily generation, and backup integration.
- Tests for registry completeness, TicketWeb coverage, migration idempotence,
  coordinate quarantine, bounded planning, review gates, rights gates,
  immutable packs, and public DB compilation.

This is a real operational foundation. It is not a fabricated claim that every
national route and timetable has already been acquired.

## Verified national baseline

Primary registry:

- POAYS directory: <https://ktelbus.com/category/ktel/>
- POAYS historical page states that the intercity federation has 62 operators:
  <https://ktelbus.com/poays/istoriko/>

Verified TicketWeb entry pattern:

- `https://ktelbus.gr/{tenant}/ticketweb/`
- Example: <https://ktelbus.gr/kav/ticketweb/>

Greek National Access Point dataset:

- <https://data.nap.gov.gr/el/dataset/information-about-transport-by-long-distance-buses-in-greece>
- The catalog describes routes, timetables, passenger classes, fare products,
  and regional bus-station contacts.
- The catalog states Open Data Commons ODbL 1.0.
- Visible resources are dated 2020, 2021, and 2023.
- The live server returned HTTP 502 again on 26 July 2026, including from the
  CKAN API and direct resource path. The XLSX body could not be downloaded.

### Registry coverage

| Metric | Current value |
|---|---:|
| Official POAYS intercity operators | 62 |
| Verified official TicketWeb tenants | 26 |
| Unverified official TicketWeb operators | 36 |
| Additional verified non-federation tenants | 2 |
| Official TicketWeb coverage | 41.9 percent |
| Raw stop groups across the 26 official tenants | 5,378 |
| Raw stop records across the 26 official tenants | 19,872 |
| Raw records marked web-active | 511 |
| Normalized published KTEL stops at initial bootstrap | 0 |
| Published national timetables at initial bootstrap | 0 |

The zeroes are intentional. The public database accepts only reviewed rows from
sources whose publication rights are marked `allowed`.

### Verified TicketWeb tenants

| Tenant | Operator | POAYS 62 | Carrier | Groups | Stops | Web-active |
|---|---|---:|---:|---:|---:|---:|
| `ach` | KTEL Achaia | Yes | 8 | 344 | 1,363 | 34 |
| `ait` | KTEL Aitoloakarnania | Yes | 2 | 447 | 1,159 | 47 |
| `amo` | Amorgos Bus Company | No | 63 | 28 | 55 | 4 |
| `arg` | KTEL Argolida | Yes | 4 | 151 | 880 | 6 |
| `ark` | KTEL Arkadia | Yes | 5 | 327 | 860 | 64 |
| `art` | KTEL Arta | Yes | 6 | 334 | 975 | 31 |
| `evv` | KTEL Evia | Yes | 12 | 302 | 1,176 | 65 |
| `fok` | KTEL Fokida | Yes | 59 | 167 | 732 | 26 |
| `fth` | KTEL Fthiotida | Yes | 57 | 221 | 585 | 16 |
| `gre` | KTEL Grevena | Yes | 9 | 147 | 332 | 4 |
| `ima` | KTEL Imathia | Yes | 16 | 125 | 539 | 6 |
| `kard` | KTEL Karditsa | Yes | 25 | 256 | 620 | 7 |
| `kas` | KTEL Kastoria | Yes | 27 | 117 | 274 | 4 |
| `kav` | KTEL Kavala | Yes | 24 | 232 | 1,263 | 5 |
| `kef` | KTEL Kefalonia | Yes | 29 | 146 | 1,036 | 7 |
| `ker` | KTEL Corfu | Yes | 28 | 230 | 1,246 | 52 |
| `kor` | KTEL Corinthia | Yes | 32 | 181 | 2,093 | 7 |
| `lak` | KTEL Lakonia | Yes | 34 | 224 | 513 | 2 |
| `lef` | KTEL Lefkada | Yes | 37 | 116 | 266 | 4 |
| `les` | KTEL Lesvos | Yes | 36 | 100 | 221 | 11 |
| `mes` | KTEL Messinia | Yes | 41 | 411 | 1,437 | 67 |
| `sam` | KTEL Samos | Yes | 52 | 42 | 109 | 1 |
| `syr` | KTEL Syros | Yes | 54 | 35 | 294 | 2 |
| `thesp` | KTEL Thesprotia | Yes | 18 | 140 | 337 | 17 |
| `tri` | KTEL Trikala | Yes | 56 | 228 | 478 | 16 |
| `ttri` | Hypertours Trikala | No | 201 | 14 | 30 | 4 |
| `xan` | KTEL Xanthi | Yes | 44 | 205 | 684 | 6 |
| `zak` | KTEL Zakynthos | Yes | 14 | 150 | 400 | 4 |

The source data is stored in
`ops/syrmos-api/pkg/ktel/operators.json`.

## Architecture decision

The architecture review reached a three-round convergence.

### Position A

Use the existing Raspberry Pi patterns, SQLite, FastAPI, atomic JSON, and
nginx, but create a normalized KTEL domain with strict provenance and review
states.

### Position B

Do not share a writable file with rail. Compile reviewed KTEL data into a
disposable public database so a crawler, parser, or legal mistake cannot leak
candidate data or block rail departures.

### Round 1

The two sides rejected two independently migrated operational databases. They
agreed on one canonical KTEL ingestion schema and a derived public artifact.

### Round 2

The two sides converged on:

- Physical separation from the rail database.
- Minimal source retention based on rights and privacy.
- A public compiler that selects reviewed, rights-allowed rows.
- Explicit dimensional coverage and three-valued journey absence semantics.

### Round 3

Atomic replacement of `ktel-public.db` was accepted for Phase 1 because each
request opens a fresh read-only connection. A remaining torn-release risk was
resolved by:

- Embedding `releaseId` in the public DB and every API payload.
- Writing JSON packs under release-addressed immutable filenames.
- Writing the fixed manifest last.
- Verifying every pack hash against the manifest.
- Returning HTTP 503 with `Retry-After: 1` if the DB and manifest release IDs
  differ during the short publication switch.
- Retaining the previous public DB and previous manifest for rollback.

Equilibrium result:

```text
isolated ktel.db
  -> validation, review, rights gate
  -> atomic compiler
  -> read-only ktel-public.db + immutable packs
  -> manifest-last release switch
  -> FastAPI and nginx
```

## Database model

### Governance

- `ktel_operators`
- `ktel_sources`
- `ktel_operator_sources`
- `ktel_import_runs`
- `ktel_source_artifacts`
- `ktel_source_records`
- `ktel_import_issues`
- `ktel_review_events`
- `ktel_publication_releases`

The source policy includes:

- `rights_status`
- `terms_status`
- `terms_url`
- `license_id`
- `retention_mode`
- `allowed_data_classes`
- `contains_personal_data`
- `contains_commercial_state`
- `retention_days`
- `legal_reviewed_at`

### Transport

- `ktel_stop_places`: passenger-facing terminal or locality groups.
- `ktel_stops`: physical boarding or alighting points.
- `ktel_lines`: public service families.
- `ktel_journey_patterns`: express and via-route variants.
- `ktel_pattern_stops`: ordered stops and pickup or dropoff rules.
- `ktel_service_calendars`: validity and weekday rules.
- `ktel_calendar_exceptions`: added and removed service dates.
- `ktel_trips`: official schedules or date-specific booking observations.
- `ktel_stop_times`: exact, approximate, or unknown call times.

### Truth controls

Every publishable entity has:

- Source-scoped external identity.
- First-seen and last-seen times.
- Publication state.
- Source rights state.
- Schedule scope.
- Geometry status where relevant.

Publication states:

- `candidate`
- `verified`
- `published`
- `stale`
- `withdrawn`
- `quarantined`

No candidate is returned by the public API. Even a manually marked
`published` row is excluded from the compiled DB when the source rights state
is not `allowed`.

## API

Canonical namespace:

```text
GET /api/v1/coaches/manifest
GET /api/v1/coaches/operators
GET /api/v1/coaches/coverage
GET /api/v1/coaches/sources
GET /api/v1/coaches/stops
GET /api/v1/coaches/routes
GET /api/v1/coaches/routes/{patternId}
GET /api/v1/coaches/journeys
```

Temporary compatibility aliases are also available under `/api/ktel`.

### Manifest

`GET /api/v1/coaches/manifest`

Returns:

- `releaseId`
- `schemaVersion`
- `updatedAt`
- Completeness state
- Immutable pack paths
- SHA-256 hash for each pack

Clients must reject a pack whose hash or release ID differs from the manifest.

### Operators

`GET /api/v1/coaches/operators`

Parameters:

- `officialOnly`, default `true`
- `coverageStatus`, optional

The first release returns the full 62-entry official registry. It can also
return the two additional verified tenants when `officialOnly=false`.

### Coverage

`GET /api/v1/coaches/coverage`

Returns national totals and one coverage record for every official operator:

- Directory identity status.
- TicketWeb status.
- Timetable coverage.
- Published stops.
- Journey patterns.
- Trips.
- Reviewed geometry.
- Fare observations.
- Registry and TicketWeb verification dates.
- Last successful source retrieval.
- Known limitations.

Timetable states include:

- `directory_only`
- `official_terminal_schedule`
- `official_stop_schedule`
- `booking_observed`
- `stale`
- `unknown`

### Stops

`GET /api/v1/coaches/stops`

Parameters:

- `query`
- `operatorId`
- `coordinateStatus`
- `webActive`
- `limit`, capped at 500
- `offset`

Only compiled published stops are returned.

### Routes

`GET /api/v1/coaches/routes`

Parameters:

- `operatorId`
- `query`
- `limit`, capped at 500
- `offset`

A route result identifies one journey pattern, not only an origin and
destination. Express, via Eleftheroupoli, and via Peramos remain distinct.

### Journeys

`GET /api/v1/coaches/journeys?date=YYYY-MM-DD`

Optional parameters:

- `originStopId`
- `destinationStopId`
- `operatorId`
- `limit`, capped at 200

Responses label:

- `scheduleScope`
- `commercialState`
- `sourceId`
- `observedAt`
- `expiresAt`
- `approximateArrivalAt`
- Fare and official booking URL when publishable

An empty array currently returns `resultStatus=unknown`. It never means
confirmed no service.

Future authoritative timetable coverage can return:

- `confirmed_no_service`
- `not_observed`
- `unknown`

## TicketWeb acquisition policy

Observed read-only endpoint family:

- `getAgencyData`
- `booking/stopGroups`
- `booking/stops`
- `booking/reachableStops`
- `booking/executions`

Explicitly excluded:

- Seat maps.
- Reservation creation.
- Passenger information.
- Payment.
- Open-ticket booking or retrieval.
- Ticket issuance.
- Reserved-seat or paid-seat counts.

Live reads are disabled unless all required environment values exist and
`KTEL_TICKETWEB_TERMS_APPROVED=1`.

No credential or key found in a browser client was copied into this repository.

Default safety limits:

| Control | Default |
|---|---:|
| Requests per run | 500 |
| Minimum delay | 2 seconds |
| Random jitter | up to 0.4 seconds |
| Response limit | 10 MiB |
| Execution horizon | 14 days |
| Planner hard cap | 2,000 queries |
| Maximum horizon accepted by code | 31 days |
| Raw disk cache | Disabled |

The planner accepts only a directed adjacency map returned by
`reachableStops`.

It does not compute the Cartesian product of 19,872 stops. That would be
394,876,512 ordered non-self pairs before dates, passenger types, or tenants.

Example:

```bash
python -m scripts.ktel_pipeline plan-executions \
  --tenant kav \
  --reachable /path/to/kav-reachable.json \
  --start-date 2026-07-26 \
  --days 14 \
  --max-queries 2000
```

## Normalized adapter import

Source adapters write a normalized JSON snapshot. Imports default to
`candidate`.

```bash
python -m scripts.ktel_pipeline import-normalized \
  /path/to/normalized-operator-snapshot.json
```

The normalized document contains:

- `operatorId`
- `sourceId`
- `retrievedAt`
- `stopPlaces`
- `stops`
- `lines`
- `journeyPatterns`
- `trips`

Invalid coordinates are quarantined during import.

Review is explicit:

```bash
python -m scripts.ktel_pipeline review \
  stop ks_example \
  publish \
  --reviewer peter \
  --reason "Matched against the operator terminal page"
```

Compile after a reviewed batch:

```bash
python -m scripts.ktel_pipeline publish
python -m syrmos_admin.generator
```

## Coordinate and stop normalization

Automatic coordinate states:

- `valid`
- `missing`
- `out_of_range`
- `outside_greece`
- `placeholder`
- `duplicate`
- `manual_review`

The Greece bounding box is deliberately broad. Passing it does not prove a
point is correct.

The route to a reliable national stop graph is:

1. Preserve operator and source external IDs.
2. Normalize Unicode and searchable names.
3. Quarantine impossible coordinates.
4. Cluster exact and near duplicates within one operator.
5. Compare terminal address, phone, group membership, and route usage.
6. Link cross-operator terminals only through reviewed crosswalks.
7. Never merge by name alone.
8. Publish only reviewed canonical stops.

The 19,872 raw TicketWeb records have not yet been run through this process
because the reusable raw payload is not stored and TicketWeb reuse rights
remain pending.

## Road geometry

An ordered stop list is not a road shape.

Geometry states:

- `unverified`
- `ordered_stops_only`
- `osm_candidate`
- `reviewed`
- `rejected`

The platform accepts GeoJSON geometry through the normalized import contract,
but it publishes geometry only after review.

National geometry work must:

1. Resolve the journey pattern and ordered stops.
2. Find an operator-published map, a reviewed OSM route relation, or a
   permitted routing result.
3. Confirm direction and route variant.
4. Check that stops occur in monotonic route order.
5. Check route length and impossible detours.
6. Store source and review state.
7. Keep express and via variants separate.

The 355 OSM relations found in the research sweep are candidate evidence, not
proof of a complete national road graph.

## Greek NAP XLSX

The importer is ready:

```bash
python -m scripts.ktel_pipeline stage-nap-xlsx \
  /path/to/2023-information-about-transport-by-long-distance-buses-in-greece.xlsx
```

It:

- Computes the workbook SHA-256 digest.
- Records source, retrieval time, size, and ODbL rights metadata.
- Inventories every worksheet and header.
- Stages every non-empty row as a source record.
- Preserves three sample rows per sheet in the command output.
- Publishes zero canonical entities until a reviewed column mapping exists.

The workbook itself was not obtained. The NAP server returned HTTP 502 on the
dataset page, CKAN API, and direct resource URL on 26 July 2026.

## Raspberry Pi operations

### Environment

Copy `ktel.env.example` values into the Pi environment file without adding
secrets to git.

Required paths:

```text
SYRMOS_KTEL_DB_PATH=/home/peterdsp/syrmos-api/db/ktel.db
SYRMOS_KTEL_PUBLIC_DB_PATH=/home/peterdsp/syrmos-api/db/ktel-public.db
SYRMOS_KTEL_MANIFEST_PATH=/home/peterdsp/syrmos-api/out/ktel/manifest.json
```

### Bootstrap

```bash
cd /home/peterdsp/syrmos-api
.venv/bin/python -m syrmos_admin.ktel_db
.venv/bin/python -m scripts.ktel_pipeline seed
.venv/bin/python -m scripts.ktel_pipeline publish
.venv/bin/python -m syrmos_admin.generator
```

The normal `deploy.sh` now performs the KTEL migration, registry seed,
publication compile, snapshot generation, nginx configuration, and service
restart.

### Storage

- `db/ktel.db`: canonical ingestion, review, and provenance.
- `db/ktel-public.db`: current disposable public artifact.
- `db/ktel-public-previous.db`: previous public artifact.
- `out/ktel/manifest.json`: current committed release.
- `out/ktel/manifest.previous.json`: previous committed manifest.
- `out/ktel/{releaseId}-*.json`: immutable packs.
- `backups/ktel-YYYY-MM-DD.db`: daily ingestion backup, retained 30 days.

The public DB is not backed up because it is reproducible.

### Publication invariants

1. Migrate and seed only the ingestion DB.
2. Compile into `ktel-public.db.tmp`.
3. Run SQLite foreign-key checks.
4. Checkpoint WAL and switch the artifact to a standalone journal.
5. Preserve the previous public DB.
6. Atomically replace the current public DB.
7. Write immutable release-addressed packs.
8. Preserve the previous manifest.
9. Atomically write the manifest last.
10. Reject API reads while DB and manifest release IDs differ.

## Nationwide execution program

### Workstream 1: source authority and permissions

- Obtain a stable NAP workbook download or provider copy.
- Record the ODbL attribution and share-alike publication design.
- Ask the TicketWeb platform provider for documented read-only access, rate
  limits, data scope, cache rules, and redistribution rights.
- Review each operator source's terms before enabling automated ingestion.
- Keep booking and ticket issuance outside Syrmos unless a formal commercial
  agreement creates that authority.

### Workstream 2: all 62 source registry

- Recheck all federation pages weekly.
- Discover official operator sites and machine-readable resources.
- Run explicit TicketWeb tenant candidates only, no blind namespace scan.
- Record verified live, verified absent, unverified, blocked, or retired.
- Resolve the POAS 34-member claim versus the 33 names currently exposed by
  its map as a separate urban registry.

### Workstream 3: NAP import

- Stage the newest workbook.
- Map workbook sheets to operators, lines, stops, calendars, trips, and fares.
- Preserve ODbL lineage for every mapped row.
- Quarantine stale or internally inconsistent rows.
- Compare the workbook with current operator publications.

### Workstream 4: TicketWeb graph

- Import permitted tenant identity and stop inventory.
- Resolve web-active origins.
- Call `reachableStops` only for verified origins.
- Build a directed sellable graph.
- Query a bounded rolling window.
- Store date-specific observations, not inferred recurring calendars.
- Expire stale bookability observations.

### Workstream 5: remaining operator adapters

Create versioned adapters for:

- Static HTML.
- Searchable web timetables.
- PDFs.
- Images requiring reviewed transcription.
- WordPress seasonal announcements.
- Operator-provided GTFS, NeTEx, SIRI, or proprietary feeds.

Every adapter needs:

- Fixture.
- Parser version.
- Source rights policy.
- Response and file size limits.
- Semantic diff.
- Quarantine thresholds.
- Maintainer ownership.

### Workstream 6: entity resolution

- Normalize all acquired stop records.
- Preserve source IDs.
- Review duplicates and terminal crosswalks.
- Resolve route variants.
- Build service calendars and exceptions.
- Separate official schedules from booking observations.
- Track retrieved time and effective time independently.

### Workstream 7: geometry

- Collect operator maps and permitted geospatial sources.
- Match OSM relations where available.
- Route only reviewed ordered stops.
- Validate stop order and distances.
- Publish route geometry per journey pattern.

### Workstream 8: client delivery

- Generate operator or geography offline packs.
- Add a coach domain to KMP, iOS, Android, and Web together.
- Keep basic directory and verified schedules offline.
- Treat current booking observations as optional network enhancements.
- Open the official operator booking page for purchase.

## Explicitly not completed

These items remain incomplete, with the reason and unblocker:

- Every route and departure for all 62 operators is not populated. The sources
  have not been acquired and reviewed.
- The remaining 36 federation operators are not proven absent from TicketWeb.
  A documented candidate list or provider cooperation is required.
- Every origin-destination pair was not queried. The implementation forbids
  that strategy and uses `reachableStops`.
- Every operating date and seasonal calendar was not queried. A rolling
  bounded observation program and official calendar sources are required.
- The 19,872 raw stop records were not normalized or deduplicated. A permitted
  raw export or provider feed is required.
- Invalid TicketWeb coordinates were not repaired in bulk. The code now
  quarantines them, but correct coordinates need authoritative sources and
  review.
- Exact road geometry for every route is not proven. Ordered stops and 355
  OSM candidates are insufficient.
- The NAP XLSX was not inspected because its server still returned HTTP 502.
  The staging importer is ready once the file is available.
- Private, documented, or contractual live feeds were not obtained. Operator
  or platform cooperation is required.
- TicketWeb reuse rights were not legally cleared. The compiler blocks
  permission-pending TicketWeb rows from public release.
- TicketWeb terms were not reviewed by qualified legal counsel. This document
  and code are not legal advice.
- National passenger demand and revenue were not validated. That requires
  operator, government, or ticket-sales datasets with lawful access.
- Reservation, purchase, payment, and ticket issuance were not attempted and
  are intentionally excluded.
- The Raspberry Pi was not deployed or live-tested in this change. All
  verification was local.
- The iOS, Android, and Web coach UI was not implemented. This change builds
  the national data and API layer first.

Calling the current database a completed national route timetable would still
be inaccurate. Calling it a complete national registry, safe ingestion model,
and deployable API foundation is accurate.
