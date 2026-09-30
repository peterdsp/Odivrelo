# Decision: web-first, one-corridor pilot

> **Historical document.** This records a decision made under the product's
> former name, HodoMap, which was rejected on 30 September 2026 and replaced
> by Poravia, itself renamed **Odivrelo** on 1 October 2026. The text below is
> preserved as written, including the old names, because rewriting a dated
> decision would falsify the record. See [the brand decision](../docs/beta/BRAND-DECISION.md).


Status: approved, provisional

Date: 6 September 2026

Decision owner: product lead

Supersedes scope ambition, not content, of the national plans below.

## Decision

Approve a small, web-first, one-corridor pilot. Do not approve the whole
architecture yet.

The existing reports (`NATIONAL_EXECUTION_PLAN.md`,
`KTEL_NATIONAL_PLATFORM.md`, `PRODUCT_DIFFERENTIATION.md`,
`LIVE_COACH_MAP_AND_ETA.md`) describe a sensible sequence. The claim that market
research validates every premise is stronger than the evidence currently shown.
We therefore treat the direction as a hypothesis to test, not a plan to build in
full.

The first milestone is one journey someone can confidently take, not a national
transport platform.

## What is approved now

1. Record this direction and its unresolved assumptions in `docs/`.
2. Check one corridor's data: reuse rights, freshness, timetable completeness,
   and actual boarding locations. If those checks fail, choose another corridor.
3. Publish one useful web journey page: where to board, when the coach runs, the
   operator's official booking link, and when the information was last checked. A
   reviewed GTFS feed should support it.
4. Test it with five likely travellers. Can they find the correct departure,
   boarding point, and ticket link more reliably than using the operator's own
   site? Watch them try.
5. Expand only after that works.

The concrete work is tracked in [the pilot backlog](pilot/README.md).

## What is explicitly not approved yet

These stay provisional. Defer them until the pilot exposes a concrete need:

- MOTIS or any national routing engine.
- Native iOS and Android apps.
- Live coach tracking and live ETA.
- National coverage across the 62 operators.
- Raspberry Pi production deployment as a launch dependency.
- The full R0 to R6 program as a precondition for the first journey page.

Architecture choices remain provisional. The pilot may reuse existing scaffolding
where convenient, but no long-lived architectural commitment is made by shipping
one journey page. A static page plus a reviewed data file is an acceptable pilot
implementation.

## Why this shape

- Evidence before coverage is already a stated product principle. A single
  verified journey tests that principle at the smallest honest unit.
- The differentiation strategy already argues for a Web-first launch wedge and a
  connected corridor, with expansion earned by measured accuracy, task
  completion, and distribution. This decision holds the project to that gate
  instead of building ahead of it.
- The cheapest way to learn whether the certainty layer is real is to put one
  journey in front of five travellers and watch them, not to complete a national
  data build first.

## Unresolved assumptions to test

The pilot exists to convert these from assumed to evidenced. Each should end the
pilot either supported or refuted.

1. Travellers cannot reliably find the correct departure, boarding point, and
   ticket link on the operator's own site. This is the core pain and is currently
   assumed.
2. At least one corridor has reuse rights we can document as `permitted`. Today,
   per `data/operators/registry.json`, 62 operator directory sources are
   `unknown` and only the NAP catalog is `permitted`.
3. That corridor's timetable is fresh, complete, and includes real, exact
   boarding locations, not just city names.
4. A reviewed GTFS feed for one corridor can be produced within the governance
   rules in [DATA_GOVERNANCE.md](DATA_GOVERNANCE.md).
5. A web page, with no app and no account, delivers enough value to be worth
   using and is discoverable.
6. Visible provenance and freshness are things travellers actually value, not
   only things the team values.
7. The market-size and demand claims in the national reports are unproven and are
   not a pilot dependency.

## Decision gates

- Gate D1, after the data check: rights, freshness, completeness, and boarding
  points all pass for one corridor, or we switch corridors. See
  [PILOT-07](pilot/tickets/PILOT-07-data-check-gate.md).
- Gate D2, after the user test: five travellers complete the journey task more
  reliably on the HodoMap page than on the operator site, or we revise before any
  expansion. See [PILOT-10](pilot/tickets/PILOT-10-pilot-outcome-gate.md).

Expansion to a second corridor, native apps, live data, or national coverage is
out of scope until Gate D2 passes and a concrete need is documented.

## Relationship to existing plans

This decision does not delete the national plans. It sequences them. The national
execution plan and differentiation strategy remain the longer arc. This document
is the current authority on what we build first and what we deliberately hold
back. Where the two disagree on scope, this document wins until Gate D2.

## References

- [Pilot backlog](pilot/README.md)
- [National execution plan](NATIONAL_EXECUTION_PLAN.md)
- [Product differentiation strategy](PRODUCT_DIFFERENTIATION.md)
- [Data governance](DATA_GOVERNANCE.md)
- [Roadmap](ROADMAP.md)
