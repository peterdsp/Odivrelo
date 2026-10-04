# Finish Odivrelo after PR #21

Continue implementing the original Odivrelo product requirements in
`/Users/peterdsp/git/Odivrelo`. PR #21 is merged at
`f8f5e71152a3b7e444a4eb01a0e1ddaf744278c6` (verified 4 October 2026).
Refresh remote state and start a new `codex/` continuation branch from current
main, preserving any local edits. Do not repeat completed recovery work.

The main Map destination, filters, clustering, locate-me, live-feed
infrastructure, interactive iOS verification, current-data acquisition and
delivery are already part of the original assignment. They remain required.
Do not recategorize them as additional scope or stop to ask which required task
to do. Maintain the execution checklist, implement, verify and deliver.

## Preserve what landed; correct the baseline

Keep the NAP parser/candidate dataset, source provenance, production channel
guards, stop-coordinate contract fixes and working journey maps. OpenFreeMap
with MapLibre on Web/Android and MapKit on iOS is the settled provider choice.
Pi recovery found no underlying KTEL rows in the inspected locations; do not
reopen that search without new evidence. The historical aggregate stop counts
are not a dataset to import.

Read the original real-product prompt, `LIVE-MAP-PARITY.md`,
`REAL-PRODUCT-ACCEPTANCE.md`, `CURRENT-SOURCE-LEDGER.md` and
`REAL-PRODUCT-EXECUTION-CHECKLIST.md`. Reconcile their contradictory status
entries against current code and evidence. Mark completed work accurately;
an appended update must not leave earlier current-status sections contradicting
it. Use “no permitted live feed identified in the investigated sources”, not
the unsupported global claim “no KTEL GPS feed exists”.

## 1. Repair Android tests and the expiring demo

The merged CI configuration has `continue-on-error: true` on Android
instrumentation. The reported run had 11 failing tests: nine described as
pre-existing and two searching today after the last packed demo service date,
2 October 2026. Verify that classification from logs and reproduction.
Pre-existing failures still need fixing under this completion task.

Introduce a shared, injectable clock/service-date boundary and deterministic
fixture dates for automated tests. Tests must not change their outcome as the
calendar advances. Replace coupled hard-coded dates systematically while
preserving dedicated fixed cases for overnight service, exceptions and both
Athens daylight-saving transitions. Compare dates in `Europe/Athens` explicitly.
Do not fix this by advancing the same hard-coded expiry to another future date.

Treat the installable demonstration separately: generate a documented rolling
demo horizon from an explicit build anchor, or offer a clearly labelled demo
date selector with an expired-pack state. Preserve fixture identity and demo
labels. Prove behavior after the pack horizon expires as well as on build day.
Never advance dates or freshness on real NAP/operator records to make them pass.

Fix the actual deep-link, lifecycle, teardown and restoration failures. Use
test reports, logs and screenshots; do not replace assertions with sleeps,
blindly inflate timeouts or remove failing cases. Run the repaired suite locally
and in CI. Once reliable, remove `continue-on-error` so the workflow fails when
instrumentation fails. If server-side required-check settings need separate
permissions, report that separately; it does not prevent fixing the tests.

## 2. Complete the passenger map experience

Implement the main Map destination on Web, iOS and Android, preserving their
existing journey-detail maps. Include operator/route/stop filters, clustering
and decluttering, explicit locate-me with permission-denial handling, fit route,
pan/zoom, attribution, accessible controls and a useful list equivalent.

Allow located stops with no geometry. Tapping a stop reveals its identity and
supported departures; choosing a journey preserves the user's origin/destination
segment, board/alight emphasis and the full intermediate-stop sequence.
Preserve selection and viewport sensibly through list/map changes, navigation,
rotation, backgrounding and process restoration. Keep provider failure, offline
data and no coverage usable. Do not imply downloadable offline basemaps unless
implemented and verified under the provider's terms.

Implement normalized live-provider interfaces, backend/client data flow,
observation timestamps, trip matching, deduplication, bounded refresh/backoff,
lifecycle behavior and live/stale/expired state transitions. Age observations
even without new network events. Test using clearly isolated synthetic feeds;
synthetic success does not establish live KTEL coverage.

Keep public observed-live layers disabled until an actual permitted feed is
connected. Any timetable estimate needs sufficient route/time inputs and must
be explicitly labelled approximate. Never animate city-level NAP candidates as
real buses or use Syrmos rail/OASA vehicles as KTEL substitutes.

## 3. Finish individual current-source assessments

The current source ledger has details for two pilot corridors and a blanket
unknown status for the other operators. That is an unfinished national source
investigation, not evidence that all operators require the same action.

Create one assessed row per federation operator and the two additional known
tenants. Track exact official sources, inspection timestamps, effective periods,
physical boarding evidence, reuse/retention evidence, adapters and unresolved
questions. Distinguish uninspected, investigated/no source found, source found,
permitted, restricted, candidate, reviewed and published.

Inspect official timetable pages, PDFs, feeds, station information and booking
handoffs through bounded, permitted access. A stored `unknown` flag needs
assessment; it is not itself a prohibition or a reuse grant. Evaluate actual
source terms and existing licenses without making unsupported legal conclusions.
Build adapters and ingest where supported; continue independent operators when
one source is blocked. Assess live sources separately from timetables.

For Athens–Delphi, close the recorded effective-date and physical-boarding gaps
as well as rights. A page labelled winter schedule with no date range is not
proof of applicability to today's journey. For Athens–Nafplio, inspect the
official schedule rather than relying on third-party frequency estimates.
Resolve operator IDs consistently with the existing registries.

Use the historical NAP candidates for lineage and comparison, retaining their
age. Publish only current, supported records after the required review. Missing
optional fields should not discard otherwise usable services, but exact
boarding guidance must not come from city centroids or office addresses.

Where external permission or confirmation really is needed, prepare the full
request now: verified intended recipient, exact sources/data classes, refresh
and retention scope, proposed attribution and the missing boarding/date/feed
questions. Save drafts; do not contact operators without explicit authorization.
Ask for that concrete sending authorization only after preparing the requests,
and continue maps, tests and release preparation independently.

## 4. Verify all platforms and release inputs

Drive the Web app, iOS Simulator and Android emulator interactively. The iOS
build passing is not a captured interactive map check. Verify map/navigation,
search, journey detail, boarding/alighting, official booking handoff, saved
journeys, offline recovery, source freshness and release identity. Exercise
accessibility, large text, reduced motion, location denial and restoration.

Use labelled fixtures to verify engineering behavior where necessary. Keep that
evidence separate from a current real journey verified against published source
data. Capture nonblank screenshots and relevant logs with commit/build IDs.

Verify production workflows can actually obtain the explicit reviewed dataset
on their clean runners. A local dataset path alone cannot supply a hosted runner.
Wire authenticated artifact retrieval or approved storage, checksums and
release identity as required without committing databases or exposing secrets.
Fail on missing input, candidate-only or demo-tainted releases, invalid hashes,
and zero usable current services after publication filtering. Preserve rollback.
Add bounded refresh monitoring and source-level diagnostics already required by
the original prompt.

## 5. Review, integrate and distribute

Complete scoped commits and a follow-up PR; fix required checks, including the
repaired Android device suite, and satisfy review requirements before merging.
Continue the original authorized Web and beta delivery workflow. Select release
channels explicitly and preserve existing signing identities and app records.

For an engineering update with no publishable real data, use the clearly
labelled demo channel and report it as a demo update. It does not complete the
real coach product. Once a reviewed current dataset exists, use the production
channel and verify the actual served/bundled release identity on every platform.
Never let production silently fall back to demonstration data.

Verify the public Web URL after deployment, iOS upload/processing/TestFlight
tester access and Android upload/internal-track tester access separately. Do not
claim distribution from signed artifacts alone. Keep public App Store/Play launch,
new paid commitments, access expansion and operator messaging subject to their
actual applicable authority; ordinary development and verification should proceed.

## Exit criteria

Report the status of every original requirement with concrete evidence:
all Android test failures fixed; main Map functionality verified on all three
platforms; live infrastructure implemented with actual coverage stated;
individual operator-source assessments completed; current public data counts;
and Web/TestFlight/Play distribution outcomes.

One corridor is an integration milestone, not national completion. Real data,
live coverage or distribution may still have genuine external prerequisites;
name each exact missing item and finish all independent work. Do not call an
unimplemented feature an external blocker or silently narrow the deliverable.
The final answer must state whether a traveller can complete a real journey
today and, if not, precisely why.
