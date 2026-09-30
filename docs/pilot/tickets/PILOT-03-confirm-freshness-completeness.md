# PILOT-03: Confirm timetable freshness and completeness

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

Confirm that the chosen corridor has a timetable that is current and complete
enough to publish one confident journey.

## Why

A journey page is only useful if the schedule is valid for the date the traveller
travels. Booking observations on one date do not prove a recurring calendar.

## Tasks

- [ ] Establish the effective period and last-updated date of each operator's
      timetable for the corridor.
- [ ] Confirm the schedule is current, not an expired or seasonal page left
      online.
- [ ] Confirm date-valid departures, including weekday, weekend, and any seasonal
      or holiday variation relevant to the pilot journey.
- [ ] Confirm route variants and intermediate stops on the corridor.
- [ ] Confirm service calendars and known exceptions.
- [ ] Record how freshness will be re-checked, and how often, during the pilot.

## Acceptance criteria

- [ ] Each corridor timetable has a recorded effective period and last-checked
      date.
- [ ] The pilot journey has date-valid departures for the dates it will claim.
- [ ] Intermediate stops and route variants for the journey are recorded.
- [ ] A freshness re-check cadence is defined and cheap to run manually.

## Kill or switch criteria

- If the timetable is stale, undated, or cannot be confirmed valid for the travel
  dates, this corridor fails the freshness check. Return to PILOT-01 and select
  the fallback.

## Out of scope (provisional, do not build yet)

- Automated national freshness monitoring.
- Predicted or seasonal offline models.

## References

- [Data governance, timetables](../../DATA_GOVERNANCE.md)
