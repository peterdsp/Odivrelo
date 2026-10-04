"""Load the labelled Aloria demonstration dataset and cut a release from it.

The normalized snapshot importer handles transport entities, service calendars,
exceptions and trip bindings in one transaction. This module only selects the
invented fixture and reviews its demonstration rows.

Compilation, GTFS export and release generation are not reimplemented: they are
``ktel_publish`` and ``ktel_release`` called as they stand. The packs those
produce carry the published contract shape, because :mod:`publicapi.packs` is
passed in as the payload provider.
"""
from __future__ import annotations

import json
import sqlite3
from pathlib import Path
from typing import Any, Iterable

from . import _staging  # noqa: F401  (installs the staging import path)
from . import packs  # noqa: E402
from .brand import BRAND  # noqa: E402
from odivrelo_ktel import ktel_db, ktel_release  # noqa: E402
from odivrelo_ktel.ktel_ingest import import_normalized_snapshot, review_entity  # noqa: E402
from odivrelo_ktel.ktel_registry import seed_registry  # noqa: E402

#: ``server/api/publicapi/demo_seed.py`` -> ... -> repo root.
REPO_ROOT = Path(__file__).resolve().parents[3]
FIXTURES_DIR = REPO_ROOT / "data" / "fixtures"
SNAPSHOT_FILE = FIXTURES_DIR / "aloria-demo-snapshot.json"
RIGHTS_PENDING_FILE = FIXTURES_DIR / "aloria-demo-rights-pending.json"

DEMO_OPERATOR_ID = "demo-aloria-coach"

#: Published in this order so a reviewer never publishes a child before its
#: parent, which would leave a dangling reference in the compiled artifact.
PUBLISH_ORDER = (
    ("stop_place", "ktel_stop_places"),
    ("stop", "ktel_stops"),
    ("line", "ktel_lines"),
    ("journey_pattern", "ktel_journey_patterns"),
    ("service_calendar", "ktel_service_calendars"),
    ("trip", "ktel_trips"),
)


def load_fixture(path: Path) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if payload.get("dataset") != "demo":
        raise ValueError(
            f"{path} is not flagged as a demonstration dataset; refusing to load it "
            "through the demo seeder"
        )
    return payload


def publish_reviewed(
    connection: sqlite3.Connection,
    *,
    reviewer: str,
    leave_unreviewed: Iterable[str] = (),
) -> dict[str, int]:
    """Publish every candidate entity except the ones held back on purpose."""
    held = set(leave_unreviewed)
    counts: dict[str, int] = {}
    for kind, table in PUBLISH_ORDER:
        published = 0
        has_external = table != "ktel_service_calendars"
        column = "external_id" if has_external else "NULL AS external_id"
        for row in connection.execute(
            f"SELECT id, {column} FROM {table} WHERE publication_state='candidate' "
            "ORDER BY id"
        ).fetchall():
            if row["external_id"] in held:
                continue
            review_entity(
                connection,
                entity_kind=kind,
                entity_id=row["id"],
                action="publish",
                reviewer=reviewer,
                reason="Reviewed demonstration dataset for the Aloria region.",
            )
            published += 1
        counts[kind] = published
    return counts


def seed(
    *,
    ingest_db_path: Path | str,
    public_db_path: Path | str,
    release_out_dir: Path | str,
    reviewer: str = "demo-seed",
    snapshot_file: Path = SNAPSHOT_FILE,
    rights_pending_file: Path | None = RIGHTS_PENDING_FILE,
    data_mode: str = "demo",
    commit: str = "unknown",
    built_at: str = "unknown",
) -> dict[str, Any]:
    """Build the ingestion database, review it, compile it and cut a release."""
    if data_mode != "demo":
        raise ValueError("The demonstration seeder cannot publish real transport data")
    snapshot = load_fixture(snapshot_file)
    imported: dict[str, Any] = {}

    connection = ktel_db.connect(str(ingest_db_path))
    try:
        ktel_db.migrate(connection)
        seed_registry(connection)
        imported["snapshot"] = import_normalized_snapshot(connection, snapshot)
        if rights_pending_file is not None:
            pending = load_fixture(rights_pending_file)
            imported["rightsPending"] = import_normalized_snapshot(connection, pending)
        published = publish_reviewed(
            connection,
            reviewer=reviewer,
            leave_unreviewed=snapshot.get("leaveUnreviewed", []),
        )
        connection.commit()
    finally:
        connection.close()

    # One generator for every client: the packs carry the published contract
    # shape, produced by the same functions the API serves from.
    release = ktel_release.generate_public_release(
        release_out_dir,
        str(ingest_db_path),
        str(public_db_path),
        payload_provider=packs.payload_provider(
            data_mode=data_mode, commit=commit, built_at=built_at
        ),
    )
    return {
        "imported": imported,
        "published": published,
        "release": release,
        "releaseDir": str(Path(release_out_dir) / BRAND.slug),
    }


def main(argv: list[str] | None = None) -> int:
    import argparse

    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--ingest-db", required=True)
    parser.add_argument("--public-db", required=True)
    parser.add_argument("--out-dir", required=True)
    parser.add_argument("--reviewer", default="demo-seed")
    parser.add_argument("--data-mode", default="demo", choices=("demo",))
    parser.add_argument("--commit", default="unknown")
    parser.add_argument("--built-at", default="unknown")
    arguments = parser.parse_args(argv)
    result = seed(
        ingest_db_path=arguments.ingest_db,
        public_db_path=arguments.public_db,
        release_out_dir=arguments.out_dir,
        reviewer=arguments.reviewer,
        data_mode=arguments.data_mode,
        commit=arguments.commit,
        built_at=arguments.built_at,
    )
    print(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":  # pragma: no cover - CLI entry point
    raise SystemExit(main())
