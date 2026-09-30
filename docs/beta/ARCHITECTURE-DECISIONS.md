# Architecture decisions, beta 1.0.0

Dated decisions. Each one is the current authority for its area. Earlier
documents are sequenced behind these, not deleted.

---

## AD-001, 30 September 2026: scope expands from a web-first one-corridor pilot to a multi-platform beta

**Context.** `docs/PILOT_DECISION.md` (6 September 2026) approved a small,
web-first, one-corridor pilot and explicitly deferred native iOS and Android
apps, national coverage and a deployed service until Gate D2 passed.

**Decision.** The product owner's beta delivery instruction expands engineering
scope to working iOS, Android and Web applications, a shared mobile core,
backend services, operations and distribution preparation, targeting a public
Web release `1.0.0`.

**What this does and does not change.**

- It changes the **engineering** scope only.
- It does **not** pass Gate D1 (the corridor data check) or Gate D2 (the
  five-traveller validation). Neither has been run.
- It does **not** establish source reuse rights, human data review, or national
  coverage. Those remain evidence claims with their own gates, recorded in
  `RELEASE-READINESS.md`.
- The pilot's evidence, tickets and history are preserved unchanged.

**Consequence.** The beta is product-complete engineering over a clearly
labelled demonstration dataset. It is not a validated product and not a
national dataset. Every client says so on screen.

---

## AD-002, 30 September 2026: the beta ships an invented demonstration dataset, not Greek coach data

**Context.** `data/operators/registry.json` records all 62 KTEL federation
operator directory sources as `rightsStatus: unknown`. Only the national access
point catalog is `permitted`, and PHASE0-02, which would establish that
dataset's precise licence, is unstarted and depends on an external response.
`ktel_publish.compile_public_database` correctly refuses to copy any row whose
source is not `allowed`, so no real Greek timetable can lawfully reach a public
release today.

**Decision.** The beta ships the engineering demo path described in the
delivery instruction: an invented region, **Aloria**, with invented operators,
terminals and services, whose coordinates are deliberately placed in open ocean
near 0°N 0°E so no row can be mistaken for a real terminal.

**Constraints that follow.**

- `dataMode` is a first-class field in the public contract. While it is `demo`,
  every client shows a persistent, non-dismissible notice, in all three
  languages, that Aloria does not exist and that no departure shown is real.
- The web build emits `noindex` and ships no sitemap while in demo mode.
- No store screenshot, listing or release note may imply real coverage.
- Flipping `dataMode` to `real` removes the notice and enables indexing with no
  other code change, so the switch is a data and rights event, not a rewrite.

**This does not satisfy real-data beta readiness.** The exact missing evidence
is recorded in `EXTERNAL-BLOCKERS.md`.

---

## AD-003, 30 September 2026: the public Web 1.0.0 is a static release-pack client

**Context.** The project's existing convention is `<appname>.peterdsp.dev`
served by GitHub Pages behind Cloudflare. GitHub Pages is static hosting: it
runs no server process and sets no response headers.

**Decision.** The deployed Web application reads the **published,
content-addressed release packs** as static files from its own origin, rather
than requiring a live API. `server/api` remains the canonical read API for
mobile clients and for any future dynamic need, and both consume the identical
contract in `data/schemas/CONTRACT-v1.md`.

**Why.** Every traveller flow in this release, that is search, journey detail,
boarding point, operator and station pages, offline packs and saved trips, is a
read against an immutable release. A static client serves all of them, works
offline by construction, needs no backend uptime for the release gate, and
costs nothing to host. The alternative, paying for and operating a public API
purely to re-serve immutable files, would add a failure mode without adding a
feature.

**Consequences.**

- The web data layer is one interface, `PublicDataSource`, with two
  implementations, `StaticPackSource` and `HttpApiSource`, both passing the
  same test suite. Switching is configuration.
- Pack files are content addressed, so they are served
  `immutable, max-age=31536000` safely.
- GitHub Pages cannot set security headers. The content security policy is
  applied through a `<meta http-equiv>` element and the limitation is stated
  honestly in `apps/web/README.md`, rather than claimed as a server header.
- Booking handoff is an external navigation to the operator, so no backend is
  involved.

---

## AD-004, 30 September 2026: service-date and timezone semantics are resolved once, server-side

**Context.** The delivery instruction is explicit that each UI must not invent
its own timetable engine.

**Decision.** `Europe/Athens` service-date semantics, midnight crossings, GTFS
times at or beyond 24:00, calendar exceptions and daylight-saving transitions
are implemented once in `server/ktel-staging/hodomap_ktel/ktel_gtfs.py` and
encoded in the published payloads. The Kotlin Multiplatform core is the single
client-side implementation for mobile; the Web client reads the same published
fields.

**Method.** A stop time is an offset from **noon minus twelve hours** on the
service date in `Europe/Athens`, which is the GTFS definition and the reason
daylight-saving days and past-midnight departures come out correct. Both 2026
Greek transitions, 29 March and 25 October, are covered by tests.

**Consequence.** A journey that departs before midnight and arrives after it
keeps the earlier service date. Clients must not recompute a service date from
an arrival instant; the contract says so and the tests enforce it.

---

## AD-005, 30 September 2026: locked dependency versions and module ownership

Chosen once, on current compatibility evidence, and locked. The stack is not
replaced again during this delivery.

| Area | Choice | Owner |
|---|---|---|
| Ingestion, compiler, GTFS, release packs | Python 3.12, stdlib `sqlite3`, `zoneinfo` | `server/ktel-staging/` |
| Public read API and private review surface | Python 3.12, FastAPI, uvicorn, read-only SQLite | `server/api/` |
| Public contracts | OpenAPI 3.1 and JSON Schema 2020-12 in `data/schemas/`, TypeScript generated from them with a CI drift gate | `data/schemas/` |
| Shared mobile core | Kotlin Multiplatform, kotlinx coroutines / serialization / datetime, Ktor, SQLDelight, JDK 21 toolchain | `shared/core`, `shared/features` |
| Android | Jetpack Compose, Material 3, `androidx.window` adaptive APIs, minSdk 26, target/compileSdk 36 | `apps/android/` |
| iOS and iPadOS | SwiftUI, MapKit, Swift 6, iOS 17 deployment target, iOS 27 SDK | `apps/ios/` |
| Web | React 19, TypeScript strict, Vite 7, React Router 7, MapLibre GL JS, Workbox service worker, IndexedDB | `apps/web/` |

The KMP core owns domain logic. Platform adapters own navigation, maps,
permissions, file handling, secure storage, lifecycle, accessibility and
notifications. The server stays authoritative for routes, calendars, lineage,
rights and release identity.

---

## AD-006, 30 September 2026: the release contract replaces the untransplanted Syrmos snapshot generator

**Context.** `server/ktel-staging/tests/test_ktel.py` imported a `generator`
module that was never copied from the Syrmos rail tree, so the suite failed at
import and nothing in Phase 0 could be verified.

**Decision.** The module's rail payloads are obsolete for a coach product, but
its release contract is not, so the contract was restated rather than deleted.
`ktel_release.py` builds content-addressed packs, verifies them, writes the
manifest last, keeps exactly one previous manifest for rollback, and refuses a
release whose compiled identity disagrees with the public database.

**Consequence.** The pre-existing manifest assertions are kept intact. Fifteen
further tests were added covering times past 24:00, both 2026 daylight-saving
transitions, pack corruption and deletion, rollback, and proof that a
`permission_pending` source stays out of a public release even after a reviewer
approves the row. No assertion was removed to obtain a green suite.

---

## AD-007, 30 September 2026: the GitHub repository is renamed, the local checkout is not

**Decision.** The remote repository is renamed to `peterdsp/Poravia` because a
public repository URL is user-facing branding. The local checkout stays at
`/Users/peterdsp/git/HodoMap` because moving it would break existing tooling
for no product benefit, and the delivery instruction explicitly warns against
treating the checkout path as the product identity.

**Integration inventory before the rename.** GitHub serves permanent redirects
for a renamed repository, so existing clones, `git remote` entries and the
fifteen issue links cross-referenced from `docs/pilot/` and `docs/phase0/`
continue to resolve. `brand.json.repository` records the current URL.

---

## AD-008, 30 September 2026: no live tracking, no predictions, in this release

**Context.** `docs/LIVE_COACH_MAP_AND_ETA.md` describes a live map and ETA
product. The delivery instruction permits predictions only with defensible
source inputs, uncertainty and tests, and live tracking only for an authorised
working feed.

**Decision.** Neither is enabled. There is no authorised live feed and no
defensible prediction input. Every client keeps the `positionQuality` field
visible and reports `scheduled` only. Where a live view would go, the clients
show schedule and stop information and state plainly that live tracking is not
available.

**Explicitly rejected.** Animating a fabricated coach to make the product look
complete.

---

## AD-009, 30 September 2026: one release generator, and packs carry the contract shape

**Context.** During parallel client development, three independent offline-pack
generators appeared: the staging release generator emitting the internal
staging payloads (`registry`, `routes`, `trips-<date>`), a JavaScript generator
under `apps/web/scripts/` emitting a near-copy with an undated singular `trips`
pack, and a shell generator under `scripts/` emitting a fourth, unrelated set
(`meta`, `places`, `operators`, `journeys`, `patterns`) into the Android
assets.

This is the exact failure the delivery instruction names: each client
inventing its own contract. Worse than duplication, it means an **offline
client sees payloads the API would never return**, so offline and online
disagree by construction and the disagreement is invisible until a traveller
hits it.

**Decision.**

1. There is **one** generator. `server/api/publicapi/packs.py` builds every
   pack using the same shaping functions in `publicapi/repository.py` that
   serve the HTTP endpoints. The other two generators are deleted.
2. Packs carry the **contract** shape, not the staging shape. A pack contains
   exactly what the matching `/v1/...` endpoint would have returned for the
   same input. An offline read and an online read are the same bytes.
3. The low-level release mechanism stays in
   `hodomap_ktel/ktel_release.py` — content addressing, digest and size in the
   manifest, manifest written last, one previous manifest retained for
   rollback, and `verify_release`. Only the payload shaping moved. The
   mechanism is not forked.
4. Canonical logical pack names, and nothing else: `meta`, `coverage`,
   `sources`, `places`, `operators`, `stops`, `journeys-<serviceDate>`, `gtfs`.
   `data/schemas/CONTRACT-v1.md` lists them, so the contract and the generator
   cannot drift again.
5. Clients consume the generated release rather than producing one.
   `scripts/web-sync-packs.sh` copies it into `apps/web/public/data/` and
   `scripts/android-sync-packs.sh` into the Android assets. Shared Kotlin test
   fixtures come from the same directory.

**Enforcement, so this cannot regress silently.** Each client carries a test
that runs the same queries through its pack-backed source and its API-backed
source against the same release and asserts the results are equal. A manifest
test asserts the pack set is exactly the canonical list with no extras. Every
pack is validated against its JSON Schema in `data/schemas/`.

**Cost.** Regenerating fixtures and reworking the web and Android data layers
mid-build. Taken deliberately: a duplicated generator is cheap to leave and
expensive to discover, and the whole product claim is that the answer you get
offline is the answer the source supports.
