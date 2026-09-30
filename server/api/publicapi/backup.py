"""Snapshot and restore a published release.

A release is only restorable if the public database and the manifest that
describes it are captured together. Taking them separately can capture a
database from release B beside a manifest from release A, which is exactly the
mixed snapshot the release contract forbids, so both are always captured in one
archive and the release id is recorded inside it.

The database copy uses ``VACUUM INTO``, which produces a consistent page image
from a live reader without stopping traffic. Copying the file with ``cp`` while
a writer holds a WAL would capture a torn database.
"""
from __future__ import annotations

import hashlib
import json
import sqlite3
import tarfile
import tempfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from . import _staging  # noqa: F401  (installs the staging import path)
from .brand import BRAND  # noqa: E402
from .config import Settings  # noqa: E402
from .db import read_only  # noqa: E402
from hodomap_ktel import ktel_api  # noqa: E402
from hodomap_ktel.ktel_release import (  # noqa: E402
    ReleaseConsistencyError,
    verify_release,
)

SNAPSHOT_METADATA = "snapshot.json"
DATABASE_MEMBER = "public.db"
RELEASE_MEMBER = "release"
SNAPSHOT_FORMAT = 1


class BackupError(RuntimeError):
    """The snapshot could not be taken, read or trusted."""


@dataclass(frozen=True)
class Snapshot:
    path: Path
    release_id: str
    taken_at: str
    database_sha256: str
    manifest_sha256: str
    pack_count: int

    def payload(self) -> dict[str, Any]:
        return {
            "snapshotFormat": SNAPSHOT_FORMAT,
            "product": BRAND.name,
            "contractVersion": BRAND.contract_version,
            "releaseId": self.release_id,
            "takenAt": self.taken_at,
            "databaseSha256": self.database_sha256,
            "manifestSha256": self.manifest_sha256,
            "packCount": self.pack_count,
        }


def _digest(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def _now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def _consistent_copy(source: Path, target: Path) -> None:
    """Copy a live SQLite database as one consistent page image."""
    if not source.is_file():
        raise BackupError(f"no public database at {source}")
    connection = sqlite3.connect(f"file:{source}?mode=ro", uri=True)
    try:
        connection.execute("VACUUM INTO ?", (str(target),))
    except sqlite3.Error as error:
        raise BackupError(f"the public database at {source} could not be copied: {error}") from error
    finally:
        connection.close()


def create(settings: Settings, destination: Path | str) -> Snapshot:
    """Write one archive holding the public database, the manifest and the packs."""
    try:
        manifest = verify_release(settings.release_dir)
    except ReleaseConsistencyError as error:
        raise BackupError(
            f"refusing to snapshot an unverifiable release: {error}"
        ) from error

    with read_only(settings.public_db_path) as connection:
        release_id = ktel_api.release_id(connection)
    if not release_id:
        raise BackupError("the public database carries no published release")
    if manifest.get("releaseId") != release_id:
        raise BackupError(
            "refusing to snapshot a mixed release: the database carries "
            f"{release_id} and the manifest describes {manifest.get('releaseId')}"
        )

    destination = Path(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory() as workspace:
        staged_db = Path(workspace) / DATABASE_MEMBER
        _consistent_copy(settings.public_db_path, staged_db)
        snapshot = Snapshot(
            path=destination,
            release_id=release_id,
            taken_at=_now(),
            database_sha256=_digest(staged_db),
            manifest_sha256=_digest(settings.manifest_path),
            pack_count=len(manifest.get("files", {})),
        )
        metadata = Path(workspace) / SNAPSHOT_METADATA
        metadata.write_text(
            json.dumps(snapshot.payload(), ensure_ascii=False, indent=2, sort_keys=True)
            + "\n",
            encoding="utf-8",
        )
        with tarfile.open(destination, "w:gz") as archive:
            archive.add(metadata, arcname=SNAPSHOT_METADATA)
            archive.add(staged_db, arcname=DATABASE_MEMBER)
            archive.add(
                settings.manifest_path, arcname=f"{RELEASE_MEMBER}/manifest.json"
            )
            previous = settings.release_dir / "manifest-previous.json"
            if previous.is_file():
                archive.add(
                    previous, arcname=f"{RELEASE_MEMBER}/manifest-previous.json"
                )
            for entry in sorted(
                manifest["files"].values(), key=lambda item: item["path"]
            ):
                archive.add(
                    settings.release_dir / entry["path"],
                    arcname=f"{RELEASE_MEMBER}/{entry['path']}",
                )
    return snapshot


def describe(archive_path: Path | str) -> dict[str, Any]:
    """Read a snapshot's metadata without unpacking it."""
    try:
        with tarfile.open(archive_path, "r:gz") as archive:
            member = archive.extractfile(SNAPSHOT_METADATA)
            if member is None:
                raise KeyError(SNAPSHOT_METADATA)
            payload = json.loads(member.read().decode("utf-8"))
    except (KeyError, tarfile.TarError, UnicodeDecodeError, ValueError) as error:
        raise BackupError(
            f"{archive_path} is not a release snapshot: {error}"
        ) from error
    if not isinstance(payload, dict):
        raise BackupError(f"{archive_path} is not a release snapshot")
    if payload.get("snapshotFormat") != SNAPSHOT_FORMAT:
        raise BackupError(
            f"{archive_path} uses snapshot format {payload.get('snapshotFormat')}, "
            f"this build reads {SNAPSHOT_FORMAT}"
        )
    return payload


def _safe_members(archive: tarfile.TarFile) -> list[tarfile.TarInfo]:
    """Refuse absolute paths, traversal and anything that is not a plain file."""
    members: list[tarfile.TarInfo] = []
    for member in archive.getmembers():
        name = member.name
        if name.startswith("/") or ".." in Path(name).parts:
            raise BackupError(f"snapshot member {name!r} escapes the archive root")
        if not (member.isfile() or member.isdir()):
            raise BackupError(f"snapshot member {name!r} is not a file or directory")
        members.append(member)
    return members


def restore(settings: Settings, archive_path: Path | str) -> dict[str, Any]:
    """Restore a snapshot in place and prove the release id came back identical."""
    metadata = describe(archive_path)
    expected = metadata["releaseId"]

    with tempfile.TemporaryDirectory() as workspace:
        root = Path(workspace)
        with tarfile.open(archive_path, "r:gz") as archive:
            archive.extractall(
                root, members=_safe_members(archive), filter="data"
            )

        staged_db = root / DATABASE_MEMBER
        staged_release = root / RELEASE_MEMBER
        if not staged_db.is_file() or not (staged_release / "manifest.json").is_file():
            raise BackupError(f"{archive_path} is missing its database or manifest")
        if _digest(staged_db) != metadata["databaseSha256"]:
            raise BackupError("the archived database does not match its recorded digest")

        # Verify before touching anything live, so a corrupt archive cannot
        # replace a working release.
        verify_release(staged_release)
        with read_only(staged_db) as connection:
            staged_release_id = ktel_api.release_id(connection)
        if staged_release_id != expected:
            raise BackupError(
                f"the archived database carries {staged_release_id} but the "
                f"snapshot records {expected}"
            )

        settings.release_dir.mkdir(parents=True, exist_ok=True)
        settings.packs_dir.mkdir(parents=True, exist_ok=True)
        settings.public_db_path.parent.mkdir(parents=True, exist_ok=True)

        for source in sorted(staged_release.rglob("*")):
            if not source.is_file():
                continue
            target = settings.release_dir / source.relative_to(staged_release)
            target.parent.mkdir(parents=True, exist_ok=True)
            holding = target.with_name(target.name + ".restore")
            holding.write_bytes(source.read_bytes())
            holding.replace(target)

        holding = settings.public_db_path.with_name(
            settings.public_db_path.name + ".restore"
        )
        holding.write_bytes(staged_db.read_bytes())
        holding.replace(settings.public_db_path)
        # A restored file image must not be paired with the previous WAL.
        for suffix in ("-wal", "-shm"):
            sidecar = Path(f"{settings.public_db_path}{suffix}")
            if sidecar.exists():
                sidecar.unlink()

    restored_manifest = verify_release(settings.release_dir)
    with read_only(settings.public_db_path) as connection:
        restored_release_id = ktel_api.release_id(connection)
    if restored_release_id != expected or restored_manifest.get("releaseId") != expected:
        raise BackupError(
            "restore did not reproduce the snapshot release: expected "
            f"{expected}, database carries {restored_release_id}, manifest "
            f"describes {restored_manifest.get('releaseId')}"
        )
    return {
        "releaseId": expected,
        "takenAt": metadata["takenAt"],
        "packCount": restored_manifest and len(restored_manifest.get("files", {})),
        "publicDbPath": str(settings.public_db_path),
        "releaseDir": str(settings.release_dir),
    }


def main(argv: list[str] | None = None) -> int:
    import argparse

    from .config import load_settings

    parser = argparse.ArgumentParser(description="Snapshot or restore a release.")
    subcommands = parser.add_subparsers(dest="command", required=True)
    create_parser = subcommands.add_parser("create", help="write a snapshot archive")
    create_parser.add_argument("archive")
    restore_parser = subcommands.add_parser("restore", help="restore a snapshot archive")
    restore_parser.add_argument("archive")
    describe_parser = subcommands.add_parser("describe", help="print snapshot metadata")
    describe_parser.add_argument("archive")

    arguments = parser.parse_args(argv)
    if arguments.command == "describe":
        print(json.dumps(describe(arguments.archive), ensure_ascii=False, indent=2))
        return 0

    settings = load_settings()
    if arguments.command == "create":
        snapshot = create(settings, arguments.archive)
        print(
            json.dumps(
                {"archive": str(snapshot.path), **snapshot.payload()},
                ensure_ascii=False,
                indent=2,
                sort_keys=True,
            )
        )
        return 0

    result = restore(settings, arguments.archive)
    print(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":  # pragma: no cover - CLI entry point
    raise SystemExit(main())
