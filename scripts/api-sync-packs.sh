#!/usr/bin/env bash
# Copy one generated release into a client's bundled data directory.
#
#   scripts/api-sync-packs.sh <target-dir> [release-dir]
#
# This is the shared implementation behind scripts/web-sync-packs.sh and
# scripts/android-sync-packs.sh. It exists so there is one copier as well as one
# generator: a client bundles the release byte for byte, and the API serves those
# same bytes, so an offline client and an online client can never disagree.
#
# The copy is verified before and after. A target is only replaced once the new
# release has been staged and checked, so an interrupted sync cannot leave a
# client with a manifest that points at packs it does not have.
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"

PY="${PY:-$ROOT/.venv/bin/python}"
case "$PY" in /*) ;; *) [ -x "$ROOT/$PY" ] && PY="$ROOT/$PY" ;; esac
[ -x "$PY" ] || PY="$(command -v python3)"

SLUG="$("$PY" -c 'import json;print(json.load(open("brand.json"))["slug"])')"

TARGET="${1:?usage: api-sync-packs.sh <target-dir> [release-dir]}"
RELEASE="${2:-$ROOT/artifacts/releases/$SLUG}"

case "$TARGET" in /*) ;; *) TARGET="$ROOT/$TARGET" ;; esac
case "$RELEASE" in /*) ;; *) RELEASE="$ROOT/$RELEASE" ;; esac

if [ ! -f "$RELEASE/manifest.json" ]; then
  echo "FAIL: no release at $RELEASE" >&2
  echo "      run scripts/api-seed-demo.sh first" >&2
  exit 1
fi

verify() {
  PYTHONPATH="$ROOT/server/api" "$PY" - "$1" <<'PYCODE'
import sys
from publicapi import _staging  # noqa: F401
from poravia_ktel.ktel_release import verify_release

manifest = verify_release(sys.argv[1])
print(f"  verified {len(manifest['files'])} packs of release {manifest['releaseId']}")
PYCODE
}

echo "Syncing release packs"
echo "  from: $RELEASE"
echo "    to: $TARGET"
verify "$RELEASE"

STAGING="$TARGET.sync.$$"
rm -rf "$STAGING"
mkdir -p "$(dirname "$TARGET")"

# Copy the manifest and the packs, and nothing else. A retained previous
# manifest is a server-side rollback target, not something a client bundles.
mkdir -p "$STAGING/packs"
cp "$RELEASE/manifest.json" "$STAGING/manifest.json"
find "$RELEASE/packs" -maxdepth 1 -type f \( -name '*.json' -o -name '*.zip' \) \
  -exec cp {} "$STAGING/packs/" \;

verify "$STAGING"

rm -rf "$TARGET"
mv "$STAGING" "$TARGET"

echo "Synced. The client reads manifest.json and packs/ from this directory."
