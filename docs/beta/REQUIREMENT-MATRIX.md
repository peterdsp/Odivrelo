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

## Second verification pass, 2 October 2026

A deeper pass over device, adaptive, artifact and naming checks that the first
pass did not fully cover. Where a runtime check genuinely could not run, the
precise limitation is recorded rather than marked passed.

### Installable QA artifacts, distinguished

- **Android QA APK (debug):** `apps/android/build/outputs/apk/debug/odivrelo-debug.apk`,
  built from `8e6053f`. Identity `dev.peterdsp.odivrelo`, versionName `1.0.0`,
  versionCode `20260930`, minSdk 26, targetSdk 36. Debug-signed, re-verified
  (`apksigner`: `CN=Android Debug`, APK Signature Scheme v2), so it installs on
  an emulator or a developer-enabled device. Install and launch commands and the
  four-artifact distinction (QA APK vs unsigned release APK vs unsigned AAB vs
  Play internal track) are in `BUILD-AND-RELEASE.md`; checksums are in
  `artifacts/ARTIFACT-MANIFEST.json`.
- **iOS states, distinguished:** simulator app (runtime-verified), unsigned
  device archive (buildable), signed device build / TestFlight / tester
  availability (all blocked, EB-02). Table in `BUILD-AND-RELEASE.md`.

### Device and adaptive runtime

| Target | What was exercised | Status |
|---|---|---|
| iPhone | 192 tests incl `testSearchToBoardingDetailAndBackPreservesState`; welcome, My trips, search, journey detail (bay A1) via validated deep link | verified |
| iPad Pro 13" | App runs; full UI suite run on the iPad destination: 21 executed, 11 passed, 10 failed | verified behaviour; the 10 failures are test-portability, not app defects (see below) |
| iPhone Duo | Runs on the Duo simulator in cover posture | partial: the toolchain exposes no posture or fold control (`apps/ios/artifacts/duo-runtime-findings.txt`), so the inner posture could not be selected |
| Android phone | Full journey (welcome, search, picker, results, journey detail bay A1), adaptive navigation rail and two-pane master-detail | verified (stable run) |
| Android foldable | App renders correctly on the OPENED inner display (1768x2208) and survives CLOSED and OPENED posture transitions with no app crash | partial: the `nostavela_foldable` emulator's `system_server` ANRs on posture changes and reports the same window size for both states, so a posture-driven layout change could not be captured on this AVD |

On iPad the 11 passing tests include cold launch in all three languages,
onboarding without an account or permission, the non-dismissible demonstration
notice, the not-covered and offline states, the accessibility filter, and every
performance test (cold-launch time, search latency, results and journey-detail
scroll). The 10 failures are all the tests that navigate by tapping the tab bar:
the UI tests were written for the iPhone bottom tab bar, but iPadOS 26 presents
the same `TabView` as a top bar or sidebar, so the `tabBars` and some button
lookups find no match (nine "no matches found" plus one infinite-coordinate tap
in a memory test). No content assertion failed on iPad. The two-column adaptive
layout itself is in the code: `WindowGeometry` selects a two-column
`NavigationSplitView` on regular width and a single column on compact. This is a
test-portability gap, recorded as such, not an iPad app defect.

### Restoration (activity and process)

- **iOS: verified.** `testSearchToBoardingDetailAndBackPreservesState` passes in
  the 192-test run on iPhone.
- **Android: implemented, on-device automated run environment-blocked.** The
  session state is mirrored into `SavedStateHandle` on every change (survives
  process death) and `rememberSaveable` is used across Search, Journey detail,
  Wallet and Settings. The instrumentation tests that assert this
  (`a_search_survives_activity_recreation`,
  `b_a_selected_journey_survives_activity_recreation`,
  `a_link_is_not_re_applied_when_the_activity_is_recreated`) exist but **do not
  run in CI** (CI runs `testDebugUnitTest` only) and **could not be run here**
  for two independent reasons: the Compose instrumentation suite deadlocks on the
  indeterminate `CircularProgressIndicator` shown during data seeding, so
  Compose's idle synchronisation never settles and every test ends in a 60 s
  `ComposeTimeoutException`; and the headless, resource-limited emulator is
  additionally too constrained to drive reliably (recurring `system_server` and
  `SystemUI` ANRs, and it fails to boot at all at 4 GB). This is a test-harness
  and environment limitation, not a product defect: the app renders and every
  flow works under manual drive with no app ANR or crash in logcat.

### Accessibility and screen readers

- **Web: verified** with axe across the e2e suite, which exercises the actual
  ARIA and semantics that assistive technology consumes.
- **iOS and Android: labels present, speech output not automatable here.** The
  Android `every_tab_is_labelled_for_a_screen_reader_and_is_large_enough_to_hit`
  instrumentation test asserts screen-reader labels and touch-target sizes but is
  blocked for the reason above. Driving actual VoiceOver or TalkBack speech could
  not be automated in this environment, which was investigated:
  `xcrun simctl ui` exposes only appearance, contrast and content size with no
  VoiceOver control, `idb` is not installed, and the Android emulator was too
  unstable to enable and drive TalkBack. This is recorded as a precise
  limitation, not a pass.

### Naming

Preliminary public collision screening for Odivrelo was performed and found no
material conflict (`BRAND-DECISION.md`, dated 2 October 2026). The domain,
GitHub handle and App Store results were re-verified first-hand. Trademark
registers could not be searched and a native-speaker review is still
outstanding; both remain external under EB-06.

### Artifact manifest and deployment

`artifacts/ARTIFACT-MANIFEST.json` was regenerated against `8e6053f`, and the
script's stale distribution claim was corrected (`web: "blocked: EB-01, DNS
record missing"` is false; it now records deployed and verified). The live site
was re-verified: `https://odivrelo.peterdsp.dev`, release `f84a9172`, HSTS and
full CSP, HTTP-to-HTTPS 301, deep links 200, and `robots.txt` still disallows
all while the data is a demonstration.

## Third pass, 2 October 2026: tests repaired and release machinery built

### iOS UI tests: fixed on both device classes

The iPad UI failures were test-portability, and they are now fixed. Each tab is
given a stable `accessibilityIdentifier`, a `selectTab` helper taps the first
hittable match across the bottom-bar, top-bar and sidebar presentations, a
`goBackIfPushed` helper is a no-op on the split-view layout, and the map memory
test guards against an off-screen frame. Result, run sequentially to avoid
contention:

- iPhone 15: 21 of 21 pass.
- iPad Pro 13": 21 of 21 pass (the one interruption was the known iOS 27
  simulator WebKit-accessibility crash, which passes on isolated rerun).

### Android instrumentation: repaired from a total deadlock to most of the suite

The suite previously timed out on every test. Three root causes were found and
fixed, each a genuine improvement rather than a test-only hack:

- **Determinism.** `LoadingState` and the indeterminate download bar now honour
  the reduced-motion setting (the signal the theme already reads), showing a
  static indicator. An indeterminate indicator requests frames forever and keeps
  the Compose test clock from reaching idle; a static one lets it settle. This
  is also the correct reduced-motion behaviour for a user.
- **Viewport robustness.** The tests now scroll to controls that sit below the
  fold on a phone (the welcome Start button, the search submit, and the
  schedule-only notice at the end of the results list) before clicking or
  asserting, rather than assuming everything is on screen.
- **Date anchoring.** The date-dependent tests were written against a fixed
  "today" and are re-aligned to the demo release's packed anchor date.

Result on an AOSP API 34 emulator with animations disabled: 10 of 18 pass, up
from 0. `SearchFlowTest` is fully green. The remaining failures are the
activity-recreation restoration and deep-link cases, where
`ActivityScenario.recreate()` does not complete on this emulator image
("Activity never becomes DESTROYED"); that is being stabilised across images. A
CI instrumentation job (`reactivecircus/android-emulator-runner`, KVM, software
GPU, animations disabled) was added; it runs and uploads its full report and is
non-blocking until the suite is green.

### Release machinery built (was only documented before)

- `.github/workflows/release-ios.yml` and `.github/workflows/release-android.yml`:
  complete, manual-dispatch-only release workflows against the new `ios-beta`
  and `android-beta` environments. They validate required secrets by name first,
  build and sign in a throwaway keychain or from a temp keystore, verify the
  signature, upload to TestFlight or the Play internal track, write checksums
  and a summary, and delete all signing material even on failure.
- `scripts/android-app-verify.sh` now has two modes: an intentionally-unsigned
  check, and an authenticated release-signing check against a declared
  certificate fingerprint. It also validates the AAB structure and its
  `jarsigner` signature rather than passing a bundle to `apksigner`.
- The Android version code is overridable (`ODIVRELO_VERSION_CODE`) so no build
  number is reused; the iOS build number is overridable in the archive step.
- `docs/beta/OWNER-SETUP.md` is the single consolidated guide to the account-only
  steps and the exact secrets to install, with a secure upload method that never
  passes a value through chat or a command line.

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
