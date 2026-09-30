#!/usr/bin/env bash
# Builds the static OdivreloCore XCFramework consumed by the iOS application.
#
#   bash scripts/shared-build-xcframework.sh            # Debug and Release
#   bash scripts/shared-build-xcframework.sh --release  # Release only
#
# Output:
#   shared/core/build/XCFramework/debug/OdivreloCore.xcframework
#   shared/core/build/XCFramework/release/OdivreloCore.xcframework
#
# Slices: ios-arm64 (device) and ios-arm64_x86_64-simulator.
# There is no system Java on this machine, so JAVA_HOME is set explicitly.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

: "${JAVA_HOME:=/opt/homebrew/opt/openjdk@21}"
if [[ ! -x "${JAVA_HOME}/bin/java" ]]; then
  echo "error: no JDK at JAVA_HOME=${JAVA_HOME}" >&2
  exit 1
fi
export JAVA_HOME
export PATH="${JAVA_HOME}/bin:${PATH}"

task=":shared:core:assembleOdivreloCoreXCFramework"
if [[ "${1:-}" == "--release" ]]; then
  task=":shared:core:assembleOdivreloCoreReleaseXCFramework"
fi

echo "JAVA_HOME=${JAVA_HOME}"
echo "Running ./gradlew ${task}"
./gradlew "${task}" --console=plain

out_dir="${repo_root}/shared/core/build/XCFramework"
if [[ ! -d "${out_dir}" ]]; then
  echo "error: expected output directory ${out_dir} was not produced" >&2
  exit 1
fi

echo
echo "XCFramework output:"
find "${out_dir}" -maxdepth 2 -name '*.xcframework' -print
echo
for framework in "${out_dir}"/*/OdivreloCore.xcframework; do
  [[ -d "${framework}" ]] || continue
  echo "== ${framework}"
  /usr/libexec/PlistBuddy -c 'Print :AvailableLibraries' "${framework}/Info.plist" 2>/dev/null \
    | grep -E 'LibraryIdentifier|SupportedArchitectures|SupportedPlatform' || true
done
