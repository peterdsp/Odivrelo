# Real Product Execution Checklist

Durable checklist for the "recover KTEL data, deliver the map, finish the
product" program. Started 3 October 2026. Update the status column as work
lands so another session can continue without re-deriving state.

Owner decisions on record (3 Oct 2026):
- Data source: owner will provide Raspberry Pi access so the real raw rows are
  recovered first, before building ingestion on top of them.
- Session focus: all four areas (real dataset pipeline, cross-platform map,
  release hardening, build/verify and deploy).

## Status legend

- DONE: landed and verified in this checkout.
- WIP: in progress.
- BLOCKED: waiting on an external dependency, with the blocker named.
- TODO: not started.

## 1. Recover and reconcile prior data

| Item | Status | Notes |
| --- | --- | --- |
| Inventory every local artifact with evidence | DONE | `docs/beta/DATA-RECOVERY-INVENTORY.md` + `data-recovery-inventory.json` |
| Answer: where were the 19,872 records | DONE | aggregate `stops` sum across 26 ktel- TicketWeb tenants in operators.json; never stored as rows |
| Verify local databases content | DONE | ingest.db and public.db hold only the Aloria demo |
| Recover Raspberry Pi ktel.db / ktel-public.db | DONE | Pi inspected read-only 3 Oct; no KTEL data exists (runs Syrmos rail). Question closed. |
| Reconcile Pi rows against the aggregates | DONE | No Pi rows exist; 19,872 confirmed aggregate-only |

## 2. Populate the real national dataset

| Item | Status | Notes |
| --- | --- | --- |
| NAP 2020 workbook parser (ODbL, real) | DONE | `nap_parser.py`, both layouts, Greek day prose, tested (12 tests) |
| Feed NAP rows through ktel_ingest as candidates | DONE | `normalize-nap-xlsx`: 1,340 candidate trips, 42 operators, 0 published |
| Operator-by-operator reconciliation (62) | DONE | `nap-reconciliation.json`, ledger updated; 42 mapped |
| Publish real current data | BLOCKED | NAP is 2020 + city-level (fails freshness/boarding); operator rights unknown |

## 3. Deliver the map on Web, iOS, Android

| Item | Status | Notes |
| --- | --- | --- |
| Inspect current map/nav flows | DONE | `docs/beta/LIVE-MAP-PARITY.md`; Syrmos reference captured |
| Resolve stop coordinates by id (contract + clients) | DONE | schema + server + web + shared + iOS; fixed web marker/offline bug; iOS/android build pending |
| Accessible main Map destination per platform | TODO | not just a journey-detail button |
| Working basemap, attribution, pan/zoom, fit-route, locate-me, filters | TODO | provider decision needed (see parity doc) |
| Embedded Android map | TODO | no map library in Android build today |
| Explicit live/estimated/scheduled/stale states | TODO | Syrmos reference for state handling |
| Live coach feed adapter (if any permitted source) | BLOCKED | no permitted KTEL live feed identified |

## 4. Make real data survive build, deploy and updates

| Item | Status | Notes |
| --- | --- | --- |
| Production release path, no silent demo seeder | DONE | `release_production.py` + `release-production.sh` + `build-release.sh`; workflows wired with `release_channel` |
| Keep demo seeder for tests and explicit demo builds | DONE | demo channel default, guarded `--expect demo` |
| Tests: production cannot fall back to Aloria | DONE | `test_release_guard.py`: missing dataset, empty import, demo operator all fail closed |
| Dataset identity and hashes carried through packs | WIP | guard checks dataMode + demo operator; note: release id is not purely content-addressed (folds timestamp) - flagged |
| Monitoring for failed refresh, count spikes, stale feeds | TODO | diagnostic report for operators |

## 5. Prove the product and deliver

| Item | Status | Notes |
| --- | --- | --- |
| One real journey through the full pipeline | BLOCKED | needs real data (Pi or NAP) |
| Web in browser, iOS in Simulator, Android in emulator | TODO | capture evidence |
| Deploy web, TestFlight, Play internal testing | TODO | existing credentials; separate verification |

## Deliverable documents to keep current

- `docs/beta/DATA-RECOVERY-INVENTORY.md` (DONE for local scope)
- `docs/beta/OPERATOR-COVERAGE-LEDGER.md` (exists, update after real data)
- `docs/beta/LIVE-MAP-PARITY.md` (TODO)
- `docs/beta/REAL-PRODUCT-ACCEPTANCE.md` (TODO)
