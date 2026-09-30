# Test matrix

Status values: `passed`, `failed`, `not-run`, `blocked`. **No row is
`passed` without a command and an artifact behind it.** Rows for work still in
progress are `not-run` until their evidence lands; they are not optimistically
marked.

Build commit for the rows below: `5a2fd62` on `codex/beta-1.0.0`.
Data release: see `artifacts/ARTIFACT-MANIFEST.json`. Data mode: `demo`.
Host: macOS 27.0 arm64, Python 3.12.14, Node 24.20.0, JDK 21, Xcode 27.0.

Artifacts live in the gitignored `artifacts/` and `apps/*/artifacts/`
directories. They are not committed. Retention in CI is 7 days.

---

## A. Data pipeline, compiler, GTFS and release

| # | Scenario | Status | Command | Evidence | Limitation |
|---|---|---|---|---|---|
| A1 | Migrate, seed, import, review, compile, pack, verify end to end | passed | `bash scripts/ci-pipeline-smoke.sh` | `artifacts/releases/poravia/manifest.json`, 11 packs | demonstration fixture only |
| A2 | Staging suite | passed | `cd server/ktel-staging && PYTHONPATH=. ../../.venv/bin/python -m unittest discover -s tests` | 22 tests, 0 failures | |
| A3 | Acquisition pipeline suite, including rename compatibility | passed | `cd server && PYTHONPATH=src ../.venv/bin/python -m unittest discover -s tests` | 11 tests, 0 failures | |
| A4 | GTFS time past 24:00 for an overnight journey | passed | A1 assertions, and `test_release_and_gtfs.py` | 1 stop time at or beyond 24:00 in the generated feed | |
| A5 | Both 2026 Greek daylight-saving transitions | passed | `test_release_and_gtfs.py::GtfsTimeSemanticsTest` | 29 March and 25 October both keep their wall clock | |
| A6 | Release is content addressed and the manifest is written last | passed | A1 assertions | every pack file name carries its own digest | |
| A7 | Corrupt pack detected | passed | `test_verify_detects_a_corrupt_pack` | `ReleaseConsistencyError` raised | |
| A8 | Missing pack detected | passed | `test_verify_detects_a_missing_pack` | `ReleaseConsistencyError` raised | |
| A9 | Rollback restores the previous manifest and it still verifies | passed | `test_rollback_restores_the_previous_manifest` | packs are immutable, so nothing is deleted | |
| A10 | Generating twice from an unchanged database is byte identical | passed | `test_generating_twice_is_byte_identical` | identical manifest bytes | |
| A11 | A `permission_pending` source stays out of a public release **even after a reviewer approves the row** | passed | `test_rights_pending_source_never_reaches_a_public_release` | row approved, still absent from every pack | |
| A12 | An unreviewed candidate never appears publicly | passed | `test_public_payloads_exclude_unreviewed_rows` | | |
| A13 | Invalid coordinates quarantined and unpublishable | passed | `test_coordinate_quarantine` | publish refused for a quarantined stop | |
| A14 | Release refused when the compiled id disagrees with the public database | passed | `test_release_without_a_published_database_is_refused` | | |
| A15 | Referential integrity on every compile | passed | `PRAGMA foreign_key_check` in `compile_public_database` | compile aborts and rolls back on a violation | |

## B. Public API service

| # | Scenario | Status | Command | Evidence | Limitation |
|---|---|---|---|---|---|
| B1 | Full API suite | passed | `.venv/bin/python -m pytest server/api/tests -q` | **266 passed**, `filterwarnings = error` except one allowlisted Starlette deprecation | |
| B2 | Contract drift gate | passed | `bash scripts/gen-contracts.sh --check` | OpenAPI 3.1 with 14 paths, thirteen JSON Schemas and the generated TypeScript all match. **Proven to fail in both directions**: editing the generated TypeScript exits 1 with a diff, restoring it exits 0. | |
| B3 | Every public endpoint against the seeded release | passed | `test_public_endpoints.py` | | |
| B4 | Service dates, overnight, both DST transitions, calendar exceptions | passed | `test_service_dates.py` | | |
| B5 | Rights and review gates at the API boundary | passed | `test_gates.py` | | |
| B6 | Readiness fails on a missing database, a release mismatch and a corrupt pack | passed | `test_readiness.py` | | |
| B7 | Admin rejects a missing or wrong token, and is absent entirely when disabled | passed | `test_admin.py` | | |
| B8 | Errors, security headers and CORS | passed | `test_errors_and_security.py` | | |
| B9 | Backup and restore produce an identical release id | passed | `test_backup.py` | | |
| B10 | Brand and configuration come from `brand.json` | passed | `test_config_and_brand.py` | no product name, slug or domain is hardcoded anywhere in the package |
| B10a | Offline packs are byte-identical to the endpoint responses | passed | `test_packs.py`, 49 tests | meta, coverage, sources, places, every operator, every stop, every journey detail and every journey result compared byte for byte; now-relative freshness compared structurally |
| B10b | The manifest names exactly the canonical pack set | passed | `test_packs.py` | a stray non-canonical name is a hard failure; no retired staging name survives |
| B10c | Every pack validates against its JSON Schema | passed | `test_packs.py` | |
| B10d | A restored backup serves byte-identical responses | passed | `test_a_restored_release_serves_identical_responses` | database, manifest and every pack wiped, then restored; identical bodies and ETags |
| B10e | An unknown **real** operator id is still rejected after the demo path was added | passed | `test_gates.py` | plus: demo operators refused on a real dataset, `demo-` prefix required, federation number forbidden |
| B10f | `/readyz` refuses to serve the demo release as real data | passed | `test_readyz_refuses_to_serve_the_demo_release_as_real_data` | uses the same coordinate gate with its real-data default, so the two cannot drift |
| B10g | Path traversal on pack filenames | passed | `test_errors_and_security.py` | | |
| B11 | Remote checks against a deployed beta service | blocked | — | — | no public API is deployed; AD-003 deploys the Web release as a static pack client |

### B12 to B20, live HTTP runtime checks against the running service

Run on 30 September 2026 against `uvicorn publicapi.app:build --factory` on
port 8791, serving the compiled demonstration release `f004e5669e013f6f`.
These are not unit tests; they are real requests over HTTP.

| # | Check | Status | Observed |
|---|---|---|---|
| B12 | Service starts and reports its configuration | passed | `service configured … product Poravia, contractVersion 1.0.0, dataMode demo, adminEnabled false` |
| B13 | Security headers on a public response | passed | CSP `default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'; sandbox`, plus `nosniff`, `no-referrer`, `DENY`, CORP, COOP, `Permissions-Policy`, HSTS, `Cache-Control: no-store`, `x-request-id` |
| B14 | `/v1/meta` declares demo mode and real attribution | passed | `dataMode: demo`, coverage note naming Aloria, three attributions including the ODbL NAP dataset |
| B15 | Place disambiguation separates a terminal from its bays | passed | `q=alo` returns `Aloria Central Terminal` as `stop_place` plus bays A1 and A2 as `stop` |
| B16 | Journey search returns results with purchase, freshness and confidence | passed | two results, `purchase.kind: online`, `freshness.state: stale`, `confidence: reviewed` |
| B17 | **Both 2026 DST transitions keep the wall clock** | passed | 29 March returns `+03:00`, 25 October returns `+02:00`, both at 09:00 local |
| B18 | **Overnight journey keeps its earlier service date** | passed | `serviceDate 2026-10-02`, `crossesMidnight true`, departs `2026-10-02T23:40+03:00`, arrives `2026-10-03T01:20+03:00` |
| B19 | The same trip is `25:20:00` in GTFS | passed | `stop_times.txt` row at `25:20:00`, past 24:00 as the specification requires |
| B20 | Calendar exceptions reach the feed | passed | `calendar_dates.txt` carries `20260406,2` removed and `20260411,1` added |
| B21 | Journey detail carries the boarding point, provenance and geometry confidence | passed | bay `A1`, `stepFree true`, review `published`, provenance `manual-review / allowed / reviewed`, geometry `ordered_stops_only` |
| B22 | Pickup and drop-off rules are per stop | passed | first stop `allowed / not_allowed`, last stop `not_allowed / allowed` |
| B23 | Ticket-office fallback where there is no online sale | passed | overnight journey returns `purchase.kind: ticket_office` with a phone, not a booking URL |
| B24 | Offline manifest and GTFS served | passed | manifest lists 9 packs for release `f004e5669e013f6f`; `/v1/gtfs` returns `application/zip`, 2,483 bytes, 9 members |

## C. Rename and brand gates

| # | Scenario | Status | Command | Evidence |
|---|---|---|---|---|
| C1 | No unintended old-brand or placeholder string in tracked source or paths | passed | `bash scripts/check-brand.sh` | **clean across the whole tree** after the `poravia_ktel` rename; allowlist documented in `BRAND-DECISION.md` |
| C2 | No old-brand or placeholder string in the built web artifact, including binaries | not-run | `bash scripts/check-brand.sh --dist apps/web/dist` | runs once the production build exists |
| C3 | Legacy `HODOMAP_` and `SYRMOS_` environment names still honoured | passed | `BrandMigrationCompatibilityTestCase` | 3 tests |
| C4 | TicketWeb gate cannot be flipped by the rename | passed | `test_ticketweb_gate_is_off_unless_explicitly_approved` | all three prefixes checked, both directions |

## D. Mandatory runtime scenarios

The twelve scenarios required for the beta, per platform. **These are filled in
as each client's evidence lands. A blank status means not yet run, and is
recorded as `not-run`, not assumed.**

| # | Scenario | Web | iOS | Android |
|---|---|---|---|---|
| D1 | Clean install and cold launch in el, en and sq; light and dark; system-language change | not-run | not-run | not-run |
| D2 | Search → service date → result → boarding detail → purchase or contact action → return with state preserved | not-run | not-run | not-run |
| D3 | Offline pack download → airplane mode → restart → saved journey still usable with accurate stale labels | not-run | not-run | not-run |
| D4 | Corrupt and interrupted downloads, insufficient storage, failed requests, changed manifest, rollback, expired data | not-run | not-run | not-run |
| D5 | Denied location, notification and file permissions; delayed network; empty coverage; invalid input; unknown deep links; missing maps | not-run | not-run | not-run |
| D6 | Protected ticket import, view and delete; app restart; backup, log and cache inspection with synthetic documents | not-run | not-run | not-run |
| D7 | Notification opt-in and opt-out, rescheduling, cancellation, time-zone change, permission revocation | not-run | not-run | not-run |
| D8 | Date, calendar and DST cases; overnight trips; provenance, rights and review exclusion; quarantine; atomic release behaviour | passed (A4, A5, A11–A13, B4, B5) | not-run | not-run |
| D9 | Fold, unfold or equivalent posture transitions during search, detail, map, a pack download and ticket viewing; rotation and constrained multitasking | not-run | not-run | not-run |
| D10 | Accessibility: real screen-reader checks, focus order, labels, large text, keyboard, contrast, reduced motion, error announcements, non-map route | not-run | not-run | not-run |
| D11 | Production web build in Chromium, WebKit and Firefox; mobile and desktop widths; navigation, reload, deep links; install, offline and update | not-run | n/a | n/a |
| D12 | Backend cold start, migration, backup restore, readiness failure, release mismatch, bounded ingestion, rollback | passed (A1, A9, B6, B9) | n/a | n/a |

### Method note for D9

Layout-continuity runs hold dataset, clock and service date, locale, theme and
network **constant** while changing geometry only, so an unrelated change
cannot hide a state-loss bug. Lifecycle, network and time-change scenarios run
separately, as their own rows.

## E. Untested combinations, stated honestly

These were **not** tested and are not claimed:

- Windows, Linux and ChromeOS host browsers. Only macOS-hosted Chromium, WebKit
  and Firefox are available here.
- Physical iPhone, iPad, Android phone, Android tablet or physical foldable. No
  device is connected; all mobile evidence is simulator and emulator.
- Safari on a physical iOS device, as distinct from the simulator.
- Any Apple platform older than the installed iOS 27 SDK's simulator runtimes.
- The Raspberry Pi acquisition target, which is unreachable from here (EB-07).
- Screen readers other than VoiceOver, TalkBack and the browser screen reader
  available on this host. JAWS and NVDA were not tested.
