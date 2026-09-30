# PILOT-10: Pilot outcome gate D2 and hold on expansion

> **Historical document.** This records a decision made under the product's
> former name, HodoMap, which was rejected on 30 September 2026 and replaced
> by Poravia, itself renamed **Odivrelo** on 1 October 2026. The text below is
> preserved as written, including the old names, because rewriting a dated
> decision would falsify the record. See [the brand decision](../../../docs/beta/BRAND-DECISION.md).


- Type: decision-gate
- Status: todo
- Step: 5, expand only after it works
- Depends on: PILOT-09
- Blocks: any expansion
- Owner: decision owner
- Estimate: half a day

## Goal

Decide, from the test evidence, whether the pilot worked, and hold all expansion
until a concrete need is documented.

## Why

Expansion is earned, not assumed. MOTIS, native apps, live tracking, and national
coverage stay deferred until the pilot exposes a concrete need. This gate is where
that discipline is enforced.

## Gate criteria

- [ ] The five-traveller test shows travellers complete the journey task more
      reliably on the HodoMap page than on the operator site. (PILOT-09)
- [ ] Boarding point correctness on the HodoMap page is high, not marginal.
- [ ] The ticket or contact handoff worked for participants.

## Outcomes

- Pass: the pilot worked. Record the result. Before any expansion, document one
  concrete need that expansion would serve, and reopen the relevant national plan
  scope deliberately, one step at a time.
- Fail or marginal: revise the journey page and retest, or reconsider the premise.
  Do not expand.

## Expansion stays deferred until this gate passes and a need is documented

- A second corridor.
- Native iOS and Android apps.
- Live coach tracking and live ETA.
- National coverage across the 62 operators.
- A routing engine such as MOTIS.
- Raspberry Pi production deployment as a launch dependency.

## Keep architecture choices provisional

Shipping the pilot does not lock in an architecture. Any decision to commit to a
long-lived platform shape is a separate, explicit decision taken after this gate,
informed by what the pilot actually required.

## Acceptance criteria

- [ ] A dated pass, fail, or revise decision is recorded in `docs/pilot/`.
- [ ] On pass, one concrete need is documented before any expansion ticket opens.
- [ ] No deferred item above is started before this gate passes.

## References

- [Pilot decision](../../PILOT_DECISION.md)
- [Roadmap](../../ROADMAP.md)
- [National execution plan](../../NATIONAL_EXECUTION_PLAN.md)
