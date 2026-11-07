#!/usr/bin/env bash
# Captures the App Store screenshots from the running app on the real release.
#
#   bash scripts/ios-app-store-screenshots.sh <simulator udid> <iphone|ipad> [en|el]
#
# Runs AppStoreScreenshotTests (OdivreloUITests) on the named simulator, in the
# given language, and exports its screenshots to
# apps/ios/AppStore/screenshots/<lang>/<device>/. Then frame them with
# scripts/ios-marketing-shots.py.
#
# Use an iPhone 6.9-inch simulator (1320x2868) and a 13-inch iPad (2064x2752).
# The project must be bootstrapped with the shared core linked
# (scripts/ios-bootstrap.sh). The search runs on the day of capture, so capture
# on a service date the bundled release holds.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UDID="$1"
DEVICE="$2"
LANG_CODE="${3:-en}"
DERIVED="${DERIVED_DATA:-${TMPDIR:-/tmp}/odivrelo-store-shots}"
OUT="${REPO_ROOT}/apps/ios/AppStore/screenshots/${LANG_CODE}/${DEVICE}"
RESULT="${DERIVED}/shots-${DEVICE}-${LANG_CODE}.xcresult"

xcrun simctl boot "$UDID" 2>/dev/null || true
xcrun simctl bootstatus "$UDID" -b >/dev/null
xcrun simctl status_bar "$UDID" override --time 9:41 --dataNetwork wifi --wifiMode active --wifiBars 3 \
  --cellularMode active --cellularBars 4 --batteryState charged --batteryLevel 100

rm -rf "$RESULT"
TEST_RUNNER_ODIVRELO_SHOT_LANGUAGE="$LANG_CODE" xcodebuild test \
  -project "${REPO_ROOT}/apps/ios/Odivrelo.xcodeproj" -scheme Odivrelo -configuration Debug \
  -destination "id=${UDID}" -derivedDataPath "$DERIVED" -resultBundlePath "$RESULT" \
  -only-testing:OdivreloUITests/AppStoreScreenshotTests -quiet

EXPORT="${DERIVED}/export-${DEVICE}-${LANG_CODE}"
rm -rf "$EXPORT"
xcrun xcresulttool export attachments --path "$RESULT" --output-path "$EXPORT" >/dev/null
mkdir -p "$OUT"
rm -f "$OUT"/*.png
python3 - "$EXPORT" "$OUT" <<'PY'
import json, pathlib, shutil, sys
export, out = map(pathlib.Path, sys.argv[1:])
for test in json.loads((export / "manifest.json").read_text()):
    for a in test["attachments"]:
        name = a["suggestedHumanReadableName"].split("_")[0]
        shutil.copy(export / a["exportedFileName"], out / f"{name}.png")
        print(out / f"{name}.png")
PY

xcrun simctl status_bar "$UDID" clear
