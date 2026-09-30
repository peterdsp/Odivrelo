"""Backup and restore: a snapshot must come back as the identical release."""
from __future__ import annotations

import json
import shutil
import sqlite3
import tarfile

import pytest
from conftest import DAYTIME_DATE, ORAVO_EXTERNAL_ID, ORIGIN_TERMINAL_EXTERNAL_ID
from fastapi.testclient import TestClient

from publicapi import backup
from publicapi.app import create_app
from publicapi.brand import BRAND
from publicapi.config import load_settings


@pytest.fixture
def isolated_settings(seeded, tmp_path):
    """Settings pointing at a private copy of the release, safe to destroy."""
    public_db = tmp_path / "public.db"
    release_dir = tmp_path / "releases" / BRAND.slug
    release_dir.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(seeded["publicDb"], public_db)
    shutil.copytree(seeded["releaseDir"], release_dir)
    return load_settings(
        {
            f"{BRAND.env_prefix}_PUBLIC_DB_PATH": str(public_db),
            f"{BRAND.env_prefix}_RELEASE_DIR": str(release_dir),
            f"{BRAND.env_prefix}_DATA_MODE": "demo",
        }
    )


def test_a_snapshot_records_the_release_it_captured(isolated_settings, tmp_path, seeded):
    archive = tmp_path / "snapshot.tar.gz"
    snapshot = backup.create(isolated_settings, archive)
    assert archive.is_file()
    assert snapshot.release_id == seeded["releaseId"]
    assert snapshot.pack_count == len(seeded["manifest"])
    assert len(snapshot.database_sha256) == 64
    assert len(snapshot.manifest_sha256) == 64

    metadata = backup.describe(archive)
    assert metadata["releaseId"] == seeded["releaseId"]
    assert metadata["product"] == BRAND.name
    assert metadata["contractVersion"] == BRAND.contract_version
    assert metadata["snapshotFormat"] == backup.SNAPSHOT_FORMAT


def test_the_archive_holds_the_database_the_manifest_and_every_pack(
    isolated_settings, tmp_path
):
    archive = tmp_path / "snapshot.tar.gz"
    backup.create(isolated_settings, archive)
    with tarfile.open(archive, "r:gz") as handle:
        names = set(handle.getnames())
    manifest = json.loads(
        (isolated_settings.release_dir / "manifest.json").read_text(encoding="utf-8")
    )
    assert "snapshot.json" in names
    assert "public.db" in names
    assert "release/manifest.json" in names
    for entry in manifest["files"].values():
        assert f"release/{entry['path']}" in names


def test_restore_produces_an_identical_release_id(isolated_settings, tmp_path, seeded):
    archive = tmp_path / "snapshot.tar.gz"
    original = backup.create(isolated_settings, archive)

    # Destroy the live release completely: database, manifest and every pack.
    isolated_settings.public_db_path.unlink()
    shutil.rmtree(isolated_settings.release_dir)

    restored = backup.restore(isolated_settings, archive)
    assert restored["releaseId"] == original.release_id == seeded["releaseId"]

    connection = sqlite3.connect(
        f"file:{isolated_settings.public_db_path}?mode=ro", uri=True
    )
    connection.row_factory = sqlite3.Row
    try:
        row = connection.execute(
            "SELECT id FROM ktel_publication_releases WHERE status='published' "
            "ORDER BY published_at DESC, created_at DESC LIMIT 1"
        ).fetchone()
        assert row["id"] == seeded["releaseId"]
    finally:
        connection.close()

    manifest = json.loads(
        (isolated_settings.release_dir / "manifest.json").read_text(encoding="utf-8")
    )
    assert manifest["releaseId"] == seeded["releaseId"]
    assert manifest == json.loads(
        (seeded["releaseDir"] / "manifest.json").read_text(encoding="utf-8")
    )


def test_a_restored_release_serves_identical_responses(
    isolated_settings, tmp_path, seeded, client, ids
):
    archive = tmp_path / "snapshot.tar.gz"
    backup.create(isolated_settings, archive)
    isolated_settings.public_db_path.unlink()
    shutil.rmtree(isolated_settings.release_dir)
    backup.restore(isolated_settings, archive)

    app = create_app(
        environ={
            f"{BRAND.env_prefix}_PUBLIC_DB_PATH": str(isolated_settings.public_db_path),
            f"{BRAND.env_prefix}_RELEASE_DIR": str(isolated_settings.release_dir),
            f"{BRAND.env_prefix}_DATA_MODE": "demo",
        }
    )
    query = {
        "origin": ids[ORIGIN_TERMINAL_EXTERNAL_ID],
        "destination": ids[ORAVO_EXTERNAL_ID],
        "date": DAYTIME_DATE,
    }
    with TestClient(app, raise_server_exceptions=False) as restored_client:
        assert restored_client.get("/readyz").status_code == 200
        for path, params in (
            ("/v1/offline/manifest", None),
            ("/v1/places", None),
            ("/v1/operators", None),
            ("/v1/coverage", None),
            ("/v1/journeys", query),
        ):
            after = restored_client.get(path, params=params)
            before = client.get(path, params=params)
            assert after.status_code == 200, path
            # Byte-for-byte, which is only possible if the release is identical.
            assert after.content == before.content, path
            assert after.headers["ETag"] == before.headers["ETag"], path


def test_restore_is_refused_when_the_archived_database_is_tampered_with(
    isolated_settings, tmp_path
):
    archive = tmp_path / "snapshot.tar.gz"
    backup.create(isolated_settings, archive)

    # Rebuild the archive with a corrupt database but the original metadata.
    unpacked = tmp_path / "unpacked"
    with tarfile.open(archive, "r:gz") as handle:
        handle.extractall(unpacked, filter="data")
    (unpacked / "public.db").write_bytes(b"not a database")
    tampered = tmp_path / "tampered.tar.gz"
    with tarfile.open(tampered, "w:gz") as handle:
        for member in sorted(unpacked.rglob("*")):
            if member.is_file():
                handle.add(member, arcname=str(member.relative_to(unpacked)))

    with pytest.raises(backup.BackupError, match="does not match its recorded digest"):
        backup.restore(isolated_settings, tampered)
    # The live release is untouched by a refused restore.
    assert isolated_settings.public_db_path.is_file()


def test_restore_refuses_an_archive_member_that_escapes_the_root(
    isolated_settings, tmp_path
):
    archive = tmp_path / "escaping.tar.gz"
    # Valid metadata, so the archive gets past describe() and reaches the member
    # guard. That is the code under test here.
    metadata = tmp_path / "snapshot.json"
    metadata.write_text(
        json.dumps(
            {
                "snapshotFormat": backup.SNAPSHOT_FORMAT,
                "releaseId": "0000000000000000",
                "takenAt": "2026-09-20T06:00:00Z",
                "databaseSha256": "0" * 64,
                "manifestSha256": "0" * 64,
                "packCount": 0,
            }
        ),
        encoding="utf-8",
    )
    payload = tmp_path / "payload"
    payload.write_text("owned", encoding="utf-8")
    with tarfile.open(archive, "w:gz") as handle:
        handle.add(metadata, arcname="snapshot.json")
        handle.add(payload, arcname="../../escaped")
    with pytest.raises(backup.BackupError, match="escapes the archive root"):
        backup.restore(isolated_settings, archive)


def test_describing_something_that_is_not_a_snapshot_is_refused(tmp_path):
    archive = tmp_path / "random.tar.gz"
    payload = tmp_path / "payload"
    payload.write_text("nothing useful", encoding="utf-8")
    with tarfile.open(archive, "w:gz") as handle:
        handle.add(payload, arcname="payload")
    with pytest.raises(backup.BackupError, match="not a release snapshot"):
        backup.describe(archive)


def test_a_snapshot_is_refused_when_the_manifest_does_not_verify(
    isolated_settings, tmp_path
):
    manifest = json.loads(
        (isolated_settings.release_dir / "manifest.json").read_text(encoding="utf-8")
    )
    (isolated_settings.release_dir / manifest["files"]["stops"]["path"]).unlink()
    with pytest.raises(backup.BackupError, match="unverifiable release"):
        backup.create(isolated_settings, tmp_path / "snapshot.tar.gz")


def test_a_snapshot_is_refused_for_a_mixed_release(isolated_settings, tmp_path):
    manifest_path = isolated_settings.release_dir / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["releaseId"] = "0000000000000000"
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
    with pytest.raises(backup.BackupError, match="mixed release"):
        backup.create(isolated_settings, tmp_path / "snapshot.tar.gz")


def test_taking_two_snapshots_of_one_release_records_the_same_digests(
    isolated_settings, tmp_path
):
    first = backup.create(isolated_settings, tmp_path / "one.tar.gz")
    second = backup.create(isolated_settings, tmp_path / "two.tar.gz")
    assert first.release_id == second.release_id
    assert first.database_sha256 == second.database_sha256
    assert first.manifest_sha256 == second.manifest_sha256
