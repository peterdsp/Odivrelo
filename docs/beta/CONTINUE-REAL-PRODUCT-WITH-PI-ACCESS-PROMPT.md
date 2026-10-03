# Odivrelo continuation prompt: recover the Pi data and finish the real product

You are continuing autonomous implementation in:

`/Users/peterdsp/git/Odivrelo`

The owner has now restored access to the Raspberry Pi at `syrmos-pi` /
`petrospi` / `192.168.10.10`. Use that access now. Do not stop at another plan,
another status document or a demo-only build. Execute the remaining work through
recovery, integration, verification and release wherever the available evidence
and access support it.

The product is **Odivrelo**. Preserve its current identity, bundle/package ID
`dev.peterdsp.odivrelo`, and existing architecture. The separate Syrmos product
must remain untouched. Read all applicable repository instructions first.

## Starting state

The previous session completed and tested:

- `docs/beta/DATA-RECOVERY-INVENTORY.md` and
  `docs/beta/data-recovery-inventory.json`.
- Production release hardening in `release_production.py`, `release_guard.py`,
  `scripts/release-production.sh` and `scripts/build-release.sh`.
- Release workflow channel dispatch for Web, iOS and Android.
- Stop-coordinate contract and map fix across server, Web, shared Kotlin and iOS.
- `docs/beta/LIVE-MAP-PARITY.md`, `docs/beta/REAL-PRODUCT-ACCEPTANCE.md` and
  `docs/beta/REAL-PRODUCT-EXECUTION-CHECKLIST.md`.
- 313 server tests, Web typecheck and 178 Web tests green in the previous run.

The working tree is intentionally uncommitted and contains user work. Inspect
`git status` and preserve every unrelated change. Do not reset, stash, discard or
overwrite the tree. Create a fresh `codex/` continuation branch only after
capturing the current worktree state and only if the repository workflow needs
one. Keep work in small durable commits or a clearly documented patch series.

The verified local truth is:

- The historic `19,872` figure was an aggregate sum across 26 TicketWeb tenants
  in `server/ktel-staging/pkg/ktel/operators.json`, not 19,872 retained rows.
- TicketWeb was metadata-only with zero-day retention and permission pending.
- Local `artifacts/ingest.db` and `artifacts/public.db` contain only the labelled
  Aloria demonstration data.
- `artifacts/nap/nap-ktel-2020.xlsx` is real and ODbL-licensed, but old and
  incomplete for current boarding instructions.
- The only unresolved possibility for individual TicketWeb rows or a compiled
  KTEL database is the Pi.
- Current clients are schedule-only. Web has a tileless route map, iOS has a
  journey MapKit map, and Android still uses an external maps handoff.
- No live KTEL vehicle feed has yet been established.

## Phase 1: recover the Pi safely and prove what exists

First test access without changing the remote system:

```sh
ssh -o BatchMode=yes -o ConnectTimeout=10 syrmos-pi 'hostname; date; test -r ~/syrmos-api/db/ktel.db'
```

If the alias fails, resolve the configured alias and address from local SSH
configuration and retry only the known Pi target. Never scan the LAN. Never
change firewall, router, SSH, systemd or credentials to force access.

On the Pi, inspect only the known Odivrelo/Syrmos KTEL locations:

- `~/syrmos-api/db/ktel.db`
- `~/syrmos-api/db/ktel-public.db`
- `~/syrmos-api/out/ktel/`
- `~/syrmos-api/backups/ktel-YYYY-MM-DD.db`
- Any configured `SYRMOS_KTEL_*`, `HODOMAP_*`, `PORAVIA_*` or `ODIVRELO_*`
  paths in the service environment.

Use SQLite `.backup` to create consistent read-only snapshots before analysis.
Copy snapshots into a gitignored, explicitly named local recovery directory.
Do not overwrite either the Pi database or the local Odivrelo databases. Do not
copy passwords, private keys, passenger data or unrelated home files.

For every discovered database, manifest, pack, backup and raw source artifact,
record in `DATA-RECOVERY-INVENTORY.md` and its JSON companion:

- exact remote and local path;
- SHA-256, byte size and retrieval timestamp;
- schema version and table list;
- row counts for operators, sources, source records, stops, stop places, lines,
  patterns, trips, stop times, calendars, exceptions, fares and geometry;
- source ID, operator, external IDs, effective dates, freshness and review state;
- rights/retention state and whether the rows are raw, candidate, approved,
  published, demo or restricted;
- whether the rows reconcile to the historic 5,378 / 19,872 / 511 aggregates.

Explain with evidence whether the Pi contains the missing individual rows. If it
does not, update the inventory to close that question permanently. If it does,
preserve the source lineage and use the recovered database as input; never
silently treat a backup as a current public release.

## Phase 2: recover, normalize and publish usable real data

Run the recovered material through the existing Odivrelo import path in
`server/ktel-staging/odivrelo_ktel/`. Do not bypass rights or review gates.
Repair adapters or migrations when the old Syrmos package shape prevents a
clean import. Keep the source IDs and original external IDs. Make the import
idempotent, transactional and reversible.

Build the permitted NAP parser in parallel if it is not already complete:

- parse both workbook layouts and the contact sheet;
- normalize Greek weekday prose into calendars and exceptions;
- retain original source text beside normalized values;
- parse overnight and ambiguous times conservatively;
- attach operator/source lineage and ODbL attribution;
- quarantine missing arrivals, city-only stops, invalid prices and ambiguous
  terminal identities;
- mark the 2020 source stale and candidate until current service dates and exact
  boarding locations are verified from an authoritative current source.

The parser may produce a real, reviewable candidate dataset. It must not claim
that a 2020 schedule is current merely because it parsed successfully. Publish
only rows whose source rights, review state, freshness and boarding evidence
meet the existing governance rules. Directory-only operators may remain listed
with an official handoff and a clear lack-of-timetable state.

Create an operator/source reconciliation report covering all 62 federation
operators plus the two additional known TicketWeb tenants. It must distinguish:

`discovered → recovered → parsed → candidate → reviewed → approved → compiled →
released → visible in each client`.

For every excluded group, give an exact reason. Do not recreate rows from
aggregate counts and do not label an empty or demo dataset as real.

## Phase 3: make the release path use the real dataset

Use the production channel implemented in the previous session. Fix any wiring
that still calls `scripts/api-seed-demo.sh` for a real release. Demonstration
fixtures remain available only for the explicit demo channel and tests.

The production path must:

- require an explicit reviewed dataset/release directory;
- fail closed on missing, empty, demo-tainted, stale or inconsistent input;
- carry one release ID and verified hashes through database, API, Web, iOS,
  Android and offline packs;
- preserve the previous production release for rollback;
- never silently fall back to Aloria;
- expose data mode, source freshness and coverage honestly;
- support current service-date refreshes and calendar exceptions.

Run the guard tests plus the full server, contract, Web, shared Kotlin, iOS and
Android checks affected by the changes. Update acceptance documents with actual
counts and release IDs, not planned values.

## Phase 4: finish map parity and live-state truthfully

Implement the missing embedded map work described in
`docs/beta/LIVE-MAP-PARITY.md`:

- Web: a main Map destination, permitted attributed basemap, stop/operator/
  route filtering, fit and locate-me, clustering/decluttering, accessible list
  equivalent, and offline/cached state.
- iOS: main Map destination, located-stop fallback when geometry is absent,
  filters, locate-me, accessible named annotations and selected segment state.
- Android: choose and document the provider, add the embedded map dependency,
  render real stop coordinates and route geometry, implement filters, fit,
  locate-me, accessibility and emulator verification. An external maps intent
  is a handoff, not an embedded map.

Use Syrmos map code as a reference for state flow, marker aging and provider
integration, while preserving Odivrelo's coach domain. Do not copy OASA or rail
vehicles into Odivrelo.

Implement explicit `live`, `estimated`, `scheduled`, `stale` and `expired`
states only where evidence supports them. Search the recovered Pi data and
current permitted sources for a KTEL vehicle-position feed. A timetable does not
prove a vehicle is moving. Never animate beyond the last validated observation.
If no permitted live feed exists, deliver the schedule map and infrastructure,
keep live coverage disabled, and document the exact missing provider.

## Phase 5: runtime verification and release

Verify the same real journey on Web, iOS Simulator and Android emulator:

1. Search an origin, destination and valid date.
2. Select a real source-backed journey.
3. See the exact boarding location and operator.
4. Open the embedded map and stop list.
5. See the selected segment with “Board here” and “Get off here”.
6. Open the official booking handoff.
7. Save/download the journey and reopen it offline.
8. Verify source, freshness, release ID and coverage state.

Also verify empty coverage, stale data, offline recovery, location denial,
failed basemap loading, large text, VoiceOver/TalkBack, deep links, process
restoration and map/list continuity. Build and run iOS and Android; do not mark
mobile work verified from source inspection alone.

Then review the diff, run CI, merge through the normal PR workflow, deploy the
Web release and upload beta builds using the already configured Apple and
Google records. Verify processing and tester availability separately. Do not
publish a public store release or send external operator messages without the
required owner authority.

## Required handoff

Update these files as evidence changes:

- `docs/beta/DATA-RECOVERY-INVENTORY.md`
- `docs/beta/data-recovery-inventory.json`
- `docs/beta/LIVE-MAP-PARITY.md`
- `docs/beta/REAL-PRODUCT-ACCEPTANCE.md`
- `docs/beta/REAL-PRODUCT-EXECUTION-CHECKLIST.md`
- `docs/beta/OPERATOR-COVERAGE-LEDGER.md`

The final report must answer, with counts and paths:

- Did the Pi contain individual KTEL rows or only aggregates?
- What exact data is now public, and what release ID contains it?
- How many operators, stops, routes, trips and current service dates are usable?
- Which data is visible in Web, iOS and Android?
- Which map capabilities run on each platform?
- Is any KTEL live GPS feed actually connected and fresh?
- Can a traveller complete a real journey end to end today?

Do not call the product ready because tests pass, a map renders, credentials
exist, or a parser produces rows. Completion requires source-to-release-to-app
evidence. If a real source or live feed remains blocked, state that precisely and
finish every independent part of the product around it.
