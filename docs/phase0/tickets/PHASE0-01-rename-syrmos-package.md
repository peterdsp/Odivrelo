# PHASE0-01: Rename the transplanted `syrmos_admin` package to a HodoMap name

> **Historical document.** This records a decision made under the product's
> former name, HodoMap, which was rejected on 30 September 2026 and replaced
> by **Poravia**. The text below is preserved as written, including the old
> name, because rewriting a dated decision would falsify the record. See
> [the brand decision](../../../docs/beta/BRAND-DECISION.md).


- Type: build
- Status: done, 9 September 2026
- Phase: 0, clear the runway
- Depends on: none
- Blocks: PHASE0-03, and cleanly PILOT-06
- Owner: unassigned
- Estimate: 1 to 2 days

## Goal

Rename the transplanted `server/ktel-staging/syrmos_admin` package to a HodoMap
name and remove the Syrmos identity from HodoMap code, with behaviour kept
identical.

## Why

The compiler, ingest, and read-only query layer under `syrmos_admin` was built in
the Syrmos rail tree and copied here verbatim. The staging README records this as
an integration task. Building the pilot on a Syrmos-named package inside HodoMap
is a correctness and independence risk: it confuses ownership, and the product
must not present as affiliated with the rail project or any operator.

## Tasks

- [ ] Choose the target package name (for example `hodomap_ktel`) and record it.
- [ ] Rename the package directory and update the five
      `from syrmos_admin.ktel_*` imports in `scripts/ktel_pipeline.py`.
- [ ] Update `pyproject.toml` packaging and any console-script entry points.
- [ ] Keep `ktel_db` path resolution and the `SYRMOS_KTEL_DB_PATH` style env
      overrides working, renaming the env vars to a HodoMap prefix with a
      documented fallback.
- [ ] Grep the tree for any remaining `syrmos` string in HodoMap code and copy.
- [ ] Run the pipeline test suite and confirm it passes.

## Data and rights

- No data or rights change. This is a code-identity task only.

## Acceptance criteria

- [ ] No `syrmos` identifier remains in HodoMap package names, imports, or copy.
- [ ] `tests/test_ktel.py` passes under the new package name.
- [ ] The pipeline CLI commands run under the new name with no behaviour change.

## Kill or switch criteria

- If renaming risks breaking the still-live Syrmos service that references these
  originals in its own tree, coordinate before proceeding. Do not delete the
  Syrmos originals as part of this ticket.

## Out of scope (provisional, do not build yet)

- Folding the package into `hodomap_pipeline`. That can be a later step.
- Any new feature, GTFS export, or served API.

## Outcome (9 September 2026)

Done and verified for the rename scope.

- `syrmos_admin` was renamed to `hodomap_ktel` with `git mv`, history preserved.
  The modules' relative imports were unaffected.
- Updated the absolute imports in `scripts/ktel_pipeline.py` and
  `tests/test_ktel.py`, the CLI help text, the `ktel.env.example` names, the seed
  label and base URL in `pkg/ktel/operators.json`, and the TicketWeb User-Agent.
- Env vars are now `HODOMAP_KTEL_DB_PATH` and `HODOMAP_KTEL_PUBLIC_DB_PATH`, with
  the legacy `SYRMOS_KTEL_*` names still read as a fallback so an existing Pi
  deployment keeps working until it migrates.
- Verified: all six `ktel_*` modules import under the new name, `py_compile`
  passes for the package and CLI, and a functional smoke test (migrate then seed)
  returns 64 operator rows and 62 official operators.
- Remaining `syrmos` strings are intentional: the legacy env fallback and factual
  references to the separate Syrmos project. `syrmos-api-integration.patch` was
  left untouched. It patches the external Syrmos repository and encodes the old
  fold-into-Syrmos plan, so it is a candidate for removal in a later cleanup, not
  part of this rename.

Known gap handed to PHASE0-03 (issue #14): `tests/test_ktel.py` imports a
`generator` module that was never transplanted, so the full suite fails at
import. This predates and is unrelated to the rename, so the third acceptance
criterion (full suite green) is completed there.

## References

- [KTEL staging README](../../../server/ktel-staging/README.md)
- [Architecture](../../ARCHITECTURE.md)
- [Global independence posture](../../../README.md)
