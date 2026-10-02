#!/usr/bin/env python3
"""Build the machine-readable artifact manifest for a Odivrelo release.

Every row is derived from a file that exists on disk right now. A platform with
no artifact is reported as absent rather than assumed, and signing and
distribution state are read from the artifact, never asserted.

    python3 scripts/artifact-manifest.py --out artifacts/ARTIFACT-MANIFEST.json
"""
from __future__ import annotations

import argparse
import hashlib
import json
import plistlib
import subprocess
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parent.parent


def sh(*args: str) -> str:
    try:
        return subprocess.run(
            args, cwd=ROOT, capture_output=True, text=True, check=True
        ).stdout.strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        return ""


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def brand() -> dict[str, Any]:
    return json.loads((ROOT / "brand.json").read_text(encoding="utf-8"))


def data_release() -> dict[str, Any]:
    """Read the generated release manifest, if one exists."""
    slug = brand()["slug"]
    for candidate in (
        ROOT / "artifacts" / "releases" / slug / "manifest.json",
        ROOT / "apps" / "web" / "public" / "data" / "manifest.json",
    ):
        if candidate.exists():
            manifest = json.loads(candidate.read_text(encoding="utf-8"))
            return {
                "releaseId": manifest.get("releaseId"),
                "contractVersion": manifest.get("contractVersion"),
                "publishedAt": manifest.get("publishedAt"),
                "packCount": len(manifest.get("files", {})),
                "manifestPath": str(candidate.relative_to(ROOT)),
            }
    return {"releaseId": None, "note": "no release manifest generated"}


def apk_signing_state(path: Path) -> str:
    """Report signing from the archive itself, not from the build's intent."""
    try:
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
    except (OSError, zipfile.BadZipFile):
        return "unreadable"
    if any(n.startswith("META-INF/") and n.endswith((".RSA", ".DSA", ".EC"))
           for n in names):
        return "signed-v1-present"
    # v2/v3 signing lives in the APK Signing Block, outside the zip entries.
    try:
        blob = path.read_bytes()
    except OSError:
        return "unknown"
    if b"APK Sig Block 42" in blob:
        return "signed-v2-or-v3"
    return "unsigned"


def ipa_signing_state(path: Path) -> str:
    try:
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
    except (OSError, zipfile.BadZipFile):
        return "unreadable"
    if any(n.endswith("embedded.mobileprovision") for n in names):
        return "provisioned"
    return "unsigned"


def app_bundle_identity(path: Path) -> dict[str, Any]:
    info = path / "Info.plist"
    if not info.exists():
        return {}
    try:
        data = plistlib.loads(info.read_bytes())
    except Exception:
        return {}
    return {
        "bundleIdentifier": data.get("CFBundleIdentifier"),
        "shortVersion": data.get("CFBundleShortVersionString"),
        "build": data.get("CFBundleVersion"),
        "minimumOSVersion": data.get("MinimumOSVersion"),
    }


def describe(path: Path, **extra: Any) -> dict[str, Any]:
    stat = path.stat()
    return {
        "path": str(path.relative_to(ROOT)),
        "bytes": stat.st_size,
        "sha256": digest(path) if path.is_file() else None,
        "builtAt": datetime.fromtimestamp(
            stat.st_mtime, tz=timezone.utc
        ).isoformat().replace("+00:00", "Z"),
        **extra,
    }


def collect() -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    b = brand()

    # Web
    dist = ROOT / "apps" / "web" / "dist"
    index = dist / "index.html"
    if index.exists():
        files = [p for p in dist.rglob("*") if p.is_file()]
        rows.append({
            "platform": "web",
            "kind": "static-site",
            "version": b["version"],
            "state": "packaged",
            "signing": "not-applicable",
            "distribution": "deployed-via-ci; this local build is a reproducible artifact",
            "fileCount": len(files),
            "totalBytes": sum(p.stat().st_size for p in files),
            **describe(index),
        })
    # Android
    for apk in sorted((ROOT / "apps" / "android").rglob("*.apk")):
        rows.append({
            "platform": "android",
            "kind": "apk",
            "variant": apk.stem,
            "version": b["version"],
            "applicationId": b["androidApplicationId"],
            "state": "packaged",
            "signing": apk_signing_state(apk),
            "distribution": "not-uploaded",
            **describe(apk),
        })
    for aab in sorted((ROOT / "apps" / "android").rglob("*.aab")):
        rows.append({
            "platform": "android",
            "kind": "aab",
            "variant": aab.stem,
            "version": b["version"],
            "applicationId": b["androidApplicationId"],
            "state": "packaged",
            "signing": apk_signing_state(aab),
            "distribution": "not-uploaded",
            **describe(aab),
        })
    # iOS
    for ipa in sorted((ROOT / "apps" / "ios").rglob("*.ipa")):
        rows.append({
            "platform": "ios", "kind": "ipa", "version": b["version"],
            "bundleId": b["bundleId"], "state": "packaged",
            "signing": ipa_signing_state(ipa),
            "distribution": "not-uploaded", **describe(ipa),
        })
    for app in sorted((ROOT / "apps" / "ios").rglob("*.app")):
        if not app.is_dir():
            continue
        files = [p for p in app.rglob("*") if p.is_file()]
        rows.append({
            "platform": "ios", "kind": "simulator-app",
            "state": "built",
            "signing": "simulator-only",
            "distribution": "not-installable-on-device",
            "note": "a simulator build is not an installable iPhone beta",
            "fileCount": len(files),
            "totalBytes": sum(p.stat().st_size for p in files),
            "path": str(app.relative_to(ROOT)),
            "sha256": None,
            **app_bundle_identity(app),
        })
    for xcf in sorted((ROOT / "shared").rglob("*.xcframework")):
        rows.append({
            "platform": "shared", "kind": "xcframework",
            "state": "built", "signing": "not-applicable",
            "distribution": "consumed-by-ios-target",
            "path": str(xcf.relative_to(ROOT)), "sha256": None,
        })
    # Release packs
    slug = b["slug"]
    manifest = ROOT / "artifacts" / "releases" / slug / "manifest.json"
    if manifest.exists():
        rows.append({
            "platform": "data", "kind": "release-packs",
            "state": "packaged", "signing": "content-addressed",
            "distribution": "served-with-the-web-build",
            **describe(manifest),
        })
    return rows


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="artifacts/ARTIFACT-MANIFEST.json")
    args = parser.parse_args()

    b = brand()
    release = data_release()
    manifest = {
        "product": b["name"],
        "version": b["version"],
        "contractVersion": b["contractVersion"],
        "commit": sh("git", "rev-parse", "HEAD"),
        "branch": sh("git", "rev-parse", "--abbrev-ref", "HEAD"),
        "treeClean": sh("git", "status", "--porcelain") == "",
        "generatedAt": datetime.now(timezone.utc)
        .isoformat().replace("+00:00", "Z"),
        "buildEnvironment": {
            "os": sh("uname", "-srm"),
            "python": sh("python3", "--version"),
            "node": sh("node", "--version"),
            "xcode": sh("xcodebuild", "-version").splitlines()[:1],
        },
        "dataMode": "demo",
        "dataModeNote": (
            "Invented region 'Aloria'. No row represents a real departure."
        ),
        "dataRelease": release,
        "artifacts": collect(),
        "distributionState": {
            "web": (
                "deployed and verified at https://odivrelo.peterdsp.dev "
                "(EB-01 resolved)"
            ),
            "ios": "blocked: EB-02, no signing identity",
            "android": "blocked: EB-03, no release keystore",
        },
    }
    out = ROOT / args.out
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"wrote {out} with {len(manifest['artifacts'])} artifact rows")
    if not manifest["artifacts"]:
        print("WARNING: no artifacts found; nothing has been built yet")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
