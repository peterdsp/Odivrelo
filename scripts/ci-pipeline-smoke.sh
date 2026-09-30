#!/usr/bin/env bash
# Prove the whole data path runs, then assert the properties a release must
# have before anything is allowed to ship on top of it.
#
# The seed, import, review, compile and pack steps live in
# scripts/api-seed-demo.sh so there is exactly one code path for them. This
# script runs that, then checks the result independently.
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"
PY="${PY:-$ROOT/.venv/bin/python}"
case "$PY" in /*) ;; *) [ -x "$ROOT/$PY" ] && PY="$ROOT/$PY" ;; esac
export PY

SLUG=$("$PY" -c 'import json;print(json.load(open("brand.json"))["slug"])')
RELEASE_DIR="${RELEASE_DIR:-$ROOT/artifacts/releases/$SLUG}"

echo "== building the demonstration release =="
bash scripts/api-seed-demo.sh

echo
echo "== asserting release properties =="
cd server/ktel-staging
PYTHONPATH=. "$PY" - "$RELEASE_DIR" <<'PYCODE'
import json, sys
from pathlib import Path
from hodomap_ktel import ktel_release

root = Path(sys.argv[1])
manifest = ktel_release.verify_release(root)
print(f"  manifest verifies: {len(manifest['files'])} packs, "
      f"release {manifest['releaseId']}")

failures = []

# 1. The manifest must be the last thing written, so every pack it names has
#    to exist and match. verify_release above already enforces that.

# 2. Content addressing: a pack's file name must carry its own digest, or a
#    stale cached copy could be served for new bytes.
for name, meta in manifest["files"].items():
    if meta["sha256"][:16] not in Path(meta["path"]).name:
        failures.append(f"pack {name} is not content addressed")

# 3. Nothing rights-pending or unreviewed may appear in any public pack.
forbidden = ("Rights pending", "rights-pending", "candidate-only")
for name, meta in manifest["files"].items():
    if not meta["path"].endswith(".json"):
        continue
    body = (root / meta["path"]).read_text(encoding="utf-8")
    for needle in forbidden:
        if needle in body:
            failures.append(f"pack {name} leaks a withheld row: {needle!r}")

# 4. The GTFS feed must be present and must carry the release id.
if "gtfs" not in manifest["files"]:
    failures.append("no GTFS pack in the release")
else:
    import io, zipfile
    blob = (root / manifest["files"]["gtfs"]["path"]).read_bytes()
    with zipfile.ZipFile(io.BytesIO(blob)) as archive:
        members = set(archive.namelist())
        required = {"agency.txt", "stops.txt", "routes.txt", "trips.txt",
                    "stop_times.txt", "feed_info.txt"}
        missing = required - members
        if missing:
            failures.append(f"GTFS feed missing {sorted(missing)}")
        else:
            feed_info = archive.read("feed_info.txt").decode("utf-8")
            if manifest["releaseId"] not in feed_info:
                failures.append("GTFS feed_version is not the release id")
            stop_times = archive.read("stop_times.txt").decode("utf-8")
            past_midnight = [
                line for line in stop_times.splitlines()[1:]
                if line.split(",")[2][:2].isdigit()
                and int(line.split(",")[2][:2]) >= 24
            ]
            print(f"  GTFS ok, {len(past_midnight)} stop times past 24:00")

# 5. The demonstration dataset must be unmistakable.
meta = json.loads(
    (root / manifest["files"]["meta"]["path"]).read_text(encoding="utf-8")
)
names = " ".join(json.dumps(meta, ensure_ascii=False).split())
if "demonstration" not in names.lower() and "demo" not in names.lower():
    failures.append("the meta pack does not label itself as demonstration data")

if failures:
    for line in failures:
        print(f"  FAIL {line}")
    raise SystemExit(1)
print("  all release assertions passed")
PYCODE

cd "$ROOT"
echo
echo "== artifact manifest =="
"$PY" scripts/artifact-manifest.py

echo
echo "Pipeline smoke passed."
