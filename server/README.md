# Server

Odivrelo currently has two server areas with different maturity:

## Active acquisition monitor

`src/odivrelo_pipeline/` is the deployed, tested daily source monitor.

It owns:

- The validated 62-operator registry.
- Conditional, rate-limited HTTP checks.
- Metadata-only monitoring when rights are unknown.
- Permitted artifact retention.
- Source-change and discovered-link review queues.
- Raspberry Pi run reports.

It does not publish timetables.

## Transplanted KTEL foundation

`ktel-staging/` contains the earlier KTEL database, compiler, API and bounded
TicketWeb foundation copied from the Syrmos working tree.

It is not yet:

- Renamed from `syrmos_admin`.
- Integrated with `odivrelo_pipeline`.
- Included in the deployed daily service.
- Reverified against Odivrelo paths and dependencies.
- Authorized for TicketWeb public redistribution.
- A complete national timetable database.

Treat it as migration input, not the current production service. Preserve its
tests and rights gates while moving capabilities into the final Odivrelo server
modules.

Current verification note: its transplanted test suite does not import because
`syrmos_admin.generator` was not copied with the KTEL modules. The failure is
isolated to `ktel-staging`; the deployed `odivrelo_pipeline` tests pass.
