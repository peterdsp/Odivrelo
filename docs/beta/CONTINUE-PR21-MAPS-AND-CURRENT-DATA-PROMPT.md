# Continue PR #21: repair CI, finish maps, acquire current coach data

Continue implementation in `/Users/peterdsp/git/Odivrelo`, starting from
PR #21 and branch `codex/real-data-recovery-and-map` after verifying their current
state. Execute this prompt together with the original real-product delivery
requirements. Preserve all completed work and unrelated edits. Do not restart
the investigation or ask whether to run the browser or build the Android map:
both are required. Carry routine work through review, passing CI, integration,
deployment and beta verification under the existing delivery authorization.

## Decisions for this continuation

Use the existing MapLibre GL JS integration on Web and MapLibre Native on
Android with OpenFreeMap as the online basemap provider. Keep MapKit on iOS.
Use a compatible stable Android SDK version supported by this project's Gradle,
Kotlin and minimum Android versions; verify its official documentation before
adding it. Implement the embedded Android map now.

Start with OpenFreeMap's documented style:
`https://tiles.openfreemap.org/styles/liberty`.
Keep the style/provider URL configurable. Retain required attribution visibly;
the existing Web code disabled attribution for its tileless diagram and must
be updated. Choose legible Odivrelo route and stop overlays. Verify that the
style, sprites, glyphs and tiles actually load on both platforms.

Provider evidence checked 4 October 2026:

- https://openfreemap.org/ — public instance is free, requires no account or
  API key, permits commercial usage, requires attribution and offers no SLA.
- https://openfreemap.org/quick_start/ — documents the style and its use with
  MapLibre on Web and MapLibre Native on mobile.

Recheck the provider's applicable usage/caching terms during implementation.
Do not make paid commitments or download a full planet to the Pi. Retain
functional route/stop-list fallbacks during provider outages. Offline timetable
and saved-route support must work; do not claim downloadable offline basemaps
unless that capability is implemented, permitted and verified.

## 1. Fix the actual PR failures first

Refresh PR #21 checks and inspect logs for its current commit. As checked on
4 October, run `37087218220` showed:

- Data pipeline, public API, contract checks, Web, shared core/Android build,
  and iOS build/tests passed.
- Rename gate failed on historical identity references in the recovery docs
  and the original implementation prompt.
- Android instrumentation failed: 11 failures among 18 tests. Logs include
  Compose timeouts in search and restoration tests. Their root cause is not yet
  established; emulator rendering errors also appear and are not proof of cause.

Repair the rename check failure with explicit historical context or narrowly
documented exceptions in the existing mechanism. Keep real historical paths
accurate and maintain detection of unintended old branding in source and built
apps. Do not suppress all documentation or JSON checks to obtain green CI.

Investigate Android failures using the test report, app logs, emulator state
and screenshots. Check whether the new nullable contract fields, release packs,
fixed demo service dates, startup, selectors or restoration are implicated.
Reproduce, fix the cause and rerun the affected device suite. Do not remove
assertions, skip failing tests, enlarge timeouts blindly or assume the runner is
at fault. Verify the result against the current PR commit.

Record the successful iOS CI evidence accurately. CI build/tests do not prove
that a person can use the new map; the required interactive runtime pass remains.

## 2. Close stale documentation without repeating completed work

The Pi recovery investigation found no individual KTEL rows in the inspected
locations. Treat that recovery route as exhausted unless new evidence appears.
The 19,872 number is recorded aggregate metadata, not a recoverable local dataset.
Avoid claims about every possible historical location beyond the inspection's
actual scope.

The NAP parser already imported 1,340 candidate trips, 263 city stops, 260 lines
and 228 calendars for 42 operators. Verify these counts from current artifacts
and preserve the parser. Do not build it again. Historical 2020 data remains
candidate and must not become a current timetable by changing its dates.

Correct the contradictory lower sections of `REAL-PRODUCT-ACCEPTANCE.md` that
still say Pi access is missing and the NAP parser is not built. Update
`LIVE-MAP-PARITY.md` to reflect the provider decision, actual Android status and
CI evidence. A shared model change is not a completed Android map.

## 3. Implement the map on all three platforms

Deliver an accessible main Map destination as well as journey-detail maps.
Provide the basemap, real coordinate validation, operator/route/stop filtering,
appropriate clustering, pan/zoom, fit route, explicit locate-me and list fallback.
Render located stops even when no route shape exists. Handle null coordinates
and empty geometry without crashes or invented locations.

Tapping a stop opens its identity, provenance and supported departures. Journey
selection retains the searched segment: boarding/alighting points are prominent,
intermediate stops stay visible and outside-segment stops are muted. Resolve
annotations by stop ID rather than line-vertex order. Preserve selection across
map/list changes, navigation, rotation and process restoration.

Use Syrmos as an interaction/reference implementation, not as a source of KTEL
vehicles. Keep its code and deployment untouched. Test native map lifecycle and
gesture handling inside Android's existing Compose layout, memory cleanup and
background/foreground behavior. Provide readable text and accessible controls
on all platforms; support reduced motion and phone/tablet layouts.

Implement observed live, estimated, scheduled, stale and expired states with
source timestamps and honest semantics. Only activate states justified by the
data. Age observations even when no updates arrive. Timetable estimates must
remain visibly approximate and must not imply GPS, confirmed departure or a
measured delay. Do not create precise moving markers from city-level NAP data.

## 4. Start current-source acquisition instead of waiting on the Pi

The next data work is source discovery, verification and adapter implementation.
It does not depend on the basemap choice or further Pi recovery. A stored
`unknown` rights flag means an assessment is unfinished; investigate its evidence.
It is neither a reuse grant nor proof that every form of access is forbidden.

Create a dated operator-by-operator source ledger for all 62 federation operators
and the two additional known tenants. Investigate official timetable pages,
PDFs, published feeds, current NAP resources, official station information and
booking handoffs. Start with Athens–Delphi and Athens–Nafplio as tractable
integration targets, then continue through the whole inventory.

An official discovery starting point verified on 4 October is
https://www.ktel-fokidas.gr/dromologia/ with its Athens–Delphi link. This confirms
that an official timetable page exists; it does not establish present service
validity, exact boarding coordinates or redistribution rights. Verify each of
those separately. Search-result crawl dates are not timetable effective dates.

For each source record official URL, operator, retrieval time, publication and
effective dates, allowed data classes, applicable reuse/retention evidence,
physical stop evidence, parser status and the exact remaining gap. Investigate
newer NAP resources rather than assuming the retained 2020 file is the latest.
Discover permitted live feeds separately from timetable and booking endpoints.

Use bounded fetching and parsing where allowed, with provenance and review.
Implement adapters for every usable source encountered. Keep restrictions
source-specific. If permission is actually required, prepare the exact evidence
and a ready-to-send request; do not send operator messages without authorization.
Continue other sources and engineering work while that request is pending.

Do not manufacture boarding locations from offices or city centroids, infer
whole timetables from ticket availability, or relabel imported content as
`manual-review` to erase lineage. Preserve candidate and publication gates.
Track each source through acquisition, parsing, review, publication and client
visibility. A real first corridor is a milestone; national investigation remains
in scope. If no source becomes publishable, say exactly which evidence remains
missing and keep current-data delivery marked incomplete.

## 5. Verify runtime and delivery

Run Web in a browser, iOS in Simulator and Android in an emulator. Exercise the
actual map, coordinate fix, selection, search, detail, booking handoff, saved
journey and offline recovery. Verify attribution, tile requests, provider
failure, location denial, accessibility, large text and process restoration.
Capture useful screenshots and logs tied to commit and release IDs.

Use explicitly labelled fixtures where real data is unavailable for engineering
verification. Such checks establish feature behavior only. Separately verify
at least one current, published real journey end to end before claiming a usable
real-data coach product. Reconcile all published records to source evidence.

Keep production releases bound to an explicit reviewed dataset. Missing inputs
must fail clearly; no fallback to Aloria. The demo remains a separate explicit
channel. Recheck publication guards and new dependencies/contracts as required.

Review, commit and push scoped changes. Fix all required CI failures, then merge
through the existing workflow once checks and review requirements are satisfied.
Follow the existing authorization for Web deployment and beta uploads. A demo
update may ship only as clearly identified demo engineering progress; it does
not fulfill the real-product release goal. Verify TestFlight and Play processing
and availability, not just upload commands. Store records already exist.

Finish with the verified PR/CI state, runtime evidence per platform, current
source coverage, actual live-feed coverage and distribution links/status. Keep
completed features, published current data and tester availability distinct.
Do not stop for another provider decision or a choice between required tasks.
