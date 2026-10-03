# Current-source acquisition ledger

Dated operator-by-operator ledger of current, real KTEL sources, their evidence
and the exact remaining gap to publication. Started 4 October 2026. This is the
discovery and assessment record the delivery plan's Section 4 requires. It is
kept separate from the demonstration dataset and from the historical NAP 2020
candidate import (`OPERATOR-COVERAGE-LEDGER.md`, `nap-reconciliation.json`).

Nothing here has been scraped, ingested or published. Each entry records an
official URL and what was verified about it, so a reviewer can decide. Operator
reuse rights are `unknown` until confirmed in writing, and no operator message is
sent without the owner's authorisation.

## Method and rules

- Record, per source: official URL, operator, retrieval time, publication and
  effective dates, allowed data classes, reuse and retention evidence, physical
  stop evidence, parser status, and the exact remaining gap.
- A stored `unknown` rights flag is an unfinished assessment, not a grant and not
  a prohibition. Investigate the evidence; do not assume either way.
- Do not manufacture boarding coordinates from offices or city centroids, do not
  infer a whole timetable from ticket availability, and do not relabel scraped
  content as `manual-review`.
- A search-result crawl date is not a timetable effective date. Verify current
  validity, exact boarding points and reuse rights separately for each source.

## National source status

- Greek NAP long-distance bus dataset. Verified 4 October 2026 at
  `https://data.nap.imet.gr/dataset/information-about-transport-by-long-distance-buses-in-greece`:
  the dataset has exactly one resource, the XLSX last updated 1 December 2020.
  There is no resource newer than 2020, so the retained file is the latest. ODbL
  1.0. The `data.nap.gov.gr` mirror currently serves an expired TLS certificate;
  the `data.nap.imet.gr` origin resolves. Already imported as candidates; it is
  historical and city-level, so it is not publishable as current service.

## Pilot corridors

### Athens to Delphi, operator ktel-fokidas (KTEL Fokida)

| Field | Value |
|---|---|
| Official URL | `https://www.ktel-fokidas.gr/dromologia/` and the route page `.../delfoi-athina-delfoi/` |
| Retrieved | 2026-10-04 |
| Departures verified | Delphi to Athens 05:30 (daily), 11:00 (daily), 14:30 (Mon-Fri), 16:00 (daily), 18:30 (daily); Athens to Delphi 08:30, 10:30, 15:00, 18:00 (daily) |
| Effective dates | Labelled "χειμερινά δρομολόγια" (winter schedule); no explicit date range on the page |
| Allowed data classes | timetable (departure times, days); fares not on this page |
| Reuse / retention rights | unknown. No licence or terms statement found on the page |
| Physical stop evidence | none. City endpoints only; no boarding coordinates |
| Parser status | not built. A page adapter is feasible, but blocked on rights |
| Remaining gap | written reuse permission; the exact boarding points and their coordinates; the schedule's effective date range |

This is the tractable first corridor: a current, official timetable with real
departure times exists. It is not publishable yet because reuse rights are
unconfirmed and the boarding points are city-level.

### Athens to Nafplio, operator ktel-argolida (KTEL Argolida)

| Field | Value |
|---|---|
| Official URL | `https://www.ktelargolida.gr/` (schedules under `/en/3226-2/`) |
| Retrieved | 2026-10-04 (site identified; timetable page not yet parsed) |
| Departures verified | not yet; third-party aggregators report roughly every 2 hours, which is not an authoritative source |
| Effective dates | not verified |
| Allowed data classes | timetable, fare (to assess on the official page) |
| Reuse / retention rights | unknown |
| Physical stop evidence | none verified |
| Parser status | not built |
| Remaining gap | read the official schedule page for exact times and validity; assess reuse rights; obtain boarding points |

## All federation operators

The 62 federation operators plus the two additional known TicketWeb tenants are
inventoried in `data/operators/registry.json` and
`server/ktel-staging/pkg/ktel/operators.json`, each with its official directory
URL. Their current status is uniform and unchanged: discovered, directory URL on
record, `rightsStatus: unknown`, not acquired. This ledger is extended operator
by operator as each official timetable page is read and assessed, starting from
the two pilot corridors above. The aggregate counts (5,378 / 19,872 / 511) are
TicketWeb observations, not a dataset to reuse.

## Live vehicle feeds

Investigated separately from timetable and booking endpoints. No permitted KTEL
vehicle-position (GPS) feed has been identified. The Syrmos live feeds (OASA
telematics, rail) are a different product and must not be substituted for KTEL
coverage. Live coverage remains incomplete, with the exact missing provider being
any permitted KTEL vehicle-position source.

## Prepared owner actions

1. Decide whether to request written reuse permission from KTEL Fokida for the
   Athens to Delphi timetable, and from KTEL Argolida for Athens to Nafplio. A
   ready-to-send request can be prepared on request; it is not sent without
   authorisation.
2. If permission is granted, a bounded page adapter per source, with provenance
   and review, can ingest the current times as candidates, then obtain and verify
   the physical boarding points before publication.
