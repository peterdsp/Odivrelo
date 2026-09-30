# Execution status

Living document. Last updated: 30 September 2026.

Branch: `codex/beta-1.0.0`. Repository: `peterdsp/Poravia`, renamed from
`peterdsp/HodoMap` on 30 September 2026; GitHub serves permanent redirects, so
the fourteen issue links cross-referenced from `docs/pilot/` and
`docs/phase0/` still resolve.

## Platform states

These words are used precisely and separately. A platform can be
runtime-verified and still distribution-blocked.

`implemented` · `built` · `runtime-verified` · `packaged` · `signed` ·
`uploaded` · `processed` · `available-to-testers` · `blocked`

See `TEST-MATRIX.md` for the evidence behind each and `RELEASE-READINESS.md`
for the gates.

## Milestones

| # | Milestone | State |
|---|---|---|
| 1 | Inventory the tree, preserve in-flight work, record the scope change | done |
| 2 | Research and select an entirely new brand, then migrate every surface | done |
| 3 | Repair Phase 0 and prove the fixture ingest → review → compile → query path | done |
| 4 | Establish versioned data contracts and the public API | in progress |
| 5 | One complete Web journey flow, and continued lawful corridor verification | in progress |
| 6 | Shared mobile core and native iOS and Android flows, adaptive from the start | in progress |
| 7 | Offline packs, saved trips, wallet, reminders, translations, accessibility, failure states | in progress |
| 8 | Runtime verification in real browsers, simulators and emulators | in progress |
| 9 | Release artifacts, operational checks, store materials, authorised uploads | partly blocked |
| 10 | Deploy and verify Web `1.0.0` at `https://poravia.peterdsp.dev` | **blocked**, see EB-01 |
| 11 | Reconcile code, evidence, artifacts, documentation and distribution status | in progress |

## Completed work

### Inventory and preservation

The working tree already held an unfinished `syrmos_admin` → `poravia_ktel`
rename, new Phase 0 documents and pilot edits. None of it was reset, stashed or
overwritten. It is preserved verbatim in commit `aeba939` on the task branch,
described as pre-existing, before any of this delivery's own changes.

The reported defect was reproduced exactly before being touched:
`tests/test_ktel.py` failed at import with
`ImportError: cannot import name 'generator' from 'poravia_ktel'`, so the whole
suite refused to load.

### Brand

**Poravia**, slug `poravia`, hostname `poravia.peterdsp.dev`, bundle and
application id `dev.peterdsp.poravia`. Chosen after screening thirteen
candidates; five were rejected on real collisions. Identity is centralised in
`brand.json` so a future rename touches one file. Full record, including the
trademark searches that could **not** be performed, is in `BRAND-DECISION.md`.

Migrated: README, active architecture, governance and design documents, design
tokens (`--hm-` → `--pv-`), the brand board and marks, systemd units, the
acquisition pipeline package, the TicketWeb user agent and cache path, the
manual-review source URL, and the GitHub repository name. Dated decisions keep
the former name and are marked historical. `scripts/check-brand.sh` enforces
the boundary in CI over both source and the built web artifact.

Compatibility preserved rather than assumed: the former `HODOMAP_` and
`SYRMOS_` environment prefixes are still read, the acquisition data root falls
back to an existing legacy directory rather than silently starting empty, and
the TicketWeb terms gate honours all three prefixes so a rename can neither
enable nor disable it. Three tests cover this.

### Phase 0

All four tickets are closed with evidence.

- **PHASE0-01** was already done; its work is preserved unchanged.
- **PHASE0-02** retrieved the National Access Point long-distance bus dataset,
  recorded its licence and digest, inventoried its structure and established
  corridor coverage. Outcome **no-go**, on the data rather than the rights.
  See `docs/phase0/NAP-DATASET-EVIDENCE.md`.
- **PHASE0-03** repaired the missing `generator` import by restating its release
  contract instead of deleting the test, and added the GTFS export the ticket
  anticipated. The suite went from failing at import to **22 passing tests**,
  with no assertion removed and fifteen added.
- **PHASE0-04** audited the tree: no tracked runtime database, no secret, no
  rights-restricted body, TicketWeb gate off everywhere. See
  `docs/phase0/HYGIENE-AUDIT.md`.

A one-page runbook records the exact ingest-to-release commands:
`docs/phase0/RUNBOOK.md`.

### Data and service foundation

- Content-addressed release packs with the manifest written **last**, one
  previous manifest retained for rollback, digest and size verification, and
  refusal of a release whose compiled identity disagrees with the public
  database.
- A byte-reproducible GTFS export from the read-only public database.
- `Europe/Athens` service-date semantics resolved **once**, as an offset from
  noon minus twelve hours, so past-midnight departures come out as `25:10:00`
  and both 2026 daylight-saving transitions are correct. Tested.
- The rights and review gates proved by test: a `permission_pending` source
  stays out of a public release **even after a reviewer approves the row**, and
  an unreviewed candidate never appears.
- The authoritative public contract, `data/schemas/CONTRACT-v1.md`, which every
  client implements.

### Delivery infrastructure

- CI with a data-pipeline job, an API job, a contract-drift gate, a rename
  gate, web, shared-core-and-Android, and iOS, with least-privilege tokens and
  bounded artifact retention.
- A deployment workflow that runs **only** on a release tag or manual dispatch,
  never on a pull request, so untrusted code cannot reach deployment
  credentials. It self-enables the Pages site on first run.
- `scripts/verify-deployment.sh`, which checks DNS, HTTPS, the HTTP redirect,
  the homepage, the release manifest, deep-link loads and the PWA files, and
  fails loudly rather than reporting a green deployment that is not live.

## Work partition

Delivery is parallelised across bounded workstreams with non-overlapping paths,
integrated here.

| Workstream | Paths | State |
|---|---|---|
| Data, compiler, GTFS, release packs | `server/ktel-staging/` | complete |
| Public API, contracts, fixtures, ops scripts | `server/api/`, `data/schemas/`, `data/fixtures/`, `scripts/api-*` | complete |
| Shared Kotlin Multiplatform core | `shared/` and the root Gradle graph | in progress |
| Android application | `apps/android/` | in progress |
| Web application | `apps/web/` | in progress |
| iOS and iPadOS application | `apps/ios/` | in progress |
| Brand, docs, CI, release materials, integration | everything else | in progress |

The Android application was re-partitioned away from the shared-core
workstream on 30 September 2026. Carrying both meant neither was finishing, and
`apps/android/src` still had no Kotlin source while the shared core was
complete. Splitting them at the `apps/android/build.gradle.kts` seam lets both
finish properly.

## Data mode

**Demonstration only.** The beta ships an invented region, Aloria, with
coordinates deliberately placed in open ocean so no row can be mistaken for a
real terminal. Every client shows a persistent, non-dismissible notice in all
three languages, and the web release is `noindex`.

This is evidence-driven, not a shortcut. All 62 federation operator directory
sources are `rightsStatus: unknown`, and the one rights-cleared national source
is five years and ten months stale, holds no boarding points or coordinates,
and does not contain the pilot corridor at all.

Flipping `dataMode` to `real` is a rights-and-review event. No client code
changes.

## Explicitly not claimed

- **Gate D1**, the corridor data check, **fails** on evidence.
- **Gate D2**, the five-traveller validation, has not been run. Engineering
  scope expanding does not pass it.
- No national coverage, no human data review of real timetables, no source
  permission from any operator.
- No live tracking and no predictions. There is no authorised feed and no
  defensible prediction input, so neither is enabled and neither is faked.
- No native desktop binary, watch, TV, automotive or spatial app. Desktop
  coverage is Web and PWA only.
- No trademark clearance. No register could be searched; `BRAND-DECISION.md`
  lists every check that failed and why.

## Remaining work and blockers

Independent work still in progress is listed in the milestone table.
Externally blocked work, with the exact unblocking action for each, is in
`EXTERNAL-BLOCKERS.md`:

| ID | Blocked | Needs |
|---|---|---|
| EB-01 | the public Web `1.0.0` hostname | one Cloudflare CNAME record |
| EB-02 | an installable iPhone beta | Apple signing identity and App Store Connect access |
| EB-03 | Play internal-track distribution | Android release keystore and Play Console access |
| EB-04 | a real-data beta | operator permission plus fresh, boarding-point-level data |
| EB-05 | the product validation claim | five recruited travellers, after EB-04 |
| EB-06 | name confidence beyond screening | a professional trademark search and native-speaker review |
| EB-07 | the production acquisition deployment | access to the Raspberry Pi |
