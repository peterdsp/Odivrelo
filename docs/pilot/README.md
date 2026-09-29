# Pilot backlog: web-first, one-corridor

> **Historical document.** This records a decision made under the product's
> former name, HodoMap, which was rejected on 30 September 2026 and replaced
> by **Poravia**. The text below is preserved as written, including the old
> name, because rewriting a dated decision would falsify the record. See
> [the brand decision](../../docs/beta/BRAND-DECISION.md).


This backlog turns the [pilot decision](../PILOT_DECISION.md) into concrete,
trackable tickets. It covers exactly two headline deliverables:

1. The data check for one corridor.
2. The one-corridor pilot: one journey page plus a five-traveller test.

Nothing here commits the project to the full national architecture. Architecture
choices stay provisional until Gate D2 passes.

## First milestone

One journey someone can confidently take. Not a national transport platform.

## Tickets

| ID | Title | Type | Step | Depends on | Issue |
|---|---|---|---|---|---|
| [PILOT-01](tickets/PILOT-01-select-corridor.md) | Select the pilot corridor and operators, with a fallback | research | 2 | - | [#2](https://github.com/peterdsp/HodoMap/issues/2) |
| [PILOT-02](tickets/PILOT-02-confirm-reuse-rights.md) | Confirm data reuse rights per operator | data-check | 2 | PILOT-01 | [#3](https://github.com/peterdsp/HodoMap/issues/3) |
| [PILOT-03](tickets/PILOT-03-confirm-freshness-completeness.md) | Confirm timetable freshness and completeness | data-check | 2 | PILOT-01 | [#4](https://github.com/peterdsp/HodoMap/issues/4) |
| [PILOT-04](tickets/PILOT-04-verify-boarding-points.md) | Verify exact boarding locations | data-check | 2 | PILOT-01 | [#5](https://github.com/peterdsp/HodoMap/issues/5) |
| [PILOT-05](tickets/PILOT-05-verify-purchase-action.md) | Verify the official purchase or contact action | data-check | 2 | PILOT-01 | [#6](https://github.com/peterdsp/HodoMap/issues/6) |
| [PILOT-06](tickets/PILOT-06-reviewed-gtfs-feed.md) | Build a reviewed GTFS feed for the corridor | build | 2 | PILOT-02, PILOT-03, PILOT-04 | [#7](https://github.com/peterdsp/HodoMap/issues/7) |
| [PILOT-07](tickets/PILOT-07-data-check-gate.md) | Data-check decision gate D1 | decision-gate | 2 | PILOT-02..06 | [#8](https://github.com/peterdsp/HodoMap/issues/8) |
| [PILOT-08](tickets/PILOT-08-publish-journey-page.md) | Publish one useful journey page | build | 3 | PILOT-06, PILOT-07 | [#9](https://github.com/peterdsp/HodoMap/issues/9) |
| [PILOT-09](tickets/PILOT-09-five-traveller-test.md) | Test with five likely travellers | test | 4 | PILOT-08 | [#10](https://github.com/peterdsp/HodoMap/issues/10) |
| [PILOT-10](tickets/PILOT-10-pilot-outcome-gate.md) | Pilot outcome gate D2 and hold on expansion | decision-gate | 5 | PILOT-09 | [#11](https://github.com/peterdsp/HodoMap/issues/11) |

## Flow

```text
PILOT-01  select corridor (+ fallback)
   |
   +--> PILOT-02 rights
   +--> PILOT-03 freshness and completeness
   +--> PILOT-04 boarding points
   +--> PILOT-05 purchase or contact action
            |
            v
        PILOT-06 reviewed GTFS feed
            |
            v
        PILOT-07 Gate D1  --- fail ---> back to PILOT-01 with another corridor
            | pass
            v
        PILOT-08 journey page
            |
            v
        PILOT-09 five-traveller test
            |
            v
        PILOT-10 Gate D2  --- fail ---> revise, do not expand
            | pass
            v
        document a concrete need before any expansion
```

## Working rules

- Governance first. Only `permitted` sources and `approved` entities can reach a
  public page. See [DATA_GOVERNANCE.md](../DATA_GOVERNANCE.md).
- Visible boundaries. The page must show where verified information ends.
- Provisional build. Prefer the smallest implementation that ships one correct
  journey. Do not adopt a framework or service the pilot does not need.
- One switch, not many. If the data check fails, switch corridor once via
  PILOT-01 and record why. Do not keep the pilot open indefinitely.

## Status legend

Each ticket carries a `Status` line: `todo`, `in-progress`, `blocked`, `done`, or
`switched`.
