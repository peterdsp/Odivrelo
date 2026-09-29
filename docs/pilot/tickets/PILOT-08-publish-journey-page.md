# PILOT-08: Publish one useful journey page

> **Historical document.** This records a decision made under the product's
> former name, HodoMap, which was rejected on 30 September 2026 and replaced
> by **Poravia**. The text below is preserved as written, including the old
> name, because rewriting a dated decision would falsify the record. See
> [the brand decision](../../../docs/beta/BRAND-DECISION.md).


- Type: build
- Status: todo
- Step: 3, journey page
- Depends on: PILOT-06, PILOT-07
- Blocks: PILOT-09
- Owner: unassigned
- Estimate: 3 to 5 days

## Goal

Publish one web page for the pilot journey that a traveller can act on with
confidence.

## Why

This is the first milestone: one journey someone can confidently take. Web-first,
no app, no account. The page is the thing the five travellers will test.

## The page must show

- [ ] Where to board: the exact boarding point, terminal, and a map or clear
      directions.
- [ ] When the coach runs: date-valid departure and arrival times, with route
      variants and intermediate stops.
- [ ] How to buy: the operator's official booking link, or verified contact and
      ticket-office details when there is no online sale.
- [ ] When it was last checked: a visible freshness date and the source.
- [ ] Where verified information ends: an explicit coverage boundary, so a
      traveller never crosses from verified into silent unknown.

## Build constraints (provisional)

- Prefer the smallest implementation that ships one correct journey. A static
  page rendered from the PILOT-06 GTFS feed is acceptable.
- Do not adopt a framework, backend, or map service the pilot does not need.
- Semantic, indexable HTML so the page can be found by search.
- Greek, English, and Albanian passenger copy for the journey.
- Accessible: screen reader labels, sufficient contrast, keyboard navigation.
- The page is backed by the reviewed GTFS feed, not hand-typed times.

## Acceptance criteria

- [ ] The page renders the pilot journey from the reviewed feed.
- [ ] All five required elements above are present and correct.
- [ ] The page states its freshness date and coverage boundary.
- [ ] Copy exists in Greek, English, and Albanian.
- [ ] Basic accessibility checks pass.

## Out of scope (provisional, do not build yet)

- Native iOS or Android apps.
- Live tracking or live ETA.
- Accounts, saved trips, or ticket import.
- A second corridor or any national search.
- A routing engine such as MOTIS.

## References

- [Pilot decision](../../PILOT_DECISION.md)
- [Design system](../../DESIGN_SYSTEM.md)
- [Product differentiation strategy, Trip Ready and Confidence Ladder](../../PRODUCT_DIFFERENTIATION.md)
