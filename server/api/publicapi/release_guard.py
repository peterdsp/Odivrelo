"""Fail-closed checks that stop demonstration data from shipping as production.

The release mechanism in :mod:`odivrelo_ktel.ktel_release` already proves a
manifest matches its packs. This module adds the one property that matters for
distribution: a release that is about to go out on the production channel must
actually be the real dataset, and a release on the demonstration channel must
be unmistakably labelled as such.

It reads only the published release directory, never a database, so it works the
same way in CI, on a developer machine and against a downloaded release. Every
failure raises :class:`ReleaseGuardError` with a precise reason and the CLI
exits non-zero, so a missing release, a failed import or a tampered pack can
never pass silently.
"""
from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from . import _staging  # noqa: F401  (installs the staging import path)
from odivrelo_ktel import ktel_release  # noqa: E402

#: Operator id and copy that only ever belong to the invented demonstration
#: dataset. If any of these reaches a release stamped ``real`` the stamp is
#: wrong, regardless of what the data mode field claims.
DEMO_OPERATOR_ID = "demo-aloria-coach"
DEMO_COPY_MARKERS = ("aloria", "demonstration operator", "fictional")

CHANNELS = ("demo", "real")


class ReleaseGuardError(RuntimeError):
    """Raised when a release does not match the channel it is shipping on."""


def _load_pack(release_dir: Path, manifest: dict[str, Any], name: str) -> Any:
    entry = manifest["files"].get(name)
    if entry is None:
        raise ReleaseGuardError(f"release has no {name!r} pack")
    return json.loads((release_dir / entry["path"]).read_text(encoding="utf-8"))


def inspect_release(release_dir: Path | str) -> dict[str, Any]:
    """Verify the manifest and summarise what channel the release belongs to."""
    release_dir = Path(release_dir)
    if not (release_dir / "manifest.json").exists():
        # A missing release is the most common fail-closed case: a missing
        # dataset variable, or an import that never produced anything.
        raise ReleaseGuardError(
            f"no release at {release_dir}; refusing to treat an absent or failed "
            "build as shippable"
        )
    # verify_release raises ReleaseConsistencyError on any pack mismatch, which
    # covers a stale or tampered asset.
    manifest = ktel_release.verify_release(release_dir)

    meta = _load_pack(release_dir, manifest, "meta")
    data_mode = meta.get("dataMode")

    operators = _load_pack(release_dir, manifest, "operators")
    operator_ids = set(operators.get("operators", {}).keys())

    journey_packs = [name for name in manifest["files"] if name.startswith("journeys-")]
    published_journeys = 0
    for name in journey_packs:
        body = _load_pack(release_dir, manifest, name)
        published_journeys += len(body.get("results", []))

    return {
        "releaseId": manifest["releaseId"],
        "dataMode": data_mode,
        "operatorIds": operator_ids,
        "publishedJourneys": published_journeys,
        "manifest": manifest,
    }


def assert_channel(release_dir: Path | str, expect: str) -> dict[str, Any]:
    """Raise unless the release at ``release_dir`` matches channel ``expect``."""
    if expect not in CHANNELS:
        raise ValueError(f"unknown channel: {expect!r}; expected one of {CHANNELS}")

    info = inspect_release(release_dir)

    if info["dataMode"] != expect:
        raise ReleaseGuardError(
            f"release {info['releaseId']} is data mode {info['dataMode']!r}, "
            f"but the {expect!r} channel was requested; refusing to ship it"
        )

    if expect == "real":
        if DEMO_OPERATOR_ID in info["operatorIds"]:
            raise ReleaseGuardError(
                "a real release carries the demonstration operator "
                f"{DEMO_OPERATOR_ID!r}; the real stamp cannot launder demo data"
            )
        leaked = _demo_copy_in(release_dir, info["manifest"])
        if leaked:
            raise ReleaseGuardError(
                f"a real release contains demonstration copy ({leaked}); refusing "
                "to ship invented data as production"
            )
        if info["publishedJourneys"] == 0:
            raise ReleaseGuardError(
                "a real release has no published journeys; refusing to ship an "
                "empty or failed import as production data"
            )

    return info


def _demo_copy_in(release_dir: Path, manifest: dict[str, Any]) -> str | None:
    """Scan the small identity packs for copy that only the demo ever uses."""
    for name in ("meta", "coverage", "operators"):
        entry = manifest["files"].get(name)
        if entry is None:
            continue
        text = (release_dir / entry["path"]).read_text(encoding="utf-8").lower()
        for marker in DEMO_COPY_MARKERS:
            if marker in text:
                return marker
    return None


def main(argv: list[str] | None = None) -> int:
    import argparse

    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--release-dir", required=True)
    parser.add_argument("--expect", required=True, choices=CHANNELS)
    arguments = parser.parse_args(argv)
    try:
        info = assert_channel(arguments.release_dir, arguments.expect)
    except ReleaseGuardError as error:
        print(f"release guard FAILED: {error}")
        return 1
    print(
        f"release guard passed: {arguments.expect} channel, release "
        f"{info['releaseId']}, {info['publishedJourneys']} published journeys"
    )
    return 0


if __name__ == "__main__":  # pragma: no cover - CLI entry point
    raise SystemExit(main())
