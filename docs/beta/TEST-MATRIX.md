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

## S. Shared Kotlin Multiplatform core

Verified independently on 30 September 2026 with
`./gradlew :shared:core:testDebugUnitTest :shared:features:testDebugUnitTest`,
then by parsing the JUnit XML rather than trusting the console.

| # | Scenario | Status | Evidence |
|---|---|---|---|
| S1 | Core and feature suites, JVM debug | passed | **158 tests, 0 failures, 0 errors** |
| S2 | Same suites, JVM release | passed | 158 tests, 0 failures, 0 errors |
| S3 | Same suites, iOS simulator arm64 | passed | 158 tests, 0 failures, 0 errors |
| S4 | Both 2026 Greek clock changes | passed | the same wall-clock span is 75 real minutes in October and 195 in March |
| S5 | Overnight keeps the earlier service date | passed | |
| S6 | SHA-256 against the four standard vectors | passed | |
| S7 | A corrupted pack that is still valid JSON is refused on digest alone, and not retried | passed | |
| S8 | A foreign-release pack is refused despite a valid digest | passed | |
| S9 | A day pack covering the wrong date is refused | passed | |
| S10 | Bounded retry, exactly three, and `Range` resume | passed | |
| S11 | Cancellation installs nothing | passed | |
| S12 | Saved trips survive the v1 to v2 migration, new column reads null not false | passed | |
| S13 | A saved trip is still readable after its release is removed | passed | |
| S14 | **No pack for a date is not "no service on that date"** | passed | four tests, including one asserting zero HTTP requests for a date the manifest omits |
| S15 | **Transport equality**: bundled vs downloaded, and static origin vs API origin | passed | `TransportEqualityTest` over search, journey detail, stop detail, meta, coverage, sources and place search |
| S16 | A first run downloads only the day it searched, not every published date | passed | |
| S17 | `iosX64` execution | not-run | compiles and links into the simulator slice, but an x86_64 simulator cannot boot on this arm64 host. Stated, not counted as a pass. |
| S18 | XCFramework produced with both slices | passed | `ios-arm64` and `ios-arm64_x86_64-simulator`, static, 1811 `swift_name` attributes |
| S19 | `:apps:android:assembleDebug` links both shared modules | passed | 22 MB debug APK carrying exactly the 11 packs the manifest names, pruned from 124 files and 748 KB to 11 and 54 KB |

## I. iOS and iPadOS

Build commit recorded in the archive: `9808c1712a1f`. Host Xcode 27.0, iOS 27.0
SDK, Swift 6.4.

| # | Scenario | Status | Evidence |
|---|---|---|---|
| I1 | Unit and UI tests | passed | **167 tests in 19 suites**, 0 failures |
| I2 | Debug build and run, iPhone 15 / iOS 27.0 | passed | `BUILD SUCCEEDED` |
| I3 | Release build, device target | passed | `BUILD SUCCEEDED`, arm64, 17 MB |
| I4 | Release archive | passed | `ARCHIVE SUCCEEDED`, 35 MB with dSYM |
| I5 | Release validation suite | passed | 40 checks, entitlements, usage descriptions, privacy manifest, bundle identity, localisations, resources |
| I6 | **Shared core linked in Release, not merely referenced** | passed | plain Release binary: 4009 Kotlin symbols, 272 `PoraviaCore*` Objective-C classes, `createPoraviaCore` present. Archive binary is stripped, so proven through 1915 symbols in the dSYM plus the class table. **Zero fixture symbols in either.** |
| I7 | Happy path in el, en and sq, light and dark, largest accessibility size | passed | screenshots, each read back |
| I8 | Twelve failure and edge scenarios | passed | offline, not-covered, empty results, partial coverage, stale release, served-from-cache, core-unavailable, corrupt download (fails digest, offers retry, installs nothing), **interrupted download fails then resumes to Installed**, insufficient storage, trips, wallet, offline packs, settings |
| I9 | iPad adaptive layout | passed | onboarding constrained to a readable column; populated two-column split with search and results left, detail right |
| I10 | **iPhone Duo runtime** | **partial** | Two integrated displays measured: screen 1 `1398×2034 @3` = 466×678 pt, screen 3 `2007×2853 @3` = 669×951 pt, black throughout, i.e. cover posture. On screen 1 the system hands the app a window narrower than the display and the app **never draws into the ~86 pt reserved strip**, verified at normal and largest accessibility size. **No posture control exists in this toolchain**: `simctl ui` offers only appearance, contrast and content size, and the device profile carries no posture, fold, hinge or reserved keys, so the unfolded posture could not be selected. Findings in `apps/ios/artifacts/duo-runtime-findings.txt`. |
| I11 | Reserved-region API | passed, as a negative result | `SwiftUI.ReservedRegion` and `GeometryProxy.reservedRegions` exist as linker symbols in `SwiftUICore.tbd` but are **absent from the public swiftinterface**; a typecheck fails. The app reports nothing rather than guessing, and never infers posture from aspect ratio. |
| I12 | **Runtime behaviour against the real core** | **failed, fix in progress** | A fresh install with no packs terminates the process: a `PoraviaException` thrown from a suspend function with no `@Throws` is never converted to `NSError`, so the Kotlin runtime kills the app before Swift can catch it. Every runtime screenshot therefore used a fixture data source. See the defect note below. |
| I13 | iPad multitasking, Split View and Stage Manager | not-run | could not be driven from this toolchain |
| I14 | VoiceOver | not-run | could not be enabled. Three accessibility hierarchy dumps captured instead, including one at AX3XL, which show a journey card exposing one correctly combined element. **A hierarchy dump is not a VoiceOver run.** |
| I15 | Performance: launch, search latency, scrolling, map, memory, size | not-run | the machine reached load average 251 with simulators contending. **No figures were invented.** |
| I16 | Signing and distribution | blocked, EB-02 | two Apple Development identities, no provisioning profile, no App Store Connect credentials; `exportArchive` fails with `Failed to Use Accounts`. A simulator app is not an installable iPhone beta. |

### Defect I12, open

The shared core's exported suspend functions carry no `@Throws`, so on
Kotlin/Native an exception outside the declared list terminates the process
instead of bridging to `NSError`. It is not fixable from Swift. The fix is
`@Throws(PoraviaException::class, CancellationException::class)` across the
eighteen exported members, plus a test that fails when an exported throwing
member lacks the annotation, and a test for the first-run state itself — no
packs, no API — which was untested, which is why a fresh install crashed while
158 core tests passed.

Until it lands, **the iOS build evidence is stronger than the iOS runtime
evidence**, and that asymmetry is stated rather than averaged away.

### Defects found and fixed during iOS verification

Each caught by a test or by reading a screenshot back, not by assuming.

- **Path traversal in the deep-link validator.** `URLComponents.path`
  percent-decodes, so `..%2F..%2Fetc` arrived as `../../etc` and `..` passed
  the character check. Now rejected; a legitimate dotted id still resolves.
- **Thirteen counted strings had no plural form** ("1 journeys"). Now proper
  String Catalog plurals, shipped as `.stringsdict` in all three languages.
- A Greek navigation title truncating; a badge overflowing its card; format
  strings used as row labels; `0h 45m`; `Zero KB`; a heading styled as body
  text; an unused `UIBackgroundModes` declaration; and `strings.source.json`
  and `__LLVM_COV` shipping inside the Release bundle.
- **Three bugs in `scripts/ios-ci-build.sh`**, which I wrote. The worst: it
  generated from the plain project spec, so it built with **no shared core at
  all** and reported success. It now bootstraps properly and fails if the built
  binary lacks the core.

## W. Web application

All of the following ran against the same artifact: release `3d7f0bf5902b38f6`,
synced with `scripts/web-sync-packs.sh` and built immediately before the
end-to-end run.

| # | Scenario | Status | Evidence |
|---|---|---|---|
| W1 | Lint | passed | `eslint . --max-warnings 0`, clean |
| W2 | Type check | passed | app and node projects, clean |
| W3 | Unit and integration tests | passed | **171 tests in 8 files** |
| W4 | Production build | passed | app 1744.3 KiB raw, 469.0 KiB gzip; data 786.1 KiB; **26 prerendered entry points**, 11 static plus 14 from the release plus the root |
| W5 | Performance budgets | passed | initial JS 162.1 KiB of a 180 KiB budget; precache 638.8 KiB of 800 KiB. MapLibre's 277.3 KiB is lazy and excluded from precache. |
| W6 | Playwright, Chromium desktop | passed | 92 tests |
| W7 | Playwright, Chromium mobile | passed | 92 tests |
| W8 | Playwright, WebKit desktop | passed | 90 passed, 2 skipped |
| W9 | Playwright, WebKit mobile | passed | 90 passed, 2 skipped |
| W10 | **Accessibility, axe-core** | passed | **zero violations** across 16 routes × 2 themes × 4 projects. No excluded regions, no disabled rules. |
| W11 | Deep links return a real 200 | passed | `/`, `/search`, `/settings`, `/operators`, `/operators/<id>`, `/offline`, `/stations`, `/coverage` all 200 through a Pages simulator; `/nope` returns 404. `e2e/prerender.spec.ts` fails if a router route has no entry point. |
| W12 | `noindex` ships in the bytes | passed | default-deny in every prerendered page; `useHead` only ever upgrades, and only when the route is indexable **and** `dataMode` is `real` |
| W13 | Cross-source equality | passed | deep equality through the pack source and the API source for every place pair × service date, every stop × date, every operator, every journey detail, and each filter variant |
| W14 | **No offline data is not no service** | passed | three tests, including one asserting a packed-but-empty date is **not** reported as missing data |
| W15 | Rename gate on the built artifact | passed | `check-brand.sh --dist apps/web/dist` reports the artifact clean |
| W16 | No third-party request | passed | asserted by test. The map loads no basemap; there is no tile provider. |
| W17 | Screenshots from the production build | passed | **154 PNGs**, 39 scenarios × 4 projects, named `scenario__project__viewport__date.png` |
| W18 | **Firefox, entirely** | **not-run** | the Playwright Firefox build (155.0 Nightly, `firefox-1543`) will not start on this host: `Could not find profile folder`. It fails identically outside Playwright with a valid pre-made profile, with a forced reinstall, with `TMPDIR` redirected, and with the sandbox disabled. A broken browser build on this machine, not an app fault. Both Firefox projects are configured; **CI installs Firefox cleanly and must run them.** |
| W19 | Offline document reload in WebKit | **not-run** | Playwright's WebKit intercepts requests before the service worker, so a document reload cannot be served from cache. Named in the test title so it reads as a limitation. Offline is still covered on every engine through in-app navigation. |
| W20 | The skip link as first Tab stop, WebKit | **not-run** | Safari omits links from the Tab order. The keyboard contract the app owns, every control focusable, named and operable, is asserted on every engine. |
| W21 | Lighthouse lab profile | not-run | bundle and precache sizes are measured and budgeted; no lab performance profile was captured |
| W22 | Screen readers | not-run | axe is automation, not a VoiceOver or NVDA pass |

### Defects found and fixed during Web verification

Beyond the 404 deep links: `hidden` overridden by `display: grid`, leaving a
listbox focusable while marked hidden; a dark-mode contrast failure putting
1.41:1 text on an amber chip; the demonstration banner outside every landmark,
because `role="note"` is not one; a null `directoryUrl` throwing from
`new URL()` and taking down a whole page; `upgrade-insecure-requests` in the
meta CSP breaking WebKit on http origins; offline broken despite installed
packs, because the manifest itself was network-only; download resume not
resuming, because it recorded files complete without persisting bytes; a
preference write reporting success with `localStorage` absent; a skipped
heading level; and a search returning only terminals so the results heading
printed raw stop ids.

### Defect fixed at the source, from the Web report

`coverage.note` and `notCovered` were English-only strings rendered on Greek
and Albanian pages. Now localised in `publicapi/copy.py`, which refuses a
partial translation rather than falling back to English in front of a user.
Verified end to end in the generated packs. Three tests hold it, including one
asserting the three languages are not the same sentence repeated.

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

- Windows, Linux and ChromeOS host browsers. Only macOS-hosted engines are
  available here.
- **Firefox on any platform.** The Playwright Firefox build will not start on
  this host; see W18. This is the largest single untested combination in the
  Web release.
- Physical iPhone, iPad, Android phone, Android tablet or physical foldable. No
  device is connected; all mobile evidence is simulator and emulator.
- Safari on a physical iOS device, as distinct from the simulator.
- Any Apple platform older than the installed iOS 27 SDK's simulator runtimes.
- The Raspberry Pi acquisition target, which is unreachable from here (EB-07).
- Screen readers other than VoiceOver, TalkBack and the browser screen reader
  available on this host. JAWS and NVDA were not tested.
