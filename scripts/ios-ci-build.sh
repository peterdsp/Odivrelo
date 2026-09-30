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

# xcodebuild refuses to overwrite an existing result bundle, so a second local
# run of this script would fail on the first build. CI starts clean; a
# developer re-running it should not have to.
rm -rf "$ARTIFACTS"/build-debug.xcresult "$ARTIFACTS"/tests.xcresult \
       "$ARTIFACTS"/build-release.xcresult

SCHEME="${SCHEME:-Poravia}"
DEVICE="${DEVICE:-iPhone 15}"

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
# ios-bootstrap.sh regenerates everything derived from a source of truth
# (brand, theme, icon, strings), copies the XCFramework into apps/ios/Frameworks
# and generates from project-core.yml, which is what actually links the shared
# core and defines PORAVIA_CORE_AVAILABLE. Generating from the plain spec here
# would build an app with no shared core and report success.
bash scripts/ios-bootstrap.sh

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

echo "== the built app really links the shared core =="
# Ask xcodebuild where it put the product rather than guessing a path: the
# default DerivedData location is not predictable and a wrong guess would make
# this check silently unenforceable.
BUILT_DIR=$(xcodebuild -project "$PROJECT" -scheme "$SCHEME" \
  -destination "id=$UDID" -configuration Debug -showBuildSettings 2>/dev/null \
  | awk -F' = ' '/ BUILT_PRODUCTS_DIR = /{print $2; exit}')
APP_BINARY="$BUILT_DIR/${SCHEME}.app/${SCHEME}"

if [ ! -f "$APP_BINARY" ]; then
  echo "  could not find the built binary at $APP_BINARY" >&2
  exit 1
fi

# A Debug build keeps its Swift code in a side dylib, so check both.
CORE_SYMBOLS=$(nm -a "$APP_BINARY" 2>/dev/null | grep -c 'kfun:dev.peterdsp.poravia.core' || true)
if [ "$CORE_SYMBOLS" -lt 100 ] && [ -f "${APP_BINARY}.debug.dylib" ]; then
  CORE_SYMBOLS=$(nm -a "${APP_BINARY}.debug.dylib" 2>/dev/null | grep -c 'kfun:dev.peterdsp.poravia.core' || true)
fi
# Objective-C class data survives stripping, so it is the fallback proof.
if [ "$CORE_SYMBOLS" -lt 100 ]; then
  CORE_SYMBOLS=$(otool -oV "$APP_BINARY" 2>/dev/null | grep -c 'PoraviaCorePoravia' || true)
fi

if [ "$CORE_SYMBOLS" -lt 50 ]; then
  echo "  the built app does not contain the shared core ($CORE_SYMBOLS symbols)" >&2
  echo "  binary: $APP_BINARY" >&2
  exit 1
fi
echo "  $CORE_SYMBOLS PoraviaCore symbols in $APP_BINARY"

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
