# Phase 0 backlog: clear the runway

> **Historical document.** This records a decision made under the product's
> former name, HodoMap, which was rejected on 30 September 2026 and replaced
> by **Poravia**. The text below is preserved as written, including the old
> name, because rewriting a dated decision would falsify the record. See
> [the brand decision](../../docs/beta/BRAND-DECISION.md).


This backlog covers the engineering and data prerequisites that unblock the
[web-first one-corridor pilot](../pilot/README.md) without committing the project
to the full national architecture.

Phase 0 is deliberately small. It removes the transplanted Syrmos identity from
HodoMap code, gets one lawful permitted data seed in hand, proves the existing
pipeline runs under its new name, and confirms the repository holds no runtime
state or secrets. Nothing here builds a national compiler, a served API, a
routing engine, or a hosting service. Those stay deferred until the pilot proves
value at Gate D2 in the pilot backlog.

## Why a separate track

The pilot backlog (Steps 2 to 5) is the data check and the one journey page. It
assumes the tooling it leans on already runs and that a permitted data source
exists. Those assumptions are Phase 0 work. Keeping them here keeps the pilot
backlog focused on the traveller-facing milestone.

## Tickets

| ID | Title | Type | Depends on | Unblocks | Issue |
|---|---|---|---|---|---|
| [PHASE0-01](tickets/PHASE0-01-rename-syrmos-package.md) | Rename the transplanted `syrmos_admin` package to a HodoMap name | build | none | PHASE0-03, PILOT-06 | [#12](https://github.com/peterdsp/HodoMap/issues/12) |
| [PHASE0-02](tickets/PHASE0-02-nap-dataset-licence.md) | Acquire and licence-clear the NAP KTEL dataset | data-check | none | PILOT-02, PILOT-03, PILOT-06 | [#13](https://github.com/peterdsp/HodoMap/issues/13) |
| [PHASE0-03](tickets/PHASE0-03-run-pipeline-under-new-name.md) | Run the transplanted pipeline end to end under the new name | build | PHASE0-01 | PILOT-06 | [#14](https://github.com/peterdsp/HodoMap/issues/14) |
| [PHASE0-04](tickets/PHASE0-04-repo-hygiene-audit.md) | Repository runtime-state and secrets hygiene audit | hygiene | none | a clean pilot start | [#15](https://github.com/peterdsp/HodoMap/issues/15) |

## Flow

```text
PHASE0-01 rename package ----> PHASE0-03 run pipeline under new name
                                        |
PHASE0-02 NAP dataset licence ----------+----> feeds PILOT-02, PILOT-03, PILOT-06
                                        |
PHASE0-04 repo hygiene audit -----------+----> clean start for the pilot
```

## Exit condition

Phase 0 is done when:

- No `syrmos` identifier remains in HodoMap code, and the pipeline tests pass
  under the new package name.
- One permitted data seed for the pilot corridor is in hand, with its licence
  and shape recorded.
- The repository holds no tracked runtime database, secret, or rights-restricted
  body.

At that point the pilot backlog can start its data check on solid ground.

## Working rules

- Governance first. Only `permitted` sources and `approved` entities can reach a
  public page. See [DATA_GOVERNANCE.md](../DATA_GOVERNANCE.md).
- Smallest change that clears the blocker. Do not adopt a framework, service, or
  compiler the pilot does not need.
- No national architecture yet. GTFS export, a served API, MOTIS, and hosting
  stay deferred until the pilot earns them.
- Leave the live Syrmos service intact. Do not delete the Syrmos originals that
  its own server still references.

## Status legend

Each ticket carries a `Status` line: `todo`, `in-progress`, `blocked`, `done`, or
`switched`.

## References

- [Pilot backlog](../pilot/README.md)
- [Product and strategy direction, phased roadmap](../ROADMAP.md)
- [National execution plan, phase 0 and phase 2](../NATIONAL_EXECUTION_PLAN.md)
- [Architecture](../ARCHITECTURE.md)
- [Data governance](../DATA_GOVERNANCE.md)
