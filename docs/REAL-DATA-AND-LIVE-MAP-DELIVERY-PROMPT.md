# Odivrelo: recover the KTEL data, deliver the map, finish the product

Implementation prompt prepared 3 October 2026 from the local checkout. Paste
this prompt into the implementation agent working in this repository.

## Assignment

Work in `/Users/peterdsp/git/Odivrelo`. Deliver a usable Greek intercity coach
product on Web, iOS and Android, populated with all the real, usable data we
have already acquired, with a first-class map experience comparable in utility
to Syrmos. Implement the missing pieces, verify them in running applications,
and carry the work through reviewed integration, deployment and beta delivery.
This is an execution request, not a request for another analysis or proposal.

The owner previously commissioned substantial national KTEL research and data
acquisition. Find what happened to it. Recover the underlying records wherever
they were retained, connect them to the product, and account for every source.
Do not equate an operator directory, aggregate counts or a written analysis
with imported timetables. Do not discard the earlier work and start with an
invented region again. A first real corridor is an integration milestone, not
permission to stop investigating and integrating the rest of the country.

Use Odivrelo's identity and existing architecture. Inspect Syrmos as a reference
in `/Users/peterdsp/git/Syrmos`; preserve that separate product. Work autonomously
through routine engineering decisions. Preserve all existing changes, inspect
applicable repository instructions, and use `codex/` for new branches. Keep a
durable execution checklist and evidence so another session can continue.

## Verified starting evidence — refresh before relying on it

These are local source and artifact findings, not a fresh audit of production:

- `docs/KTEL_NATIONAL_PLATFORM.md` records the earlier national analysis:
  62 federation operators, 26 official TicketWeb tenants plus two additional
  tenants, 5,378 stop groups, 19,872 raw stop records and 511 web-active records.
- `server/ktel-staging/pkg/ktel/operators.json` preserves 64 operator entries
  and per-tenant aggregate counts. Summing the 26 `ktel-` tenants reproduces
  those three totals. This file does **not** contain 19,872 individual stops.
- `data/operators/registry.json` is a separate acquisition registry. Reconcile
  the two registries instead of assuming they are equivalent or counting the
  same operator twice. Metadata-only acquisition does not retain raw bodies.
- `artifacts/nap/nap-ktel-2020.xlsx` exists locally. Its previous inspection is
  in `docs/phase0/NAP-DATASET-EVIDENCE.md`; it is historical city-level evidence,
  not proof of current departure times or physical boarding locations.
- `artifacts/ingest.db` currently contains 65 operator rows, seven stop rows and
  five trip rows. `artifacts/public.db` contains 65 operator rows, five stop
  rows and four trip rows; its source-artifact and source-record tables are
  empty. These counts demonstrate why registry size cannot measure usable
  coverage. Inspect entity content and provenance before classifying it.
- Demo inputs are in `data/fixtures/aloria-demo-snapshot.json`; generated
  releases exist under `artifacts/releases/odivrelo/` and the older
  `artifacts/releases/poravia/`. Client packs also exist in the iOS resources
  and generated Web/Android asset directories. Inspect the manifest and its
  referenced metadata pack; do not assume `dataMode` is a top-level field.
- `.github/workflows/deploy-web.yml` calls `scripts/ci-pipeline-smoke.sh`, which
  calls `scripts/api-seed-demo.sh`. Both mobile release workflows also call the
  demo seeder. Releasing the current code can recreate demo data even after a
  successful real import. This is an implementation problem to fix.
- Web `apps/web/src/components/MapPanel.tsx` draws route geometry and stops with
  MapLibre on a deliberately tileless background. It is not a live fleet map.
- iOS `apps/ios/Odivrelo/Features/JourneyDetail/RouteMapSection.swift` provides a
  MapKit journey map. It has no fleet tracking, and currently draws unnamed
  geometry vertices rather than resolving all stop annotations by stop ID.
- Android `apps/android/src/main/kotlin/dev/peterdsp/odivrelo/ui/journey/JourneyDetailScreen.kt`,
  specifically `MapOrList`, shows explanatory UI and an external maps handoff.
  That does not meet the requirement for an embedded interactive map.
- `docs/LIVE_COACH_MAP_AND_ETA.md` is a detailed proposal, not implemented map
  evidence. The existing clients explicitly describe scheduled-only data.
- Syrmos reference files include
  `feature/map/src/commonMain/kotlin/com/syrmos/feature/map/MapViewModel.kt`,
  `feature/map/src/androidMain/kotlin/com/syrmos/feature/map/PlatformMapView.android.kt`
  and `iosApp/iosApp/Views/Map/MapView.swift`. Its code distinguishes observed
  live vehicles from timetable projections and handles aging and expired
  observations. Inspect the actual implementations; do not assume every
  moving Syrmos marker is GPS evidence.
- On 3 October, both store app records were confirmed in the browser:
  Apple app ID `6818628951`, Google Play app ID `4976107944969010424`, package
  and bundle ID `dev.peterdsp.odivrelo`. Signing credentials were previously
  configured. Verify access and processing; do not repeat the obsolete claim
  that the owner must create these records or buy developer accounts.
- Existing uncommitted work includes iOS release signing changes, the calendar
  importer/publisher, demo-seed protections, calendar regression tests and
  credential/runbook documentation. Read and preserve it before editing.

## 1. Recover and reconcile all prior data

Create `docs/beta/DATA-RECOVERY-INVENTORY.md` and a machine-readable inventory
with one entry per actual artifact/source. Start with the paths above, ignored
data directories, acquisition state, Git history/branches/worktrees and the
KTEL staging package. Trace former Odivrelo names HodoMap, Poravia and Dromologio,
and the original Syrmos KTEL migration. Old checkout names may no longer exist.

Inspect the documented local data roots under `.local/share/odivrelo`,
`poravia`, `hodomap` and `syrmos` where present. Resolve configured
`ODIVRELO_`, `PORAVIA_`, `HODOMAP_` and `SYRMOS_` data-path overrides without
printing credentials. Search relevant historical task records if accessible
for exact artifact destinations, acquisition commands and retention decisions.
Do not scan unrelated personal material.

The historical platform document points to a Raspberry Pi layout containing
`/home/peterdsp/syrmos-api/db/ktel.db`, `ktel-public.db`,
`out/ktel/` and `backups/ktel-YYYY-MM-DD.db`. Investigate these through existing
authorized access and documented deployment configuration. Read databases
safely and make consistent snapshots before any migration. Do not overwrite
the Syrmos service or its databases. If access is absent, report the exact
missing location/access and continue local work.

For every artifact record its location, digest, size, format, collection time,
operator/tenant, row counts, effective dates, source URL, retention conditions,
permission evidence and status: recovered raw data, aggregate metadata only,
candidate records, published records, stale, missing, restricted or corrupt.
Separate real records from fixtures, duplicates and failed imports. Distinguish
19,872 observed records from 19,872 unique physical stops.

Explain the gap between the July analysis and today's releases using concrete
evidence: records not retained, held remotely, lost during migration, never
normalized, rejected, or overwritten by demo generation. Do not guess which
occurred. Where records are missing, identify the documented endpoint and
implement a bounded reacquisition path where source access and reuse allow it.
Do not manufacture rows from aggregate counts.

## 2. Populate the real national dataset

Read `docs/DATA_GOVERNANCE.md`, both national KTEL documents, the actual source
policies and current adapters. Recheck stale blocker statements against primary
evidence. An `unknown` database flag is an unresolved assessment, not by itself
proof that acquisition or reuse is prohibited. Conversely, public accessibility
does not establish unrestricted reuse. Record the supported scope per source
and data class; preserve actual restrictions without inventing blanket ones.

Build an operator-by-operator work queue for all 62 federation operators and
the two additional known operators, incorporating any newly verified operators
without conflating them with that baseline. For each, locate and assess official
sites, timetable pages/PDFs, GTFS or other feeds, stops, calendars, announcements,
booking handoffs and any live-position service. Reuse recovered data first,
then refresh or fill gaps through permitted adapters. Use bounded requests,
caching, backoff and source-specific budgets. Do not generate every possible
stop pair or mistake ticket availability for the complete service timetable.

Implement ingestion through `server/ktel-staging/odivrelo_ktel/`, integrate the
acquisition pipeline in `server/src/odivrelo_pipeline/`, and update the public
API/contracts and shared clients as needed. Normalize and preserve:

- Operator and source identity; original external IDs and Greek spellings.
- Distinct cities, station complexes and physical boarding/alighting points.
- Route variants, directions, ordered intermediate stops and restrictions.
- Trips, scheduled stop times, effective periods, weekdays, seasonal changes,
  holiday exceptions and overnight service in `Europe/Athens`.
- Supported fares and official booking URLs with retrieval/validity context.
- Route geometry, origin of coordinates, confidence and attribution.
- Source lineage, observation time, parser version and review history.

Deduplicate using supported identity and geography, not names alone. Preserve
unknown fields. Never turn an operator office into a boarding stop or a city
centroid into an exact terminal. Quarantine invalid coordinates and ambiguous
matches. Do not infer current schedules from the 2020 workbook or reset its
freshness when it is downloaded again. Use it for recoverable historical facts
and discovery, then verify changeable facts against current sources.

Import every usable recovered record into the appropriate layer. Publish all
records that pass the existing evidence and review requirements. Do not route
scraped material through `manual-review` to erase lineage or bypass restrictions.
Prepare a concrete review queue for decisions requiring a person; never forge
human review. Missing optional fields must not discard an otherwise usable
service. Directory-only operators should remain useful through verified official
links and explicit coverage labels, without becoming fake timetable coverage.

Make imports idempotent, transactional, versioned and reversible. Refreshes must
handle removed services and exceptions without erasing unrelated operators.
Reconcile counts from recovered → parsed → candidate → approved → compiled →
API → client, with an explicit reason for every excluded group of records.

## 3. Deliver the map across Web, iOS and Android

Inspect the current map and navigation flows first. Implement what is missing
on each platform, using Syrmos for interaction and state-management references
while keeping Odivrelo's coach domain and visual identity. Provide an accessible
main Map destination, not just a journey-detail button or an external handoff.

The map must provide geographic context with a working basemap, attribution,
pan/zoom, fit-route, an explicit locate-me action, stop and operator/route
filters, clustering where necessary, and a list equivalent. Choose a provider
whose terms and operational limits fit the product; use approved configuration
and keep private provider credentials on the server. Do not assume a public
tile server permits bulk offline downloads.

Tapping a stop shows its real name, operator, next scheduled or supported
predicted departures and journey navigation. Tapping a coach opens a sheet
with origin, destination, exact route variant, next stop, full stop timeline,
departure/arrival information, freshness, source and official booking handoff.
Keep the selected origin-to-destination segment prominent, mark "Board here"
and "Get off here", and retain the full route with outside-segment stops muted.
Resolve stop coordinates by IDs; geometry vertices are not stop identities.
Preserve selection across map/list changes, detail navigation and restoration.

Use explicit states:

- **Live:** a fresh, attributable vehicle observation matched to the operator
  and, when possible, a specific trip. An unmatched vehicle must say so.
- **Estimated:** a defensible timetable/geometry or traffic-derived estimate,
  with uncertainty and visible explanation that departure/location is unconfirmed.
- **Scheduled:** a supported timetable, with no claim about vehicle position.
- **Stale/unavailable:** actual age, restrained or expired marker treatment and
  a useful scheduled/list fallback.

Search for and connect real coach feeds wherever available and permitted. Build
the provider adapter, normalized live contract and client refresh path. Include
observation timestamps, trip matching, provider-specific freshness thresholds,
deduplication, rejection of invalid/out-of-order observations, failure backoff
and foreground/background lifecycle handling. Age markers even when the feed
stops delivering updates. Do not animate beyond the latest validated observation.

Where GPS is unavailable, support clearly labelled estimates only when the
inputs justify them. Sparse stop coordinates or two terminal times do not
justify precise road positions, actual departures, delays or minute-exact ETAs.
Do not substitute OASA/rail vehicles for KTEL coverage. If no live coach source
can be connected, finish the real-data map and live integration infrastructure,
document the exact missing feed, and mark live coverage incomplete.

Saved route information and downloaded timetables must work offline. Clearly
distinguish cached data from current observations. Show an offline schematic
or cached basemap only within actual capability and provider terms. Keep the
map usable with denied location permission, reduced motion, large text,
VoiceOver/TalkBack, phone/tablet layouts and supported foldable configurations.

## 4. Make real data survive build, deployment and updates

Introduce a production release path that consumes an explicit reviewed dataset
release and never silently invokes the demo seeder. Keep fixture generation for
tests and explicit demo builds. Update Web, iOS and Android workflows and pack
sync scripts together. Tests must prove production cannot switch back to Aloria
through a default, missing environment variable, failed import or stale asset.

Carry the same dataset identity and verified hashes through the public database,
API, static packs, all client bundles and offline downloads. Reject inconsistent
releases. Publish atomically with a retained rollback version. Support a rolling
service-date horizon and monitored refresh, not only the fixed demonstration
dates. Respect each source's actual validity window. Check the current date;
the inspected demo resources include a last date of 2 October 2026.

Add monitoring for failed refreshes, sudden count changes, expiring calendars,
stale feeds, rejected records and client update failures. Provide a diagnostic
view/report for operators and ingestion issues. Ordinary passengers should see
useful journeys and concise coverage information, not engineering status screens.

## 5. Prove the product works and deliver it

Start with one recovered, current real journey through the complete pipeline,
then expand across the full inventory. Continue independent work when a specific
operator is blocked. Reuse existing suites and add meaningful tests for importer
formats, calendars/DST/overnight travel, duplicate and invalid stops, retention
and publication gates, refresh rollback, live/stale transitions, source outages,
map selection and release identity. Run affected required checks.

Drive the actual Web app in a browser, iOS in Simulator and Android in an
emulator. For each platform verify real place search → valid-date result →
correct boarding location → embedded map → stop/coach sheet → selected segment
→ official booking handoff → saved/offline journey. Verify physical devices
where available; report unavailable verification precisely. Exercise location
denial, offline recovery, empty coverage, stale observations, failed tiles,
deep links and process restoration. Synthetic feed tests are test evidence,
not proof of live KTEL coverage.

Capture meaningful screenshots and runtime evidence with dates, release IDs,
source IDs and representative real journeys. For every published operator,
verify automated source-to-client reconciliation and representative service
dates. Manually inspect varied parser/source types and high-risk terminals;
spot checks of one corridor cannot establish national completeness.

Review the diff, fix issues and integrate through the repository's normal PR
and CI workflow. Continue through authorized merge, Web deployment and beta
uploads using the existing credentials and confirmed store records. Verify the
deployed site, TestFlight processing/tester availability and Google Play internal
testing status separately. Do not change Syrmos access or credentials to make
Odivrelo pass. Public store launch, financial commitments, operator messages and
new legal certifications require their own applicable authority.

## Completion and handoff

Keep these deliverables current:

- `docs/beta/DATA-RECOVERY-INVENTORY.md`: where the original data is, what was
  recovered, and exact missing locations or retention outcomes.
- A machine-readable operator/source coverage inventory and updated
  `docs/beta/OPERATOR-COVERAGE-LEDGER.md`, based on actual records.
- `docs/beta/LIVE-MAP-PARITY.md`: Syrmos reference capabilities, each Odivrelo
  platform's implementation/runtime evidence, and actual live-feed coverage.
- `docs/beta/REAL-PRODUCT-ACCEPTANCE.md`: requirement results, source-to-release
  reconciliation, runtime evidence, deployment/build links and remaining gaps.

The final report must answer: Where were the 19,872 reported raw records? How
many underlying rows were recovered? How many distinct usable stops, operators,
routes and current trips are now public? Which data appears in each app? Which
map capabilities work on each platform? Which operators have genuine live GPS,
estimates or schedules only? Can a traveller install/open the delivered product
and complete a real journey flow today?

Do not declare completion because a map renders, tests pass, credentials exist,
or a dataset is labelled `real`. Do not silently narrow national scope to a
single corridor. Finish everything supported by available data and access;
report any remaining blocker with exact evidence and the smallest required
owner action. If actual data/feed access prevents the requested outcome, state
that the product or affected capability remains incomplete.
