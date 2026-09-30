"""Shared fixtures: one seeded demonstration release per test session.

Seeding runs the real pipeline (import, review, compile, GTFS, release), so
every test in this suite asserts against an artifact produced exactly the way
production produces one. It is session scoped because it is deterministic:
generating twice from the same fixture yields the same release id.
"""
from __future__ import annotations

import shutil
import sys
from pathlib import Path

import pytest

# ``server/api`` on the path, so ``publicapi`` imports without an install step.
API_ROOT = Path(__file__).resolve().parents[1]
if str(API_ROOT) not in sys.path:
    sys.path.insert(0, str(API_ROOT))

from publicapi import demo_seed  # noqa: E402
from publicapi.brand import BRAND  # noqa: E402
from publicapi.config import load_settings  # noqa: E402

#: Places and journeys the assertions refer to by name rather than by opaque id.
ORIGIN_TERMINAL_EXTERNAL_ID = "place-aloria-central"
BAY_A1_EXTERNAL_ID = "stop-aloria-bay-a1"
BAY_A2_EXTERNAL_ID = "stop-aloria-bay-a2"
ORAVO_EXTERNAL_ID = "stop-oravo-junction"
MISTONA_EXTERNAL_ID = "stop-mistona-village"

DAYTIME_DATE = "2026-10-02"
SPRING_FORWARD_DATE = "2026-03-29"
AUTUMN_BACK_DATE = "2026-10-25"
CALENDAR_REMOVED_DATE = "2026-04-06"
CALENDAR_ADDED_DATE = "2026-04-11"
CALENDAR_NORMAL_DATE = "2026-04-08"

ADMIN_TOKEN = "test-admin-token-that-is-long-enough-1234567890"


@pytest.fixture(scope="session")
def seeded(tmp_path_factory) -> dict:
    """Seed the demonstration release once and describe where everything landed."""
    root = tmp_path_factory.mktemp("release")
    ingest_db = root / "ingest.db"
    public_db = root / "public.db"
    out_dir = root / "artifacts"
    result = demo_seed.seed(
        ingest_db_path=ingest_db,
        public_db_path=public_db,
        release_out_dir=out_dir,
        reviewer="pytest",
    )
    return {
        "root": root,
        "ingestDb": ingest_db,
        "publicDb": public_db,
        "outDir": out_dir,
        "releaseDir": Path(result["releaseDir"]),
        "releaseId": result["release"]["releaseId"],
        "manifest": result["release"]["files"],
        "published": result["published"],
        "imported": result["imported"],
    }


def base_environ(seeded: dict, **overrides: str) -> dict[str, str]:
    environment = {
        f"{BRAND.env_prefix}_PUBLIC_DB_PATH": str(seeded["publicDb"]),
        f"{BRAND.env_prefix}_RELEASE_DIR": str(seeded["releaseDir"]),
        f"{BRAND.env_prefix}_DATA_MODE": "demo",
    }
    environment.update(overrides)
    return environment


@pytest.fixture
def settings(seeded: dict):
    return load_settings(base_environ(seeded))


@pytest.fixture
def client(seeded: dict):
    """A public-only client: the admin router is not mounted at all."""
    from fastapi.testclient import TestClient

    from publicapi.app import create_app

    app = create_app(environ=base_environ(seeded))
    with TestClient(app, raise_server_exceptions=False) as test_client:
        yield test_client


@pytest.fixture
def admin_client(seeded: dict, tmp_path: Path):
    """An admin-enabled client working on a private copy of the databases.

    Administrative calls mutate the ingestion database and cut releases, so they
    must never run against the session-scoped artifact the read tests share.
    """
    from fastapi.testclient import TestClient

    from publicapi.app import create_app

    work = tmp_path / "admin"
    work.mkdir()
    ingest = work / "ingest.db"
    public = work / "public.db"
    release_dir = work / "releases" / BRAND.slug
    release_dir.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(seeded["ingestDb"], ingest)
    shutil.copy2(seeded["publicDb"], public)
    shutil.copytree(seeded["releaseDir"], release_dir)

    environment = {
        f"{BRAND.env_prefix}_PUBLIC_DB_PATH": str(public),
        f"{BRAND.env_prefix}_RELEASE_DIR": str(release_dir),
        f"{BRAND.env_prefix}_DATA_MODE": "demo",
        f"{BRAND.env_prefix}_ADMIN_ENABLED": "true",
        f"{BRAND.env_prefix}_INGEST_DB_PATH": str(ingest),
        f"{BRAND.env_prefix}_ADMIN_DB_PATH": str(work / "admin.db"),
        "ADMIN_API_TOKEN": ADMIN_TOKEN,
    }
    app = create_app(environ=environment)
    with TestClient(app, raise_server_exceptions=False) as test_client:
        test_client.paths = {  # type: ignore[attr-defined]
            "ingest": ingest,
            "public": public,
            "releaseDir": release_dir,
            "outDir": release_dir.parent,
        }
        yield test_client


class _LogStream:
    """Capture the service's own JSON log lines.

    The ``publicapi`` logger deliberately does not propagate to the root logger,
    so ``caplog`` sees nothing. Reading the real stream is also the more faithful
    test: it exercises the JSON formatter and its size caps, not just the record.
    """

    def __init__(self) -> None:
        import io

        self.buffer = io.StringIO()

    def text(self) -> str:
        return self.buffer.getvalue()

    def lines(self) -> list[str]:
        return [line for line in self.text().splitlines() if line.strip()]


@pytest.fixture
def log_stream():
    """Route service logging into a buffer for the duration of one test."""
    from publicapi.logging_setup import configure_logging

    stream = _LogStream()
    configure_logging("INFO", stream=stream.buffer)
    try:
        yield stream
    finally:
        configure_logging("INFO")


@pytest.fixture(scope="session")
def ids(seeded: dict) -> dict[str, str]:
    """Map the fixture's external ids onto the opaque ids the API serves."""
    import sqlite3

    connection = sqlite3.connect(f"file:{seeded['publicDb']}?mode=ro", uri=True)
    connection.row_factory = sqlite3.Row
    try:
        mapping = {
            row["external_id"]: row["id"]
            for row in connection.execute(
                "SELECT external_id, id FROM ktel_stop_places"
            )
        }
        mapping.update(
            {
                row["external_id"]: row["id"]
                for row in connection.execute("SELECT external_id, id FROM ktel_stops")
            }
        )
        mapping.update(
            {
                row["external_id"]: row["id"]
                for row in connection.execute("SELECT external_id, id FROM ktel_trips")
            }
        )
    finally:
        connection.close()
    return mapping
