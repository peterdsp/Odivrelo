# KTEL staging (transplanted from the Syrmos project)

These files were originally built in the separate Syrmos rail project tree by
mistake; they belong to HodoMap (the intercity coach app). The Python package
was renamed from `syrmos_admin` to `hodomap_ktel` in PHASE0-01 (issue #12), so no
Syrmos identity remains in HodoMap code. Behaviour is unchanged.

- `hodomap_ktel/ktel_*.py`: the 6 self-contained KTEL modules. They import each
  other with relative imports (`from . import ktel_db`, `from .ktel_registry`),
  so they work as a package regardless of where it is mounted.
- `ktel_migrations/`, `data/`: `ktel_db.py` resolves these relative to the
  package parent (`__file__.parent.parent`), that is this `ktel-staging/` dir.
  The DB paths are env-overridable. `HODOMAP_KTEL_DB_PATH` and
  `HODOMAP_KTEL_PUBLIC_DB_PATH` are the current names; the legacy
  `SYRMOS_KTEL_DB_PATH` and `SYRMOS_KTEL_PUBLIC_DB_PATH` are still read as a
  fallback so an existing deployment keeps working until it migrates.
- `pkg/ktel/operators.json`: operator seed data.
- `scripts/ktel_pipeline.py`: the CLI. Uses absolute `from hodomap_ktel.ktel_*`,
  so run it with this dir on `PYTHONPATH`. Runtime dep: `openpyxl`.
- `tests/test_ktel.py`, `ktel.env.example`.
- Design docs are in `../../docs/KTEL_NATIONAL_PLATFORM.md` and
  `../../docs/KTEL_NATIONAL_EXECUTION_PLAN.md`.

## Known gap (PHASE0-03, issue #14)

`tests/test_ktel.py` imports a `generator` module that was never transplanted
(only the 6 `ktel_*` modules were copied), so the test suite currently fails at
import. PHASE0-03 must either port a replacement or drop the obsolete Syrmos
snapshot path in favour of GTFS export, then get the suite green. Until then,
verify the package with a direct functional check rather than the full suite.

## Note on the Syrmos originals

The originals remain in the separate Syrmos project tree on purpose: that
project's own server still references its copy, so deleting them there would
break it. That is a concern for the Syrmos project, not for this repository.
