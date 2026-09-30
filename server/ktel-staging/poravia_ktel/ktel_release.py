"""Build immutable, checksummed public packs and write the manifest last.

This module replaces the Syrmos-era ``generator`` snapshot module that was never
transplanted into this repository. The old module produced rail snapshots for a
different product; the behaviour worth keeping was its release contract, not its
rail payloads. That contract is preserved here and restated against coach
publishing and GTFS export:

* every pack is content addressed, so a file name cannot be reused for
  different bytes;
* the manifest records a SHA-256 for every pack and is written **last**, so a
  reader never sees a manifest pointing at a half-written pack;
* generating twice from an unchanged public database produces an identical
  manifest, which is what makes rollback and client cache validation safe.
"""
from __future__ import annotations

import hashlib
import json
import os
import shutil
import sqlite3
from pathlib import Path
from typing import Any, Callable, Protocol

from . import branding, ktel_api, ktel_db, ktel_gtfs
from .ktel_publish import compile_public_database

#: Upper bound on rows served inside a single pack. Packs are split by logical
#: payload rather than paged, so this is a guard against an unbounded release,
#: not a pagination window.
PACK_ROW_LIMIT = 50_000


class ReleaseConsistencyError(RuntimeError):
    """Raised when a manifest and its packs disagree."""


class PayloadProvider(Protocol):
    """Builds the logical pack name to body mapping for one release.

    The release mechanism below owns content addressing, digesting, the trailing
    manifest and rollback. It does not own the *shape* of what goes inside a
    pack. Passing a provider is how the public API service publishes packs in
    the published contract shape, so an offline client sees exactly what the
    matching endpoint would have returned, without forking this mechanism.
    """

    def __call__(
        self, conn: sqlite3.Connection, release_id: str
    ) -> dict[str, bytes]:  # pragma: no cover - structural type only
        ...


def _canonical_json(payload: Any) -> bytes:
    return json.dumps(
        payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).encode("utf-8")


def _digest(body: bytes) -> str:
    return hashlib.sha256(body).hexdigest()


def _service_dates(conn: sqlite3.Connection) -> list[str]:
    return [
        row["service_date"]
        for row in conn.execute(
            "SELECT DISTINCT service_date FROM ktel_trips "
            "WHERE publication_state='published' AND service_date IS NOT NULL "
            "ORDER BY service_date"
        ).fetchall()
    ]


def _collect_payloads(
    conn: sqlite3.Connection, release_id: str
) -> dict[str, bytes]:
    """Return the logical pack name to canonical body mapping."""
    packs: dict[str, bytes] = {
        "registry": _canonical_json(ktel_api.registry_payload(conn)),
        "coverage": _canonical_json(ktel_api.coverage_payload(conn)),
        "sources": _canonical_json(ktel_api.sources_payload(conn)),
        "stops": _canonical_json(
            ktel_api.stops_payload(conn, limit=PACK_ROW_LIMIT)
        ),
        "routes": _canonical_json(
            ktel_api.routes_payload(conn, limit=PACK_ROW_LIMIT)
        ),
    }
    for service_date in _service_dates(conn):
        packs[f"trips-{service_date}"] = _canonical_json(
            ktel_api.trips_payload(
                conn, service_date=service_date, limit=PACK_ROW_LIMIT
            )
        )
    packs["gtfs"] = ktel_gtfs.build_feed_zip(conn, release_id=release_id)
    return packs


def _write_atomic(path: Path, body: bytes) -> None:
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_bytes(body)
    os.replace(temporary, path)


def generate_public_release(
    out_dir: Path | str,
    ingest_db_path: str = ktel_db.DEFAULT_KTEL_DB_PATH,
    public_db_path: str = ktel_db.DEFAULT_KTEL_PUBLIC_DB_PATH,
    *,
    compile_first: bool = True,
    payload_provider: PayloadProvider | Callable[..., dict[str, bytes]] | None = None,
) -> dict[str, Any]:
    """Compile, then publish content-addressed packs and a trailing manifest.

    ``payload_provider`` substitutes another set of pack payloads while keeping
    every release guarantee documented above. Omitting it keeps the historical
    staging payload shapes.
    """
    root = Path(out_dir) / branding.PRODUCT_SLUG
    packs_dir = root / "packs"
    packs_dir.mkdir(parents=True, exist_ok=True)

    if compile_first:
        compiled = compile_public_database(
            ingest_db_path=ingest_db_path, public_db_path=public_db_path
        )
        release_id = compiled["releaseId"]
        counts = compiled["counts"]
    else:
        with ktel_db.connect(public_db_path, read_only=True) as public:
            release_id = ktel_api.release_id(public) or ""
            counts = {}
        if not release_id:
            raise ReleaseConsistencyError(
                "public database carries no published release"
            )

    with ktel_db.connect(public_db_path, read_only=True) as public:
        published_release = ktel_api.release_id(public)
        if published_release != release_id:
            raise ReleaseConsistencyError(
                "compiled release "
                f"{release_id} does not match public database "
                f"{published_release}; refusing to publish a mixed snapshot"
            )
        metadata = ktel_api.release_metadata(public)
        payloads = (payload_provider or _collect_payloads)(public, release_id)

    files: dict[str, dict[str, Any]] = {}
    for name in sorted(payloads):
        body = payloads[name]
        digest = _digest(body)
        suffix = ".zip" if name == "gtfs" else ".json"
        filename = f"{name}-{digest[:16]}{suffix}"
        target = packs_dir / filename
        if not target.exists() or _digest(target.read_bytes()) != digest:
            _write_atomic(target, body)
        files[name] = {
            "path": f"packs/{filename}",
            "sha256": digest,
            "bytes": len(body),
            "mediaType": (
                "application/zip" if name == "gtfs" else "application/json"
            ),
        }

    manifest = {
        "contractVersion": branding.PUBLIC_CONTRACT_VERSION,
        "product": branding.PRODUCT_NAME,
        "releaseId": release_id,
        "publishedAt": metadata["publishedAt"],
        "counts": counts,
        "files": files,
    }
    manifest_body = json.dumps(
        manifest, ensure_ascii=False, indent=2, sort_keys=True
    ).encode("utf-8") + b"\n"

    previous = root / "manifest.json"
    if previous.exists():
        # Keep exactly one previous manifest so a rollback has a target.
        shutil.copy2(previous, root / "manifest-previous.json")
    _write_atomic(previous, manifest_body)

    verify_release(root)
    return {
        "releaseId": release_id,
        "manifestPath": str(previous),
        "packDirectory": str(packs_dir),
        "counts": counts,
        "files": files,
    }


def verify_release(root: Path | str) -> dict[str, Any]:
    """Re-read a published directory and prove the manifest matches the packs."""
    root = Path(root)
    manifest_path = root / "manifest.json"
    if not manifest_path.exists():
        raise ReleaseConsistencyError(f"no manifest at {manifest_path}")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    for name, metadata in manifest["files"].items():
        path = root / metadata["path"]
        if not path.exists():
            raise ReleaseConsistencyError(f"pack {name} missing at {path}")
        body = path.read_bytes()
        if _digest(body) != metadata["sha256"]:
            raise ReleaseConsistencyError(f"pack {name} digest mismatch")
        if len(body) != metadata["bytes"]:
            raise ReleaseConsistencyError(f"pack {name} size mismatch")
    return manifest


def rollback_release(root: Path | str) -> dict[str, Any]:
    """Restore the previous manifest. Packs are immutable, so none are deleted."""
    root = Path(root)
    previous = root / "manifest-previous.json"
    if not previous.exists():
        raise ReleaseConsistencyError("no previous manifest to roll back to")
    _write_atomic(root / "manifest.json", previous.read_bytes())
    previous.unlink()
    return verify_release(root)


# Backwards-compatible alias for the historical Syrmos entry point name used by
# the transplanted test suite.
def _generate_ktel_snapshots(
    out_dir: Path | str,
    ingest_db_path: str,
    public_db_path: str,
) -> dict[str, Any]:
    return generate_public_release(out_dir, ingest_db_path, public_db_path)
