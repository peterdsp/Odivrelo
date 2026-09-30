#!/usr/bin/env bash
# Build and test the iOS application, on CI or locally.
#
#   bash scripts/ios-ci-build.sh                 # simulator build + tests
#   bash scripts/ios-ci-build.sh --release       # also build the Release config
#
# The shared XCFramework must exist first; build it with
# scripts/shared-build-xcframework.sh. This script fails loudly if it is
# missing rather than quietly building an app without its shared core.
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"
ARTIFACTS="$ROOT/apps/ios/artifacts"
mkdir -p "$ARTIFACTS"

SCHEME="${SCHEME:-Poravia}"
DEVICE="${DEVICE:-iPhone 18 Pro Max}"

echo "== shared core =="
if ! ls "$ROOT"/shared/core/build/XCFramework/*/PoraviaCore.xcframework >/dev/null 2>&1; then
  echo "PoraviaCore.xcframework is missing." >&2
  echo "Run: bash scripts/shared-build-xcframework.sh" >&2
  exit 1
fi
echo "  found $(ls -d "$ROOT"/shared/core/build/XCFramework/*/PoraviaCore.xcframework | tr '\n' ' ')"

echo "== generate the Xcode project =="
if ! command -v xcodegen >/dev/null 2>&1; then
  echo "xcodegen is not installed. Install it, or open an existing project." >&2
  exit 1
fi
(cd apps/ios && xcodegen generate --quiet)

PROJECT="apps/ios/${SCHEME}.xcodeproj"
[ -d "$PROJECT" ] || { echo "no project at $PROJECT" >&2; exit 1; }

# Resolve the simulator by name so a renamed runtime fails here, visibly,
# rather than silently building for the wrong destination.
UDID=$(xcrun simctl list devices available -j \
  | python3 -c "
import json,sys
want = sys.argv[1]
data = json.load(sys.stdin)['devices']
for runtime, devices in data.items():
    for d in devices:
        if d['name'] == want:
            print(d['udid']); raise SystemExit(0)
raise SystemExit(f'simulator not found: {want}')
" "$DEVICE")
echo "== destination: $DEVICE ($UDID) =="

echo "== build, Debug, simulator =="
xcodebuild -project "$PROJECT" -scheme "$SCHEME" \
  -destination "id=$UDID" -configuration Debug \
  -resultBundlePath "$ARTIFACTS/build-debug.xcresult" \
  -quiet build 2>&1 | tail -30

echo "== test =="
xcodebuild -project "$PROJECT" -scheme "$SCHEME" \
  -destination "id=$UDID" -configuration Debug \
  -resultBundlePath "$ARTIFACTS/tests.xcresult" \
  -quiet test 2>&1 | tail -40

if [ "${1:-}" = "--release" ]; then
  echo "== build, Release, device-targeted =="
  # Unsigned: there is no signing identity in this environment (EB-02). This
  # proves the Release configuration compiles and links the shared framework.
  xcodebuild -project "$PROJECT" -scheme "$SCHEME" \
    -configuration Release -destination 'generic/platform=iOS' \
    -resultBundlePath "$ARTIFACTS/build-release.xcresult" \
    CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY="" \
    -quiet build 2>&1 | tail -30
  echo "  Release built unsigned. An unsigned build is NOT an installable beta."
fi

echo
echo "iOS build and tests completed. Result bundles in apps/ios/artifacts/."
