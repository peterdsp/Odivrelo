# PILOT-02: Confirm data reuse rights per operator

> **Historical document.** This records a decision made under the product's
> former name, HodoMap, which was rejected on 30 September 2026 and replaced
> by **Poravia**. The text below is preserved as written, including the old
> name, because rewriting a dated decision would falsify the record. See
> [the brand decision](../../../docs/beta/BRAND-DECISION.md).


- Type: data-check
- Status: todo
- Step: 2, data check
- Depends on: PILOT-01
- Blocks: PILOT-06, PILOT-07
- Owner: unassigned
- Estimate: 2 to 5 days, plus external response time

## Goal

For every operator on the chosen corridor, establish a documented rights state
for the timetable data we intend to publish. Only `permitted` data can reach the
journey page.

## Why

An accessible endpoint is not a reusable public dataset. Today, per
`data/operators/registry.json`, all 62 operator directory sources are
`rightsStatus: unknown` and only the NAP catalog is `permitted`. Publishing on
`unknown` rights would break the project's own governance and its independence
posture.

## Tasks

- [ ] For each corridor operator, identify the specific source that carries the
      timetable we would publish.
- [ ] Determine and document the rights state: `permitted`,
      `permission_pending`, `restricted_internal`, `prohibited`, or `unknown`.
- [ ] Where rights are `unknown`, decide whether to request permission or to rely
      on a `permitted` source such as the NAP dataset instead.
- [ ] Record the evidence: source URL, contact channel, date, and any written
      response, following the lineage rules in DATA_GOVERNANCE.
- [ ] Confirm the official booking deep link is allowed to be linked.

## Data and rights

- Retain provenance for every source: identifier, operator, retrieval time,
  effective period, source URL, content digest, parser or reviewer, rights state.
- Do not commit raw private booking responses, credentials, or rights-pending
  bodies.

## Acceptance criteria

- [ ] Each corridor operator has exactly one recorded rights state with dated
      evidence.
- [ ] At least one operator's publishable timetable is `permitted`, or a
      `permitted` alternative source is identified for it.
- [ ] Linking to each official booking or contact page is confirmed acceptable.

## Kill or switch criteria

- If no corridor operator can reach `permitted` for a publishable timetable, and
  no `permitted` alternative exists, this corridor fails. Return to PILOT-01 and
  select the fallback.

## Out of scope (provisional, do not build yet)

- National rights matrix across all 62 operators.
- TicketWeb provider negotiation beyond what this corridor needs.

## References

- [Data governance](../../DATA_GOVERNANCE.md)
- [National execution plan, phase 1 legal and source authority](../../NATIONAL_EXECUTION_PLAN.md)
