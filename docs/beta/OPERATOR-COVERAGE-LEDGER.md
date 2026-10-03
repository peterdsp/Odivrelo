# Operator coverage ledger

What real Greek intercity coach data is discovered, imported, validated,
published, stale, or blocked, with the evidence for each state. This is the
honest answer to "how much of the country does Odivrelo actually cover with
real data", kept separate from the invented demonstration dataset that the
1.0.0 beta ships.

Last reviewed: 3 October 2026.

## States

| State | Meaning |
|---|---|
| discovered | A source for this operator or corridor is known and recorded in `data/operators/registry.json`. |
| imported | Content has lawfully entered the candidate ingestion database. |
| validated | A human has reviewed boarding points, service dates and purchase actions against official sources. |
| published | The reviewed, rights-cleared rows are in a public read-only release. |
| stale | A permitted source exists but its data is too old or too incomplete to publish. |
| blocked | Progress is stopped by a specific external prerequisite, recorded with the exact unblocking action. |

## Headline

- **62 federation operators discovered. 42 imported as candidates from the NAP
  2020 workbook. 0 validated, 0 published with real data.** The 42 come from the
  one permitted source (NAP, ODbL 1.0) and are held strictly as candidates: the
  data is 2020 vintage and city-level, so it fails the freshness and
  boarding-evidence gates and is never shipped. The operator directory sources
  remain `rightsStatus: unknown`, so no operator-sourced timetable can lawfully
  reach a release, tracked as [EB-04](EXTERNAL-BLOCKERS.md#eb-04-source-reuse-rights-for-any-real-greek-coach-corridor).
- **Raspberry Pi recovery closed (3 Oct 2026).** The Pi holds no KTEL data: it
  runs the separate Syrmos rail product. The 19,872 figure was an aggregate, never
  retained rows. See [`DATA-RECOVERY-INVENTORY.md`](DATA-RECOVERY-INVENTORY.md).
- **1 national source permitted, but stale and incomplete.** The Greek National
  Access Point long-distance bus dataset is ODbL 1.0 (reuse allowed), but it is
  rejected for publication on fitness, not rights. See the per-source note
  below and [`docs/phase0/NAP-DATASET-EVIDENCE.md`](../phase0/NAP-DATASET-EVIDENCE.md).
- **What is published to production today is the invented Aloria
  demonstration dataset only.** `dataMode` is `demo`, its coordinates sit in
  open ocean so no row can be mistaken for a real terminal, and every client
  shows a persistent, non-dismissible notice in Greek, English and Albanian.
  Nationwide real coverage is therefore not claimed anywhere.

## NAP 2020 candidate import (3 October 2026)

The only permitted real dataset, the Greek NAP long-distance bus workbook
(ODbL 1.0, 2020 vintage), was parsed into the import contract and loaded as
candidates by `scripts/ktel_pipeline.py normalize-nap-xlsx`. Full per-operator
counts are in `docs/beta/nap-reconciliation.json`.

Reconciliation funnel `discovered -> parsed -> candidate -> (not published)`:

| Stage | Count | Note |
|---|---|---|
| Prefecture sheets discovered | 42 | one per prefecture, plus a contact sheet |
| Operators mapped | 42 of 62 | each sheet mapped to a registry operator |
| Route rows parsed | 1,048 | both workbook layouts handled |
| Trips (time-expanded) | 1,340 | comma-separated times expanded to one trip each |
| Distinct cities | 54 | city-level origins and destinations |
| Distinct city pairs | 258 | one line and pattern per pair |
| Service calendars | 228 | from Greek day prose |
| Candidate trips | 1,340 | every row is `publication_state=candidate` |
| **Published** | **0** | 2020 and city-level: fails freshness and boarding gates |

Why nothing is published, by excluded group:

- All 1,340 trips: 2020 vintage, so they fail the freshness gate. They are a
  historical candidate for review, not current service.
- All 263 stops: city-level with no coordinate, so they can never be a physical
  boarding point and stay candidate.
- 513 trips carry an ambiguous day pattern (for example `ΚΑΘΗΜΕΡΙΝΑ`, kept as
  daily with an ambiguity flag) and need human confirmation of the weekdays.
- Ν.ΒΟΙΩΤΙΑΣ spans two operators (ktel-thiva, ktel-livadeia); it is assigned to
  the capital operator and flagged for a reviewer to split.

## Source inventory, from `data/operators/registry.json`

Verified counts on 2 October 2026: 62 operators, 64 sources, all
`retentionMode: metadata_only` (daily directory checks keep content digests
only, not republished content).

| Source group | Count | sourceKind | rightsStatus | State | Evidence |
|---|---|---|---|---|---|
| Operator directory pages (`ktelbus.com/...`) | 62 | `operator_directory` | unknown | discovered, blocked | No operator permission in hand. Directory checks retain metadata and digests only. |
| POAYS national KTEL directory | 1 | `federation_directory` | unknown | discovered, blocked | Federation index. No open data, no licence; see the discovery note below. |
| Greek NAP long-distance bus dataset catalog | 1 | `national_access_point` | permitted | discovered, stale | ODbL 1.0, `fitnessForPublication: rejected`. Verified authoritative catalog metadata on 2 October 2026 (below). |

No operator advances past `discovered` while its source stays
`rightsStatus: unknown`. The compiler joins public releases on
`rights_status = 'allowed'` and on reviewed entities only, and a test proves a
`permission_pending` row stays out of a public release even after a reviewer
approves it, so a stuck state here cannot leak into production by mistake.

## Per-source detail

### Greek NAP long-distance bus dataset (permitted, stale)

- Publisher: Hellenic Institute of Transport. Licence: ODbL 1.0 (attribution
  plus share-alike), so rights are not the blocker.
- Authoritative catalog, re-verified on 2 October 2026 directly from the NAP
  CKAN API (`data.nap.imet.gr`, `package_show`): `license_id = ODbL-1.0`,
  `metadata_modified = 2020-12-01`, `num_resources = 1`, the single XLSX
  resource last modified 2020-09-11.
- Fitness: rejected. It has no boarding points, no coordinates and no stop
  identifiers, only free-text Greek city names, so it cannot distinguish the
  Athens Kifissos and Liosion terminals, which is the product's core promise.
  Calendars are unnormalised prose with no validity period, and many rows carry
  no arrival time. Full dated evidence:
  [`docs/phase0/NAP-DATASET-EVIDENCE.md`](../phase0/NAP-DATASET-EVIDENCE.md).
- Still useful for: operator contact and official-site lineage, and as a
  structural reference for what a real feed must normalise.

## Discovery notes, 2 October 2026

A read-only search for any source that is simultaneously rights-cleared, fresh,
and boarding-point-level for even one Greek intercity corridor. No message was
sent, no form submitted, and no access control bypassed. Conclusion: no such
source was found. Details, so the search does not have to be repeated:

- **NAP mirror editions (to confirm).** The `data.gov.gr` harvested copy of the
  NAP dataset is reported to bundle additional editions dated 2021 and
  2023-05-30, newer than the authoritative catalog's 2020 resource. This was
  seen on the mirror only and is **not yet confirmed from the authoritative
  source**, which still reports 2020-12-01 (verified above). Even if confirmed,
  the 2023 edition reportedly keeps the same shape: city-level names, no
  coordinates, no stop identifiers, prose calendars. It would not change the
  rejected fitness verdict. Action to close out: re-fetch from the authoritative
  NAP host once its download endpoint is back (it returned 502 during the
  search) and record the resource dates.
- **KtelTransitGtfs (GitHub `thalis-ap/KtelTransitGtfs`).** The only artefact
  found with boarding-point-level GTFS for real KTEL names (coordinates, stop
  ids and station descriptions for Lefkada and Kefalonia). Blocked on two
  counts: the repository carries no licence at all, so under default copyright
  it is all rights reserved and not reusable; and its provenance and accuracy
  are undocumented and unverified. Watch-item only. It would become a candidate
  only if an open licence were added and the timetables were confirmed against
  KTEL Lefkadas and KTEL Kefalonias. Not usable for a real-data beta.
- **Ticketing and federation sites, reuse terms.** `ktelbus.com` (the POAYS
  federation site) shows no open data or documented API and no open licence, so
  it is all rights reserved. `e-ktel.com` (KTEL Chania-Rethymno) terms of use
  forbid copying, reproducing or deriving commercial benefit without written
  consent. `dromologiaktel.gr` is a third-party aggregator, not an operator,
  and does not permit reproduction. All prohibited for reuse. TicketWeb stays
  intentionally disabled pending written terms approval, honoured by a gate
  that reads all three environment prefixes so a rename cannot flip it.
- **Aggregators (unverified, access-gated).** The Transitland REST API returned
  Unauthorized without a key, and the Mobility Database did not expose a Greek
  intercity feed without using its search API. Neither positively identified an
  in-scope Greek intercity coach feed; both are recorded as unverified, not as
  absent.
- **Out of scope.** OASA (Athens urban, CC BY-NC) and rail feeds are urban or
  rail, not intercity KTEL, and are not counted as coverage.

## Bottom line

No Greek intercity corridor is published with real data, and none can be until
[EB-04](EXTERNAL-BLOCKERS.md#eb-04-source-reuse-rights-for-any-real-greek-coach-corridor)
clears: documented operator reuse rights plus fresh, complete, boarding-point
level data for at least one corridor, then a human review of boarding points
and purchase actions, then a legal read of the ODbL share-alike obligation
before any database derived from the NAP dataset is published. Flipping
`dataMode` to `real` is that rights-and-review event, not a code change.
