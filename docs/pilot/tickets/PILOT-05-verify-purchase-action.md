# PILOT-05: Verify the official purchase or contact action

> **Historical document.** This records a decision made under the product's
> former name, HodoMap, which was rejected on 30 September 2026 and replaced
> by Poravia, itself renamed **Odivrelo** on 1 October 2026. The text below is
> preserved as written, including the old names, because rewriting a dated
> decision would falsify the record. See [the brand decision](../../../docs/beta/BRAND-DECISION.md).


- Type: data-check
- Status: todo
- Step: 2, data check
- Depends on: PILOT-01
- Blocks: PILOT-07, PILOT-08
- Owner: unassigned
- Estimate: 1 to 2 days

## Goal

For each corridor operator, verify a working way to buy: either a tested official
booking deep link, or a verified physical purchase and contact fallback.

## Why

The journey page routes travellers to the operator to buy. HodoMap does not sell
tickets. The handoff must be correct, or the page sends people to a dead end.

## Tasks

- [ ] For operators with online sales, test the official booking link end to end
      to the point of purchase, without completing a purchase.
- [ ] For operators without online sales, verify phone, email, ticket-office
      address, opening hours, and directions.
- [ ] Record the redirect chain and the final official host for each link.
- [ ] Note any booking rules a traveller must know, such as luggage or seasonal
      restrictions relevant to the journey.
- [ ] Record observation date and reviewer for each action.

## Acceptance criteria

- [ ] Every corridor operator has a verified purchase or contact action.
- [ ] Each online link resolves to an official operator or authorized booking
      host.
- [ ] Contact-only operators have complete, dated ticket-office details.

## Kill or switch criteria

- If an operator on the journey has neither a working official purchase link nor
  verified contact details, either drop that operator from the pilot journey or
  return to PILOT-01 and select the fallback.

## Out of scope (provisional, do not build yet)

- In-app ticket issuance or payment.
- National booking-provider classification.

## References

- [National execution plan, coverage level R5](../../NATIONAL_EXECUTION_PLAN.md)
- [Product differentiation strategy](../../PRODUCT_DIFFERENTIATION.md)
