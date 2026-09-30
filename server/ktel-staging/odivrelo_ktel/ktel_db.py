"""SQLite connection and migrations for the isolated national KTEL domain."""
from __future__ import annotations

import os
import sqlite3
from pathlib import Path

DEFAULT_KTEL_DB_PATH = (
    os.environ.get("ODIVRELO_KTEL_DB_PATH")
    or os.environ.get("PORAVIA_KTEL_DB_PATH")  # legacy fallback, former name
    or os.environ.get("HODOMAP_KTEL_DB_PATH")
    or os.environ.get("SYRMOS_KTEL_DB_PATH")  # legacy fallback, drop after Pi migration
    or str(Path(__file__).resolve().parent.parent / "data" / "ktel.db")
)
DEFAULT_KTEL_PUBLIC_DB_PATH = (
    os.environ.get("ODIVRELO_KTEL_PUBLIC_DB_PATH")
    or os.environ.get("PORAVIA_KTEL_PUBLIC_DB_PATH")  # legacy fallback, former name
    or os.environ.get("HODOMAP_KTEL_PUBLIC_DB_PATH")
    or os.environ.get("SYRMOS_KTEL_PUBLIC_DB_PATH")  # legacy fallback, drop after Pi migration
    or str(Path(__file__).resolve().parent.parent / "data" / "ktel-public.db")
)
KTEL_MIGRATIONS_DIR = Path(__file__).resolve().parent.parent / "ktel_migrations"


def connect(
    db_path: str = DEFAULT_KTEL_DB_PATH,
    *,
    read_only: bool = False,
) -> sqlite3.Connection:
    path = Path(db_path)
    if read_only:
        conn = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        conn = sqlite3.connect(str(path), isolation_level=None)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    conn.execute("PRAGMA busy_timeout = 5000")
    if not read_only:
        conn.execute("PRAGMA journal_mode = WAL")
        conn.execute("PRAGMA synchronous = NORMAL")
    return conn


def current_version(conn: sqlite3.Connection) -> int:
    row = conn.execute(
        "SELECT name FROM sqlite_master "
        "WHERE type='table' AND name='schema_version'"
    ).fetchone()
    if not row:
        return 0
    version = conn.execute(
        "SELECT MAX(version) AS value FROM schema_version"
    ).fetchone()
    return int(version["value"] or 0)


def migrate(conn: sqlite3.Connection) -> int:
    applied = current_version(conn)
    for path in sorted(KTEL_MIGRATIONS_DIR.glob("[0-9]*.sql")):
        version = int(path.stem.split("_", 1)[0])
        if version <= applied:
            continue
        conn.executescript(path.read_text(encoding="utf-8"))
        applied = current_version(conn)
        if applied != version:
            raise RuntimeError(
                f"KTEL migration {path.name} did not record version {version}"
            )
    return applied


if __name__ == "__main__":
    with connect() as connection:
        version = migrate(connection)
        print(f"KTEL DB ready at {DEFAULT_KTEL_DB_PATH}, version={version}")
