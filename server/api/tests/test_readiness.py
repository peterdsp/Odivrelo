"""Readiness: the probe must fail for every way a release can be wrong."""
from __future__ import annotations

import json
import shutil

import pytest
from conftest import base_environ
from fastapi.testclient import TestClient

from publicapi.app import create_app
from publicapi.brand import BRAND


@pytest.fixture
def isolated(seeded, tmp_path):
    """A private copy of the release, safe to break."""
    public_db = tmp_path / "public.db"
    release_dir = tmp_path / "releases" / BRAND.slug
    release_dir.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(seeded["publicDb"], public_db)
    shutil.copytree(seeded["releaseDir"], release_dir)
    return {"publicDb": public_db, "releaseDir": release_dir}


def probe(isolated: dict, **overrides):
    environment = {
        f"{BRAND.env_prefix}_PUBLIC_DB_PATH": str(isolated["publicDb"]),
        f"{BRAND.env_prefix}_RELEASE_DIR": str(isolated["releaseDir"]),
        f"{BRAND.env_prefix}_DATA_MODE": "demo",
    }
    environment.update(overrides)
    app = create_app(environ=environment)
    with TestClient(app, raise_server_exceptions=False) as client:
        return client.get("/readyz")


def test_a_healthy_copy_is_ready(isolated):
    assert probe(isolated).status_code == 200


def test_readyz_fails_when_the_public_database_is_missing(isolated):
    isolated["publicDb"].unlink()
    response = probe(isolated)
    assert response.status_code == 503
    body = response.json()
    assert body["checks"]["publicDatabase"] == "failed"
    assert any("no compiled public database" in item for item in body["problems"])


def test_readyz_fails_when_the_database_carries_no_published_release(isolated, tmp_path):
    import sqlite3

    connection = sqlite3.connect(str(isolated["publicDb"]), isolation_level=None)
    try:
        connection.execute("DELETE FROM ktel_publication_releases")
    finally:
        connection.close()
    response = probe(isolated)
    assert response.status_code == 503
    body = response.json()
    assert body["checks"]["publishedRelease"] == "missing"
    assert any("no published release" in item for item in body["problems"])


def test_readyz_fails_on_a_release_mismatch(isolated):
    manifest_path = isolated["releaseDir"] / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["releaseId"] = "0000000000000000"
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    response = probe(isolated)
    assert response.status_code == 503
    body = response.json()
    assert body["checks"]["releaseAgreement"] == "failed"
    assert any("published database carries" in item for item in body["problems"])


def test_readyz_fails_on_a_corrupt_pack(isolated):
    manifest = json.loads(
        (isolated["releaseDir"] / "manifest.json").read_text(encoding="utf-8")
    )
    victim = isolated["releaseDir"] / manifest["files"]["stops"]["path"]
    victim.write_bytes(b'{"tampered": true}')
    response = probe(isolated)
    assert response.status_code == 503
    body = response.json()
    assert body["checks"]["manifest"] == "failed"
    assert any("digest mismatch" in item or "size mismatch" in item for item in body["problems"])


def test_readyz_fails_on_a_missing_pack(isolated):
    manifest = json.loads(
        (isolated["releaseDir"] / "manifest.json").read_text(encoding="utf-8")
    )
    (isolated["releaseDir"] / manifest["files"]["gtfs"]["path"]).unlink()
    response = probe(isolated)
    assert response.status_code == 503
    assert response.json()["checks"]["manifest"] == "failed"


def test_readyz_fails_when_the_manifest_is_absent(isolated):
    (isolated["releaseDir"] / "manifest.json").unlink()
    response = probe(isolated)
    assert response.status_code == 503
    assert response.json()["checks"]["manifest"] == "failed"


def test_readyz_fails_on_a_corrupt_database(isolated):
    isolated["publicDb"].write_bytes(b"this is not a SQLite database" * 64)
    response = probe(isolated)
    assert response.status_code == 503
    body = response.json()
    assert body["checks"]["publicDatabase"] in {"failed", "ok"}
    assert body["problems"]


def test_public_reads_fail_gracefully_on_a_corrupt_database(isolated):
    isolated["publicDb"].write_bytes(b"this is not a SQLite database" * 64)
    environment = {
        f"{BRAND.env_prefix}_PUBLIC_DB_PATH": str(isolated["publicDb"]),
        f"{BRAND.env_prefix}_RELEASE_DIR": str(isolated["releaseDir"]),
        f"{BRAND.env_prefix}_DATA_MODE": "demo",
    }
    app = create_app(environ=environment)
    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.get("/v1/meta")
    assert response.status_code == 503
    assert response.json()["error"]["code"] == "unavailable"
    # Liveness is unaffected: the process is fine, the data is not.
    with TestClient(app, raise_server_exceptions=False) as client:
        assert client.get("/healthz").status_code == 200


def test_public_reads_fail_gracefully_when_the_database_is_missing(isolated):
    isolated["publicDb"].unlink()
    environment = {
        f"{BRAND.env_prefix}_PUBLIC_DB_PATH": str(isolated["publicDb"]),
        f"{BRAND.env_prefix}_RELEASE_DIR": str(isolated["releaseDir"]),
        f"{BRAND.env_prefix}_DATA_MODE": "demo",
    }
    app = create_app(environ=environment)
    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.get("/v1/places")
    assert response.status_code == 503
    assert response.json()["error"]["code"] == "unavailable"


def test_the_manifest_endpoint_refuses_a_mismatched_release(isolated):
    manifest_path = isolated["releaseDir"] / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["releaseId"] = "0000000000000000"
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
    environment = {
        f"{BRAND.env_prefix}_PUBLIC_DB_PATH": str(isolated["publicDb"]),
        f"{BRAND.env_prefix}_RELEASE_DIR": str(isolated["releaseDir"]),
        f"{BRAND.env_prefix}_DATA_MODE": "demo",
    }
    app = create_app(environ=environment)
    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.get("/v1/offline/manifest")
        pack = client.get("/v1/gtfs")
    assert response.status_code == 409
    assert response.json()["error"]["code"] == "release_mismatch"
    assert pack.status_code == 409
