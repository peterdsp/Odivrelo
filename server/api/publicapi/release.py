"""Release manifest, pack access and readiness, on top of the staging generator.

Integrity checking is delegated to ``poravia_ktel.ktel_release.verify_release``,
which is the code that wrote the manifest in the first place. Nothing here
re-implements digesting or release generation.
"""
from __future__ import annotations

import json
import re
import sqlite3
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from . import _staging  # noqa: F401  (installs the staging import path)
from .config import Settings  # noqa: E402
from .db import PublicDatabaseError, integrity_ok, read_only  # noqa: E402
from .errors import not_found, release_mismatch, unavailable  # noqa: E402
from poravia_ktel import ktel_api  # noqa: E402
from poravia_ktel.ktel_registry import coordinate_status  # noqa: E402
from poravia_ktel.ktel_release import (  # noqa: E402
    ReleaseConsistencyError,
    verify_release,
)

#: Content-addressed pack names are generated, never user supplied. Anything
#: that does not match this shape cannot be a pack, so it is a 404 before the
#: filesystem is touched at all.
PACK_NAME = re.compile(r"^[a-z0-9][a-z0-9.\-]{0,95}\.(json|zip)$")

MEDIA_TYPES = {"json": "application/json", "zip": "application/zip"}


@dataclass(frozen=True)
class ReadinessReport:
    ready: bool
    release_id: str | None
    checks: dict[str, str]
    problems: tuple[str, ...]

    def payload(self) -> dict[str, Any]:
        return {
            "status": "ready" if self.ready else "not_ready",
            "releaseId": self.release_id,
            "checks": self.checks,
            "problems": list(self.problems),
        }


def load_manifest(settings: Settings) -> dict[str, Any]:
    path = settings.manifest_path
    if not path.is_file():
        raise unavailable(f"no release manifest at {path}")
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise unavailable("the release manifest could not be read") from error
    if not isinstance(payload, dict) or "files" not in payload:
        raise unavailable("the release manifest is not a v1 manifest")
    return payload


def manifest_for_release(settings: Settings, release_id: str | None) -> dict[str, Any]:
    """Load the manifest and refuse to serve it if it disagrees with the DB."""
    manifest = load_manifest(settings)
    if release_id and manifest.get("releaseId") != release_id:
        raise release_mismatch(
            "the published database and the release manifest describe different "
            f"releases ({release_id} and {manifest.get('releaseId')}); no mixed "
            "snapshot is served"
        )
    return manifest


def pack_entry(manifest: dict[str, Any], filename: str) -> dict[str, Any]:
    """Find the manifest entry whose path ends in ``filename``."""
    if not PACK_NAME.match(filename):
        raise not_found(f"no pack named {filename!r} in this release", "filename")
    for entry in manifest.get("files", {}).values():
        if not isinstance(entry, dict):
            continue
        path = str(entry.get("path", ""))
        if path.rsplit("/", 1)[-1] == filename:
            return entry
    raise not_found(f"no pack named {filename!r} in this release", "filename")


def read_pack(settings: Settings, entry: dict[str, Any]) -> bytes:
    """Read a pack and prove its bytes against the manifest before serving."""
    relative = str(entry.get("path", ""))
    target = (settings.release_dir / relative).resolve()
    if settings.release_dir.resolve() not in target.parents:
        raise not_found("no such pack in this release", "filename")
    if not target.is_file():
        raise unavailable(f"pack {relative} is listed in the manifest but missing")
    body = target.read_bytes()
    if len(body) != int(entry.get("bytes", -1)):
        raise unavailable(f"pack {relative} does not match its manifest size")
    return body


def media_type(entry: dict[str, Any]) -> str:
    stored = entry.get("mediaType")
    if isinstance(stored, str) and stored:
        return stored
    suffix = str(entry.get("path", "")).rsplit(".", 1)[-1]
    return MEDIA_TYPES.get(suffix, "application/octet-stream")


def etag(entry: dict[str, Any]) -> str:
    return f'"{entry["sha256"]}"'


def etag_matches(header: str | None, tag: str) -> bool:
    """RFC 9110 ``If-None-Match`` comparison, weak-tag tolerant."""
    if not header:
        return False
    if header.strip() == "*":
        return True
    bare = tag.strip('"')
    for candidate in header.split(","):
        candidate = candidate.strip()
        if candidate.startswith("W/"):
            candidate = candidate[2:]
        if candidate.strip('"') == bare:
            return True
    return False


def published_release_id(connection: sqlite3.Connection) -> str | None:
    return ktel_api.release_id(connection)


def demo_rows_present(connection: sqlite3.Connection) -> bool:
    """True when a published stop would fail the real-data coordinate gate.

    This is the guard that stops an invented dataset being served as real. It
    calls the same registry function the ingestion gate uses, with the default
    ``dataset='real'``, so the two can never drift apart.
    """
    rows = connection.execute(
        "SELECT latitude, longitude FROM ktel_stops WHERE publication_state='published'"
    ).fetchall()
    return any(
        coordinate_status(row["latitude"], row["longitude"]) not in {"valid", "missing"}
        for row in rows
    )


def readiness(settings: Settings) -> ReadinessReport:
    """Everything ``/readyz`` promises, as a single report."""
    checks: dict[str, str] = {}
    problems: list[str] = []
    release_id: str | None = None

    try:
        with read_only(settings.public_db_path) as connection:
            checks["publicDatabase"] = "ok"
            if not integrity_ok(connection):
                checks["integrity"] = "failed"
                problems.append("the compiled public database failed its integrity check")
            else:
                checks["integrity"] = "ok"
                release_id = published_release_id(connection)
                if not release_id:
                    checks["publishedRelease"] = "missing"
                    problems.append("the compiled public database carries no published release")
                else:
                    checks["publishedRelease"] = "ok"
                if settings.data_mode == "real" and demo_rows_present(connection):
                    checks["dataMode"] = "failed"
                    problems.append(
                        "the release contains stops outside the real-data coordinate "
                        "gate but the service is configured to serve real data"
                    )
                else:
                    checks["dataMode"] = "ok"
    except PublicDatabaseError as error:
        checks["publicDatabase"] = "failed"
        problems.append(str(error))
    except sqlite3.DatabaseError as error:
        checks["publicDatabase"] = "failed"
        problems.append(f"the compiled public database is unreadable: {error}")

    try:
        manifest = verify_release(settings.release_dir)
        checks["manifest"] = "ok"
        if release_id and manifest.get("releaseId") != release_id:
            checks["releaseAgreement"] = "failed"
            problems.append(
                "the release manifest describes "
                f"{manifest.get('releaseId')} but the published database carries "
                f"{release_id}"
            )
        elif release_id:
            checks["releaseAgreement"] = "ok"
    except ReleaseConsistencyError as error:
        checks["manifest"] = "failed"
        problems.append(str(error))
    except (OSError, json.JSONDecodeError, KeyError, TypeError) as error:
        checks["manifest"] = "failed"
        problems.append(f"the release manifest could not be verified: {error}")

    return ReadinessReport(
        ready=not problems,
        release_id=release_id,
        checks=checks,
        problems=tuple(problems),
    )


def previous_database_path(settings: Settings) -> Path:
    """Where :func:`compile_public_database` parks the superseded artifact."""
    target = settings.public_db_path
    return target.with_name(f"{target.stem}-previous{target.suffix}")


def restore_previous_database(settings: Settings, release_id: str | None) -> bool:
    """Swap the superseded public database back in, if it is the rolled-back one.

    Rolling back only the manifest would leave the API serving rows from the
    newer release behind an older manifest, which is precisely the mixed
    snapshot the release contract forbids. The swap is two-way, so a rollback
    can itself be rolled forward.
    """
    if not release_id:
        return False
    previous = previous_database_path(settings)
    if not previous.is_file():
        return False
    try:
        with read_only(previous) as connection:
            if published_release_id(connection) != release_id:
                return False
    except (PublicDatabaseError, sqlite3.DatabaseError):
        return False
    current = settings.public_db_path
    holding = current.with_name(current.name + ".swap")
    if current.is_file():
        current.replace(holding)
    previous.replace(current)
    if holding.is_file():
        holding.replace(previous)
    return True


def snapshot_paths(settings: Settings) -> list[Path]:
    """Files a backup must capture for a restorable release."""
    return [settings.public_db_path, settings.manifest_path, settings.packs_dir]
