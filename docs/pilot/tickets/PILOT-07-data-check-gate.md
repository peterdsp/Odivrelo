# PILOT-07: Data-check decision gate D1

> **Historical document.** This records a decision made under the product's
> former name, HodoMap, which was rejected on 30 September 2026 and replaced
> by Poravia, itself renamed **Odivrelo** on 1 October 2026. The text below is
> preserved as written, including the old names, because rewriting a dated
> decision would falsify the record. See [the brand decision](../../../docs/beta/BRAND-DECISION.md).


- Type: decision-gate
- Status: todo
- Step: 2, data check
- Depends on: PILOT-02, PILOT-03, PILOT-04, PILOT-05, PILOT-06
- Blocks: PILOT-08
- Owner: decision owner
- Estimate: half a day

## Goal

Decide whether the chosen corridor's data is good enough to publish a confident
journey, or whether to switch corridors.

## Why

The decision is explicit: if the checks fail, choose another corridor. This gate
makes that stop-or-go call a recorded artifact rather than a silent drift into
building on weak data.

## Gate criteria, all must pass

- [ ] Rights: at least one publishable timetable is `permitted`, or a `permitted`
      alternative is in use. (PILOT-02)
- [ ] Freshness: the journey has date-valid departures for the dates it will
      claim, with a recorded last-checked date. (PILOT-03)
- [ ] Completeness: route variants, intermediate stops, and calendars for the
      journey are recorded. (PILOT-03)
- [ ] Boarding: every stop on the journey has a verified exact boarding point with
      valid coordinates. (PILOT-04)
- [ ] Purchase: every operator on the journey has a verified purchase or contact
      action. (PILOT-05)
- [ ] Feed: a reviewed GTFS feed for the journey passes a validator using only
      `permitted` and `approved` data. (PILOT-06)

## Outcomes

- Pass: proceed to PILOT-08. Record the decision, the corridor, and the date.
- Fail: return to PILOT-01, select the fallback corridor, and record why the first
  corridor failed. Switch once. Do not keep the pilot open indefinitely.

## Acceptance criteria

- [ ] A dated pass or fail decision is recorded in `docs/pilot/`.
- [ ] On fail, the specific failing criteria and the switch are recorded.

## References

- [Pilot decision, decision gates](../../PILOT_DECISION.md)
