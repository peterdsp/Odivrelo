"""Connection-per-request, read-only access to the compiled public database.

There is no module-level connection and no cache of rows. Every request opens
its own read-only handle and closes it, which keeps the service free of shared
mutable state and makes an atomic release swap underneath a running process
safe: in-flight requests finish against the file they opened.

The ingestion database is never opened here. Only the administrative surface
touches it, through its own module.
"""
from __future__ import annotations

import sqlite3
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator

from . import _staging  # noqa: F401  (installs the staging import path)
from .errors import unavailable  # noqa: E402
from odivrelo_ktel import ktel_db  # noqa: E402


class PublicDatabaseError(RuntimeError):
    """The compiled public database is absent, unreadable or corrupt."""


@contextmanager
def read_only(path: Path | str) -> Iterator[sqlite3.Connection]:
    """Yield a read-only handle on the compiled public database."""
    target = Path(path)
    if not target.is_file():
        raise PublicDatabaseError(f"no compiled public database at {target}")
    try:
        connection = ktel_db.connect(str(target), read_only=True)
    except sqlite3.Error as error:
        raise PublicDatabaseError(
            f"the compiled public database at {target} could not be opened"
        ) from error
    try:
        yield connection
    except sqlite3.DatabaseError as error:
        raise PublicDatabaseError(
            f"the compiled public database at {target} is corrupt or incomplete"
        ) from error
    finally:
        connection.close()


@contextmanager
def public_connection(path: Path | str) -> Iterator[sqlite3.Connection]:
    """Same as :func:`read_only`, but reported as a contract 503."""
    try:
        with read_only(path) as connection:
            yield connection
    except PublicDatabaseError as error:
        raise unavailable(str(error)) from error


def integrity_ok(connection: sqlite3.Connection) -> bool:
    """Cheap structural check used by the readiness probe."""
    try:
        row = connection.execute("PRAGMA quick_check(1)").fetchone()
    except sqlite3.DatabaseError:
        return False
    return bool(row) and str(row[0]).lower() == "ok"
