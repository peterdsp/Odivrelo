# PHASE0-03: Run the transplanted pipeline end to end under the new name

- Type: build
- Status: todo
- Phase: 0, clear the runway
- Depends on: PHASE0-01
- Blocks: PILOT-06
- Owner: unassigned
- Estimate: 1 to 2 days

## Goal

Prove the ingest, review, and publish pipeline runs end to end on a tiny fixture
under the renamed HodoMap package, producing a compiled read-only public
database, with tests green. No new features.

## Why

PILOT-06 builds the reviewed feed for the corridor and will lean on this
compiler and query layer. It was transplanted verbatim and has never been run
wired into HodoMap. Proving it runs on a small fixture now removes a hidden risk
from the pilot, and produces a runbook the pilot can follow.

Known gap found in PHASE0-01: `tests/test_ktel.py` imports a `generator` snapshot
module that was never transplanted from Syrmos, so the suite currently fails at
import. The Syrmos snapshot generator is being superseded by GTFS export anyway.
This ticket must either port a minimal replacement or drop that obsolete test
path, then get the suite green.

## Tasks

- [ ] Seed the operator registry into a scratch database.
- [ ] Import a minimal normalized fixture through `import-normalized`.
- [ ] Move one entity through `review` to a published state.
- [ ] Run `publish` to compile a read-only public database with a release id.
- [ ] Confirm the read-only query functions return the compiled rows.
- [ ] Run the test suite and confirm it passes under the new name.
- [ ] Write a one-page runbook listing the exact commands in order.

## Data and rights

- Use only synthetic or clearly permitted fixtures.
- Keep scratch databases out of version control. No `.db`, `-wal`, or `-shm`
  files are committed.

## Acceptance criteria

- [ ] The seed, import, review, publish, and query steps run in sequence without
      error.
- [ ] A compiled public database is produced and carries a release id.
- [ ] Tests pass.
- [ ] A one-page runbook exists under `docs/phase0/`.

## Kill or switch criteria

- If the transplanted compiler needs substantial rework to run, record the gap
  and scope it as its own ticket. Do not expand this into the national compiler
  now.

## Out of scope (provisional, do not build yet)

- GTFS export from the public database. That is part of PILOT-06.
- A served HTTP API or CDN hosting.
- A national compile across operators.

## References

- [KTEL staging README](../../../server/ktel-staging/README.md)
- [Data governance, publication gate](../../DATA_GOVERNANCE.md)
- [Architecture, database separation](../../ARCHITECTURE.md)
