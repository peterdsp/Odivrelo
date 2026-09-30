# PILOT-06: Build a reviewed GTFS feed for the corridor

> **Historical document.** This records a decision made under the product's
> former name, HodoMap, which was rejected on 30 September 2026 and replaced
> by **Poravia**. The text below is preserved as written, including the old
> name, because rewriting a dated decision would falsify the record. See
> [the brand decision](../../../docs/beta/BRAND-DECISION.md).


- Type: build
- Status: todo
- Step: 2, data check
- Depends on: PILOT-02, PILOT-03, PILOT-04
- Blocks: PILOT-08
- Owner: unassigned
- Estimate: 3 to 5 days

## Goal

Produce a small, reviewed GTFS feed that describes the pilot journey, built only
from `permitted` sources and `approved` entities. This feed supports the journey
page.

## Why

The decision states that a reviewed GTFS feed should support the journey page. A
standard feed keeps the data honest, testable against a validator, and reusable if
the pilot expands. It also forces stops, calendars, and trips to be explicit
rather than hand-typed into a page.

## Tasks

- [ ] Model the corridor as GTFS: `agency`, `stops`, `routes`, `trips`,
      `stop_times`, `calendar`, and `calendar_dates` for exceptions.
- [ ] Use only `permitted` source data from PILOT-02 and `approved` boarding
      points from PILOT-04.
- [ ] Set exact boarding coordinates on `stops`, with original names preserved.
- [ ] Encode date validity and exceptions from PILOT-03.
- [ ] Run a GTFS validator and resolve errors.
- [ ] Keep source lineage: which source and effective period each trip comes from.
- [ ] Store the feed and a short data-provenance note in the repository.

## Data and rights

- No `unknown`, `permission_pending`, or `prohibited` rows in the feed.
- No invalid coordinates. Quarantine and exclude them.
- No passenger data, credentials, or runtime database files.

## Acceptance criteria

- [ ] A GTFS feed for the pilot journey passes a standard validator.
- [ ] Every trip traces to a `permitted` source with an effective period and
      retrieval time.
- [ ] Boarding coordinates match the verified points from PILOT-04.

## Kill or switch criteria

- If a feed cannot be built without including non-`permitted` data or unverified
  boarding points, stop and return to PILOT-07 as a failed data check.

## Out of scope (provisional, do not build yet)

- A national feed or automated national compiler.
- Road geometry beyond what the journey page needs.
- A live GTFS-realtime feed.

## References

- [Data governance, publication gate](../../DATA_GOVERNANCE.md)
- [Architecture](../../ARCHITECTURE.md)
