#!/usr/bin/env bash
# Bundle the generated release into the iOS client's resources.
#
#   scripts/ios-sync-packs.sh [release-dir]
#
# The iOS client ships release/manifest.json and release/packs/... inside its
# application bundle, copies them into the core's packs directory on first run
# and then calls adoptSeededRelease(), which verifies every digest before
# registering anything. This is the same delivery mechanism the Android client
# uses, reading the same files from the same one release, so a traveller who has
# just installed either application can search offline without downloading
# anything first.
#
# Copying is all this does. The bundled files get no extra trust: a file whose
# digest does not match the manifest is rejected by the core exactly as a
# tampered download would be.
#
# Default release directory: artifacts/releases/<brand slug>/
# Run scripts/api-seed-demo.sh first to produce one.
set -euo pipefail
cd "$(dirname "$0")/.."
DEST="apps/ios/Odivrelo/Resources/release"
bash scripts/api-sync-packs.sh "$DEST" ${1:+"$1"}

# Ship exactly the packs the manifest names, and nothing else.
#
# The release directory accumulates every pack every generator run has ever
# produced, so a plain copy would put well over a hundred superseded files into
# the application bundle. Only the ones the manifest names can ever be read, so
# the rest are dead weight a traveller would download with the application and
# never use.
python3 - "$DEST" <<'PRUNE'
import json, pathlib, sys

dest = pathlib.Path(sys.argv[1])
manifest = json.loads((dest / "manifest.json").read_text())
named = {pathlib.Path(entry["path"]).name for entry in manifest["files"].values()}

packs = dest / "packs"
removed = 0
for file in sorted(packs.iterdir()):
    if file.name not in named:
        file.unlink()
        removed += 1

missing = sorted(name for name in named if not (packs / name).is_file())
if missing:
    sys.exit("the release is incomplete, these packs are missing: " + ", ".join(missing))

total = sum(f.stat().st_size for f in packs.iterdir()) + (dest / "manifest.json").stat().st_size
print(f"  pruned {removed} superseded packs, kept {len(named)} ({total / 1024:.0f} KB)")
PRUNE
