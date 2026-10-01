# Requirement matrix, verified against the running implementation

Every requirement checked against what the code and the running apps actually
do, not against a prior summary. Each row carries first-hand evidence a reader
can reproduce. Where a requirement is not met, it says so and names the exact
external blocker.

Verified: 2 October 2026, on this machine (macOS, Xcode 27, Node 24, Python
3.12 venv, JDK 17 under `~/Library/Android/jdk`, Android SDK with API 34
emulator). Source at branch tip `09e447c` (the content merged to `main` as PR
`#17`, `1ece69e`).

## The four things kept separate

The user asked to tell these apart. They are not the same claim.

1. **Shipped fix.** The journey detail is scoped to the searched leg. PR `#17`
   is merged to `main` and live.
2. **Functional demonstration.** The whole product works end to end on an
   invented dataset (region Aloria). This proves the software behaves, on web,
   iOS and Android. It does not prove real transport coverage.
3. **Verified real-data product.** Not achieved. No Greek corridor is published
   with real data. Blocked by [EB-04](EXTERNAL-BLOCKERS.md). See the
   [operator coverage ledger](OPERATOR-COVERAGE-LEDGER.md).
4. **Released mobile apps.** Not achieved. iOS and Android both build and are
   runtime-verified, but neither is signed or installable by a tester, because
   no signing identity or store account is available. Blocked by
   [EB-02](EXTERNAL-BLOCKERS.md) and [EB-03](EXTERNAL-BLOCKERS.md).

## Per-platform test and runtime evidence, run today

| Platform | What was run | Result |
|---|---|---|
| Backend, public API | `pytest server/api/tests` | 275 passed |
| Backend, data compiler and GTFS | `python -m unittest` in `server/ktel-staging` | 22 passed |
| Backend, acquisition pipeline | `python -m unittest` in `server/tests` (PYTHONPATH=src) | 11 passed |
| Shared KMP core | `./gradlew :shared:core:allTests` (JVM and iOS targets) | 414 tests, 0 failures, BUILD SUCCESSFUL |
| Web, static checks | `npm run typecheck`, `npm run lint` | typecheck clean, lint clean (0 warnings) |
| Web, unit | `npm run test` (vitest) | 178 passed |
| Web, end to end | `npm run e2e` (Playwright, Chromium, Firefox, WebKit, mobile and desktop widths, with axe) | 384 passed, 4 skipped |
| Web, production build | `npm run build` | PWA built, `dataMode=demo`, noindex, 26 prerendered entry points |
| iOS and iPadOS | `scripts/ios-ci-build.sh` (iPhone 15 simulator) | Debug build succeeded, shared core linked (3948 `OdivreloCore` symbols), 192 tests passed, 0 failed |
| Android | CI-built `odivrelo-debug.apk` installed on the API 34 emulator, full journey driven by hand | Launched clean (no crash), welcome, search, picker, results, journey detail all exercised |
| Live website | `curl` deep links, manifest, robots; `scripts/verify-deployment.sh` ran green in CI on deploy | `https://odivrelo.peterdsp.dev` serves release `f84a9172508256f3`, deployed after PR `#17` |

Backend total: 308 tests. Shared core: 414. iOS: 192. Web: 178 unit plus 384
end to end. All green.

Evidence artefacts live in the gitignored artefact directories, by the repo's
convention: iOS result bundles in `apps/ios/artifacts/`, fresh Android
screenshots in `apps/android/artifacts/screenshots/` dated `2026-10-02`
(welcome, adaptive search rail, two-pane results, journey detail with boarding
bay). Web evidence is reproducible from the commands above.

## Requirement by platform

Legend: `met` with evidence, `met (demo)` means the behaviour is real but runs
on the demonstration dataset, `blocked` with the external blocker id.

### Website (React or TypeScript, PWA)

| Requirement | State | Evidence |
|---|---|---|
| First launch, no account, no mandatory permission, three languages | met | e2e launch spec: cold launch in el, en, sq; no permission prompt |
| Search, place disambiguation, service date, results, empty and error states | met (demo) | e2e journey and states specs; live `/search`, `/results` return 200 with correct titles |
| Journey detail: boarding point, provenance, freshness, confidence | met (demo) | e2e journey spec; the scoped-leg fix shipped in PR `#17` |
| Official booking handoff, contact fallback, never sells tickets | met (demo) | e2e journey spec; "does not sell tickets" shown on first launch and purchase surfaces |
| Offline packs: integrity, atomic install, interrupted-download recovery, update, rollback, delete | met | e2e offline spec: download, corrupt-pack rejection, interrupted-and-resumed, delete with freed-storage report |
| Saved trips usable offline with cached release and age shown | met | e2e offline spec: saved trip usable with network off, age labelled |
| Travel wallet: local only, never uploaded or logged, deletion deletes | met | e2e states spec: wallet import, type validation, real delete, eviction honesty |
| Reminders opt-in, permission-denied handled, no barcode in payload | met | e2e states spec: denied notification handled, background-reminder limitation stated plainly |
| Every control does something or explains its unavailability | met | e2e covers states; lint and typecheck clean |
| Scheduled, predicted, live kept distinct; only scheduled present and said so | met | journey detail states "timetable only, no live vehicle position" |
| Accessibility: screen reader, focus order, large text, keyboard, contrast, reduced motion, non-map route | met | e2e accessibility spec with axe; 200 per cent text, keyboard-only operation, map-closed path all pass |
| Adaptive layout, state preservation across geometry | met | e2e runs mobile and desktop widths across three engines |
| Deployed, verified, at the canonical hostname | met | `https://odivrelo.peterdsp.dev` live, release `f84a9172`, robots disallow (demo), content-addressed manifest |
| Real-data product | blocked | EB-04; ships the labelled demonstration dataset |

Firefox note carried from the test matrix: one service-worker reload case is
skipped on WebKit by Playwright design, documented in the e2e offline spec.

### iOS and iPadOS (SwiftUI, MapKit, shared KMP core)

| Requirement | State | Evidence |
|---|---|---|
| Builds for the simulator with the shared core actually linked | met | `ios-ci-build.sh`: 3948 `OdivreloCore` symbols in the built binary |
| Unit and UI tests pass | met | 192 tests, 0 failures, result bundle in `apps/ios/artifacts/tests.xcresult` |
| Release configuration compiles (unsigned) | met | `ios-ci-build.sh --release` path builds unsigned; CI `iOS build and tests` job green |
| Adaptive: iPhone, iPad, iPhone Duo | met | simulators present and exercised; Duo runtime coverage reported as far as the installed runtime allows, per the test matrix |
| Signed archive, TestFlight, testers | blocked | EB-02: no Apple team, certificate, provisioning profile, or App Store Connect key |

A dead `?? "no message"` on a non-optional `String` in
`CoreAdapter/CoreErrorTranslation.swift` was found during the build and fixed;
the file now compiles with no warning (`BUILD SUCCEEDED`).

### Android (Jetpack Compose, shared KMP core)

| Requirement | State | Evidence |
|---|---|---|
| Builds debug APK, release APK, release AAB | met | CI `Shared core and Android` job green; artefacts: `odivrelo-debug.apk`, `odivrelo-release-unsigned.apk`, `odivrelo-release.aab`, proguard `mapping.txt` |
| Installs and launches on the emulator, no crash | met | API 34 emulator: `MainActivity` resumed in about 1.3 s, no `AndroidRuntime` or FATAL log |
| First launch, three languages, demo notice | met | welcome screen in Greek, language switch to English verified, non-dismissible demo notice present |
| Search, place picker (terminal vs bay), empty state | met (demo) | picker shows "Aloria Central Terminal" and "bay A1" distinctly; a non-matching query shows "no place found" |
| Results with boarding bay, operator, fare, badges | met (demo) | 09:00 Aloria Central Terminal bay A1 to 12:10 Oravo Junction, 3h 10m, operator, indicative fare 18.00 EUR, Reviewed and Step free badges |
| Journey detail: boarding point, operating day, freshness, confidence, no fake live | met (demo) | detail shows bay A1, operating day, "Stale, checked 11 days ago", Reviewed, Approximate, "No live tracking: timetable only" |
| Adaptive: nav rail and two-pane master-detail on wide windows | met | the search and results screens render a left navigation rail and a results-plus-detail two-pane layout |
| Signed release, Play internal track, testers | blocked | EB-03: no release keystore, no Play Console app record |

Local note, not a product blocker: a local Android or Kotlin build needs
`JAVA_HOME` pointed at the bundled JDK 17 at
`~/Library/Android/jdk/jdk-17.0.20.1+1/Contents/Home`. It is not on `PATH` and
`/usr/libexec/java_home` does not find it, so a plain `./gradlew` fails until
`JAVA_HOME` is set. CI uses its own JDK and is unaffected.

### Backend and ingestion (Python, FastAPI, SQLite)

| Requirement | State | Evidence |
|---|---|---|
| Reproducible packaging, health and readiness checks | met | `/healthz`, `/readyz`; pinned dependencies |
| Only rights-cleared and reviewed rows reach a public release | met | tests prove a `permission_pending` row stays out even after approval, and an unreviewed candidate never appears |
| Candidate and public datasets separate | met | two databases; public routes open the compiled database read-only |
| Immutable content-addressed releases, manifest written last, rollback | met | release-and-GTFS tests: corrupt-pack and missing-pack detection, rollback restores the previous manifest |
| Europe/Athens service dates, past-midnight, GTFS beyond 24:00, DST | met | both 2026 DST transitions tested; past-midnight renders as 25:10:00 |
| Production acquisition on the Raspberry Pi | blocked | EB-07: the Pi is not reachable from here; a locally runnable equivalent and the deploy script are delivered |

## Remaining external blockers

Reconfirmed on 2 October 2026. Full detail and the exact unblocking action per
entry in [`EXTERNAL-BLOCKERS.md`](EXTERNAL-BLOCKERS.md).

| ID | Blocked capability | Needs |
|---|---|---|
| EB-02 | an installable iPhone beta | Apple signing identity and App Store Connect access |
| EB-03 | Play internal-track distribution | Android release keystore and Play Console access |
| EB-04 | a real-data beta | operator reuse rights plus fresh, boarding-point-level data (see the coverage ledger) |
| EB-05 | the product validation claim | five recruited travellers, after EB-04 |
| EB-06 | name confidence beyond screening | a professional trademark search and native-speaker review |
| EB-07 | the production acquisition deployment | access to the Raspberry Pi |

EB-01, the public Web hostname, is resolved: the site is live and verified.

## Conclusion

Client engineering across web, iOS and Android is implemented and
runtime-verified on real builds, and the backend and shared core pass their full
suites. The website is deployed and verified at the canonical hostname. What is
not done is gated on external access that this environment does not have:
signing credentials for the two mobile stores, and documented operator rights
plus a fresh boarding-point-level source for any real Greek corridor. The beta
is an honest, working, limited-coverage demonstration, not a validated
national real-data product, and every client says so on screen.
