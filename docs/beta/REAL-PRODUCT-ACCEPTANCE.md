# Real Product Acceptance

Prepared 3 October 2026. Records what was delivered and verified in this working
session, what remains, and answers the delivery prompt's final questions with
honest evidence. This is a session acceptance record, not a claim of program
completion.

## Answers to the required questions (updated 3 October 2026)

- Did the Pi contain individual KTEL rows or only aggregates? Neither: the Pi
  contains no KTEL data at all. Access was restored and it was inspected
  read-only. It runs the separate Syrmos rail product (`syrmos.db`: 28 rail
  tables, zero `ktel_*`). No `ktel.db`/`ktel-public.db`/`ingest.db` exists
  anywhere; no log fetched ktelbus/ticketweb/nap; the legacy `hodomap` checkout
  (Odivrelo's former name) holds only `registry.json`. The 19,872 was an
  aggregate (sum of the `stops` field
  across 26 TicketWeb tenants in `operators.json`), never retained rows. Question
  permanently closed. See `DATA-RECOVERY-INVENTORY.md`.
- How many underlying rows were recovered? Zero from the Pi (none exist). The
  only real source is the NAP 2020 workbook, now parsed into candidates: 1,340
  trips, 263 city stops, 260 lines, 228 calendars across 42 of 62 operators.
- What exact data is public, and under what release id? Only the labelled Aloria
  demonstration release (dataMode demo). No real KTEL service is public. The NAP
  candidates are held for review and are never shipped.
- How many usable operators, stops, routes, trips, current service dates? Public
  real: zero. Candidate real (NAP 2020, not current): 42 operators, 54 cities,
  258 city pairs, 1,340 trips, 0 current service dates (2020 vintage).
- Which data appears in each app? All three clients ship the labelled Aloria demo.
- Which map capabilities run on each platform? See `LIVE-MAP-PARITY.md`. Web:
  tileless route with now-correct stop markers; iOS: MapKit route with named
  stops resolved by id (new); Android: no embedded map.
- Is any KTEL live GPS feed connected and fresh? No. All schedule-only; no live
  feed exists or was found on the Pi or in permitted sources.
- Can a traveller complete a real journey end to end today? No. The flow works
  against the demonstration dataset, but no real KTEL journey is published. The
  blocker is current, boarding-level real data: NAP 2020 is historical and
  city-level, and operator reuse rights remain unknown.

## Delivered and verified this session

1. Data recovery inventory, with machine-readable companion.
   `DATA-RECOVERY-INVENTORY.md`, `data-recovery-inventory.json`. Evidence based,
   answers the 19,872 question, names the exact missing Pi location.

2. Production release hardening so real data cannot silently revert to demo.
   - `server/api/publicapi/release_guard.py`: fail-closed channel guard.
   - `server/api/publicapi/release_production.py`: production path that consumes a
     reviewed real dataset and refuses missing, empty or demo-tainted data.
   - `scripts/release-production.sh`, `scripts/build-release.sh`: channel aware
     entry points. Default stays demo so the current labelled beta is unchanged.
   - Workflows `deploy-web.yml`, `release-ios.yml`, `release-android.yml` call the
     channel-aware build and add a `release_channel` input.
   - Tests: `server/api/tests/test_release_guard.py` (8 tests) prove production
     fails on a missing dataset, an empty import, and a demo-operator dataset,
     and that a real release passes. Fixed a broken call in the staged
     `test_calendar_import.py`.
   Verified: 286 api + 27 ktel tests pass; both channels exercised locally
   (demo builds and passes its guard; production fails closed without a dataset).

3. Stop coordinates resolved by id across the contract and clients.
   Fixed a real bug where journey stops carried no coordinate, so the web map
   placed markers at `undefined` and offline `mapData` was always false. Changed
   the OpenAPI schema, the server emitter, the web and shared models, and the iOS
   map (named stop annotations by id with segment emphasis). Android receives the
   data but has no embedded map yet.
   Verified: contracts regenerate in sync; web typecheck and 178 web tests pass;
   new server regression test passes. iOS and Android code complete, mobile build
   and runtime verification pending (run the release CI or a local build).

## Source to release reconciliation (3 October 2026)

| Stage | Count | Note |
| --- | --- | --- |
| Recovered from Pi | 0 | Pi inspected read-only; no KTEL data exists there |
| Discovered (NAP sheets) | 42 prefectures | plus a contact sheet |
| Parsed (NAP 2020) | 1,048 route rows -> 1,340 trips | both layouts |
| Candidate (imported) | 1,340 trips, 263 stops, 42 operators | all `candidate` |
| Reviewed / approved | 0 | awaiting current-source verification |
| Published real | 0 | fails freshness + boarding gates |
| In public release | Aloria demo only | dataMode demo |

Reason for every excluded group: the Pi held no KTEL rows; NAP 2020 is permitted
(ODbL) but 2020 and city-level, so it stays candidate; operator-site scraping is
not done because rights remain `unknown`. Full per-operator funnel in
`docs/beta/nap-reconciliation.json` and `OPERATOR-COVERAGE-LEDGER.md`.

The production release path was verified to refuse this candidate dataset:
`release_production` against the NAP ingest db fails closed with "no published
trips", so candidate real data cannot accidentally ship.

## Status by area (updated 4 October 2026)

Done and verified since the first session:

- Pi recovery closed: inspected read-only, no KTEL data (runs Syrmos rail).
- NAP 2020 parser built and imported: 42 operators, 1,340 candidate trips.
- Rename gate repaired for the historical evidence references.
- Web OpenFreeMap basemap with visible attribution, verified in a preview build
  (style, tiles and attribution load; route and stop overlays draw on top).

Not done yet (honest gaps):

- Embedded Android map (MapLibre Native). The shared model carries the stop
  coordinates; the map itself is not implemented.
- Main Map destination (as opposed to the journey-detail map) on any platform;
  stop/operator/route filters, clustering, locate-me.
- Current, publishable real data: NAP 2020 is historical and city-level, operator
  reuse rights remain `unknown`, and no live KTEL GPS feed exists. Current-source
  acquisition is tracked in the operator source ledger.
- Live feed adapter, normalized live contract, live/estimated/stale states.
- iOS and Android map changes are not yet built or run on a device here.
- Deployment and beta uploads were not performed.

Android instrumentation note: the suite is non-blocking in CI. On 4 October it
had 11 failures that reproduce on a local host-GPU emulator (so not a CI GPU
issue). Nine are pre-existing (deep-link teardown and activity-recreation cases
the suite comment already flags as being stabilised). Two, `SearchFlowTest.c/d`,
fail because the demonstration dataset's latest packed date is 2026-10-02 and the
test searches "today" (now past it); this is a demo date time-bomb that hits any
run after 2026-10-02 on any branch, not a code regression. The proper fix is to
re-anchor the demo dataset to the current date, which is deferred because 23
test sites hard-code the fixed demo dates.

## Smallest owner actions to unblock

1. A path to current, boarding-level real data: operator reuse permission, or a
   current NAP resource. Without it the apps stay schedule-demo over a reviewable
   candidate dataset. See the operator source ledger.
2. Authorisation to send the prepared operator permission requests, if that route
   is chosen.
