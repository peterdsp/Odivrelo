# PILOT-04: Verify exact boarding locations

> **Historical document.** This records a decision made under the product's
> former name, HodoMap, which was rejected on 30 September 2026 and replaced
> by Poravia, itself renamed **Odivrelo** on 1 October 2026. The text below is
> preserved as written, including the old names, because rewriting a dated
> decision would falsify the record. See [the brand decision](../../../docs/beta/BRAND-DECISION.md).


- Type: data-check
- Status: todo
- Step: 2, data check
- Depends on: PILOT-01
- Blocks: PILOT-06, PILOT-07
- Owner: unassigned
- Estimate: 2 to 4 days

## Goal

Verify the actual, exact boarding location for every stop used in the pilot
journey, not just the city or terminal name.

## Why

Knowing exactly where to board is the difference between a plan and a missed
coach. In Athens the Liosion versus Kifissos question is a concrete example: the
right terminal is not obvious from a city name. This is the boarding-point promise
at the heart of the certainty layer.

## Tasks

- [ ] For each stop on the pilot journey, identify the exact boarding point:
      terminal name, address, and where the coach physically departs.
- [ ] Capture coordinates and validate them before use. Quarantine invalid
      coordinates.
- [ ] Confirm the origin terminal is correct when a hub has more than one
      terminal.
- [ ] Preserve original Greek names and identifiers alongside transliterations.
- [ ] Record the evidence source and observation date for each boarding point.

## Data and rights

- Keep station complexes separate from boarding points.
- Never merge stops on name similarity alone. Require human review for ambiguous
  or cross-operator terminal matches.

## Acceptance criteria

- [ ] Every stop on the pilot journey has a verified, exact boarding point with
      validated coordinates.
- [ ] Multi-terminal hubs on the journey resolve to the correct terminal.
- [ ] Original names and identifiers are preserved.

## Kill or switch criteria

- If a boarding point on the journey cannot be verified with confidence, either
  narrow the pilot journey to stops that can be verified, or return to PILOT-01
  and select the fallback.

## Out of scope (provisional, do not build yet)

- National stop normalization across the 19,872 source stops.
- Automated geometry review.

## References

- [Data governance, stop normalization](../../DATA_GOVERNANCE.md)
