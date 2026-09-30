#!/usr/bin/env bash
# Regenerates everything the iOS project is derived from, then generates the
# Xcode project itself.
#
#   brand.json                  -> Poravia/Generated/Brand.swift
#   design/tokens/*.json        -> Poravia/Generated/Theme.swift + colour sets
#   design/logo/*.svg           -> app icon, brand mark, wordmark
#   strings.source.json         -> Localizable.xcstrings + Generated/L10n.swift
#   shared/core/build/XCFramework -> apps/ios/Frameworks/PoraviaCore.xcframework
#   apps/ios/project.yml        -> apps/ios/Poravia.xcodeproj  (via XcodeGen)
#
# The project is generated, never hand-edited, so nothing can drift between the
# spec and what actually builds. Run this after any change to the inputs above.
#
# Usage:
#   bash scripts/ios-bootstrap.sh              # link the release XCFramework
#   PORAVIA_CORE_VARIANT=debug bash scripts/ios-bootstrap.sh
#   PORAVIA_CORE_VARIANT=none  bash scripts/ios-bootstrap.sh   # no core at all
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IOS_DIR="${REPO_ROOT}/apps/ios"
FRAMEWORKS_DIR="${IOS_DIR}/Frameworks"
XCF_ROOT="${REPO_ROOT}/shared/core/build/XCFramework"
VARIANT="${PORAVIA_CORE_VARIANT:-release}"

say() { printf '\n== %s\n' "$1"; }

say "Generating brand constants"
bash "${REPO_ROOT}/scripts/ios-generate-brand.sh"

say "Generating theme from design tokens"
bash "${REPO_ROOT}/scripts/ios-generate-theme.sh"

say "Generating app icon, brand mark and wordmark"
bash "${REPO_ROOT}/scripts/ios-generate-appicon.sh"

say "Generating the String Catalog and its accessors"
bash "${REPO_ROOT}/scripts/ios-generate-strings.sh"

say "Resolving the shared core"
mkdir -p "${FRAMEWORKS_DIR}"
rm -rf "${FRAMEWORKS_DIR}/PoraviaCore.xcframework"

CORE_LINKED="no"
if [[ "${VARIANT}" != "none" ]]; then
  SOURCE="${XCF_ROOT}/${VARIANT}/PoraviaCore.xcframework"
  if [[ ! -d "${SOURCE}" ]]; then
    # Fall back to whichever variant exists, so a debug-only build still links.
    for candidate in release debug; do
      if [[ -d "${XCF_ROOT}/${candidate}/PoraviaCore.xcframework" ]]; then
        SOURCE="${XCF_ROOT}/${candidate}/PoraviaCore.xcframework"
        VARIANT="${candidate}"
        break
      fi
    done
  fi

  if [[ -d "${SOURCE}" ]]; then
    # Copied rather than symlinked: Xcode resolves a symlinked XCFramework
    # inconsistently between the build and the archive step.
    cp -R "${SOURCE}" "${FRAMEWORKS_DIR}/PoraviaCore.xcframework"
    CORE_LINKED="yes"
    echo "linked PoraviaCore.xcframework (${VARIANT}) from ${SOURCE}"
    /usr/libexec/PlistBuddy -c 'Print :AvailableLibraries' \
      "${FRAMEWORKS_DIR}/PoraviaCore.xcframework/Info.plist" 2>/dev/null \
      | grep -E 'LibraryIdentifier|SupportedArchitectures|SupportedPlatform' || true
  else
    echo "warning: no PoraviaCore.xcframework under ${XCF_ROOT}" >&2
    echo "         build it with: JAVA_HOME=/opt/homebrew/opt/openjdk@21 bash scripts/shared-build-xcframework.sh" >&2
  fi
fi

say "Recording the source commit"
COMMIT="$(git -C "${REPO_ROOT}" rev-parse --short=12 HEAD 2>/dev/null || echo unknown)"
/usr/libexec/PlistBuddy -c "Set :PoraviaSourceCommit ${COMMIT}" \
  "${IOS_DIR}/Poravia/Resources/Info.plist" 2>/dev/null \
  || /usr/libexec/PlistBuddy -c "Add :PoraviaSourceCommit string ${COMMIT}" \
     "${IOS_DIR}/Poravia/Resources/Info.plist"
echo "commit ${COMMIT}"

say "Generating the Xcode project"
if ! command -v xcodegen >/dev/null 2>&1; then
  echo "error: xcodegen is required to generate apps/ios/Poravia.xcodeproj" >&2
  exit 1
fi

cd "${IOS_DIR}"
if [[ "${CORE_LINKED}" == "yes" ]]; then
  xcodegen generate --spec project-core.yml --quiet
  echo "generated with the shared core linked (PORAVIA_CORE_AVAILABLE is set)"
else
  xcodegen generate --spec project.yml --quiet
  echo "generated without the shared core; the app will report it as missing"
fi

say "Done"
echo "scheme:      Poravia"
echo "project:     ${IOS_DIR}/Poravia.xcodeproj"
echo "shared core: ${CORE_LINKED} (${VARIANT})"
