"""Cut a production release from an already reviewed real dataset.

This is the counterpart to :mod:`publicapi.demo_seed`. The demo seeder imports
the invented Aloria fixture; this module imports nothing. It consumes an
ingestion database that the acquisition and review pipeline has already filled
with real, published rows, compiles it, and cuts a release stamped
``dataMode="real"``.

It never touches the demonstration fixture and never falls back to it. If the
ingestion database is missing, holds no published journeys, or still carries the
demonstration operator, it raises and exits non-zero. The release is then
re-read through :mod:`publicapi.release_guard`, so a release that is not genuinely
real cannot leave this entry point.
"""
from __future__ import annotations

import json
import sqlite3
from pathlib import Path
from typing import Any

from . import _staging  # noqa: F401  (installs the staging import path)
from . import packs, release_guard  # noqa: E402
from .brand import BRAND  # noqa: E402
from odivrelo_ktel import ktel_db, ktel_release  # noqa: E402
from .release_guard import ReleaseGuardError  # noqa: E402

DEMO_OPERATOR_ID = "demo-aloria-coach"


def _published_counts(ingest_db_path: Path) -> dict[str, int]:
    connection = ktel_db.connect(str(ingest_db_path), read_only=True)
    try:
        trips = connection.execute(
            "SELECT COUNT(*) FROM ktel_trips WHERE publication_state='published'"
        ).fetchone()[0]
        demo_published = connection.execute(
            "SELECT COUNT(*) FROM ktel_trips "
            "WHERE publication_state='published' AND operator_id=?",
            (DEMO_OPERATOR_ID,),
        ).fetchone()[0]
        return {"publishedTrips": int(trips), "demoPublishedTrips": int(demo_published)}
    finally:
        connection.close()


def cut_release(
    *,
    ingest_db_path: Path | str,
    public_db_path: Path | str,
    release_out_dir: Path | str,
    commit: str = "unknown",
    built_at: str = "unknown",
) -> dict[str, Any]:
    """Compile the reviewed dataset and cut a real release, then guard it."""
    ingest_db_path = Path(ingest_db_path)
    if not ingest_db_path.exists():
        raise ReleaseGuardError(
            f"no reviewed dataset at {ingest_db_path}; the production path never "
            "falls back to the demonstration seeder, so it stops here"
        )

    counts = _published_counts(ingest_db_path)
    if counts["publishedTrips"] == 0:
        raise ReleaseGuardError(
            "the reviewed dataset has no published trips; refusing to cut a "
            "production release from an empty or failed import"
        )
    if counts["demoPublishedTrips"] > 0:
        raise ReleaseGuardError(
            "the reviewed dataset still publishes the demonstration operator "
            f"{DEMO_OPERATOR_ID!r}; refusing to ship it as real data"
        )

    release = ktel_release.generate_public_release(
        release_out_dir,
        str(ingest_db_path),
        str(public_db_path),
        payload_provider=packs.payload_provider(
            data_mode="real", commit=commit, built_at=built_at
        ),
    )

    release_dir = Path(release_out_dir) / BRAND.slug
    # Fail closed: re-read the release we just wrote and prove it is really real.
    release_guard.assert_channel(release_dir, "real")

    return {
        "release": release,
        "releaseDir": str(release_dir),
        "publishedTrips": counts["publishedTrips"],
    }


def main(argv: list[str] | None = None) -> int:
    import argparse

    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--ingest-db",
        required=True,
        help="path to the reviewed real ingestion database (never the fixture)",
    )
    parser.add_argument("--public-db", required=True)
    parser.add_argument("--out-dir", required=True)
    parser.add_argument("--commit", default="unknown")
    parser.add_argument("--built-at", default="unknown")
    arguments = parser.parse_args(argv)
    try:
        result = cut_release(
            ingest_db_path=arguments.ingest_db,
            public_db_path=arguments.public_db,
            release_out_dir=arguments.out_dir,
            commit=arguments.commit,
            built_at=arguments.built_at,
        )
    except (ReleaseGuardError, sqlite3.Error) as error:
        print(f"production release FAILED: {error}")
        return 1
    print(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":  # pragma: no cover - CLI entry point
    raise SystemExit(main())
