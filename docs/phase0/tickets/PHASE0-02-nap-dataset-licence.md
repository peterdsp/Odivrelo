# PHASE0-02: Acquire and licence-clear the NAP KTEL dataset

> **Historical document.** This records a decision made under the product's
> former name, HodoMap, which was rejected on 30 September 2026 and replaced
> by Poravia, itself renamed **Odivrelo** on 1 October 2026. The text below is
> preserved as written, including the old names, because rewriting a dated
> decision would falsify the record. See [the brand decision](../../../docs/beta/BRAND-DECISION.md).


- Type: data-check
- Status: done, 30 September 2026, outcome no-go
- Phase: 0, clear the runway
- Depends on: none. Pairs with the corridor named in PILOT-01.
- Blocks: PILOT-02, PILOT-03, PILOT-06
- Owner: unassigned
- Estimate: 2 to 4 days, plus external response time

## Goal

Obtain the Greek National Access Point long-distance bus (KTEL) dataset lawfully,
record its exact reuse licence, and inventory its structure, so the pilot
corridor feed can be built from a permitted source.

## Why

Per `data/operators/registry.json`, every operator directory source is
`rightsStatus: unknown` and only the NAP catalog is `permitted`. The NAP dataset
is therefore the one national permitted seed for the pilot. Two things about it
are unconfirmed and must be established before the feed is built:

1. Its precise reuse licence. The Greek NAP carries a mix of licences, including
   a Non-Commercial Government Licence that would bar commercial reuse of a
   specific dataset. The registry marks the catalog `permitted`, but the exact
   dataset terms need direct confirmation.
2. Its shape. The dataset is published as a spreadsheet of routes and timetables,
   not GTFS. The repository already has a `stage-nap-xlsx` command that stages
   such a workbook without publishing it, but it has had no lawful input yet.

## Tasks

- [ ] Retrieve the dataset from the NAP with bounded, backed-off requests. Do not
      bypass TLS verification if the certificate is invalid. Escalate per the
      execution plan NAP recovery order instead.
- [ ] Record source URL, retrieval time, SHA-256 digest, publication or effective
      date, and the exact licence text with a link.
- [ ] Run `stage-nap-xlsx` to inventory sheets, headers, and sample rows without
      publishing any entity.
- [ ] Confirm whether the pilot corridor operators appear in the dataset, and
      with which fields. For the primary corridor these are KTEL Fokida, and the
      Livadeia or Thiva leg.
- [ ] Write a short go or no-go note: can this dataset seed PILOT-06, or must
      PILOT-02 pursue operator permission for the corridor instead.

## Data and rights

- Retain the exact permitted original plus full lineage. If the licence forbids
  redistribution, do not commit the raw workbook; keep metadata and digest only.
- Do not bypass TLS verification. An expired certificate is a blocker to
  escalate, not to work around.
- No passenger data, credentials, or runtime database files enter the repository.

## Acceptance criteria

- [ ] The NAP dataset licence is recorded with dated evidence and a link.
- [ ] A sheet and column inventory exists, produced by the staging command.
- [ ] Corridor coverage in the dataset is known and written down.
- [ ] A go or no-go note states whether the dataset can seed the pilot feed.

## Kill or switch criteria

- If the dataset licence is non-commercial or otherwise prohibits the intended
  use, and no operator permission is reachable for the corridor, flag it to
  PILOT-02 and consider the fallback corridor in PILOT-01.

## Out of scope (provisional, do not build yet)

- Mapping reviewed columns into published entities. That is PILOT-06.
- Importing the full national workbook or all 62 operators.
- The OASA urban open-data licence question. That is a separate, later concern
  for multimodal first and last mile, not the pilot seed.

## References

- [National execution plan, NAP recovery order](../../NATIONAL_EXECUTION_PLAN.md)
- [Data governance, rights states and lineage](../../DATA_GOVERNANCE.md)
- [Corridor brief](../../pilot/CORRIDOR_BRIEF.md)
- `scripts/ktel_pipeline.py stage-nap-xlsx`

## Outcome (30 September 2026)

Done. The dataset was retrieved lawfully, its licence recorded, its structure
inventoried, and corridor coverage established. The answer is **no-go**.

- Licence is **ODbL 1.0**, not the feared non-commercial government licence.
  Reuse including commercial reuse is permitted, subject to attribution of the
  Hellenic Institute of Transport and share-alike on derived databases.
- `data.nap.gov.gr` has a certificate that expired on 15 April 2026. TLS
  verification was **not** bypassed; the working mirror `data.nap.imet.gr` was
  used instead.
- SHA-256 `b01d4711774b7a87c70241c420b021da62a38d709518ae0348e7bd066987d856`,
  231,397 bytes, dataset last updated **1 December 2020**.
- 42 prefecture sheets, 1,184 non-empty rows, two incompatible layouts.
- **`ΔΕΛΦΟΙ` appears zero times.** The primary pilot corridor does not exist in
  the dataset. KTEL Fokida's sheet covers Amfissa only.
- **No boarding points, no coordinates, no stop identifiers.** Origin and
  destination are free-text city names, so the Athens Kifissos-versus-Liosion
  terminal question, which is the product's core promise, cannot be answered
  from this source at all.
- Calendars are unnormalised Greek prose with no validity period. Most rows
  have no arrival time. The Achaia sheet records
  `ΤΡΟΠΟΠΟΙΟΥΝΤΑΙ ΚΆΘΕ ΕΒΔΟΜΑΔΑ` in place of departure times.

Full evidence: [NAP-DATASET-EVIDENCE.md](../NAP-DATASET-EVIDENCE.md).

This refutes pilot assumptions 2, 3 and 4 for this source and fails Gate D1 on
evidence. It is handed to PILOT-02 and PILOT-03, and it is the reason
[AD-002](../../beta/ARCHITECTURE-DECISIONS.md) ships a labelled demonstration
dataset instead.
