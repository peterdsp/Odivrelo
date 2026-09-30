#!/usr/bin/env bash
# Runs Odivrelo on a named simulator and captures screenshots of real screens.
#
# Every shot comes from the running build, driven through the interface with
# simctl and the app's own launch arguments. Filenames carry the device, the
# iOS runtime, the scenario and the date, so a screenshot can always be traced
# back to what produced it.
#
# Usage:
#   bash scripts/ios-capture-evidence.sh "iPhone 15" normal el
#   bash scripts/ios-capture-evidence.sh "iPad Pro 13-inch (M5)" normal en
#   bash scripts/ios-capture-evidence.sh "iPhone Duo" offline sq
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${REPO_ROOT}/apps/ios/artifacts/screenshots"
APP_PATH="${ODIVRELO_APP:-/tmp/odivrelo-dd/Build/Products/Debug-iphonesimulator/Odivrelo.app}"
BUNDLE_ID="dev.peterdsp.odivrelo"

DEVICE_NAME="${1:-iPhone 15}"
SCENARIO="${2:-normal}"
LANGUAGE="${3:-el}"
APPEARANCE="${4:-light}"

mkdir -p "${OUT_DIR}"

if [[ ! -d "${APP_PATH}" ]]; then
  echo "error: ${APP_PATH} not found. Build the Debug configuration first." >&2
  exit 1
fi

# Resolve the device and remember its runtime, so the filename records it.
DEVICE_LINE="$(xcrun simctl list devices available \
  | grep -E "^ +${DEVICE_NAME} \(" | head -1)"
if [[ -z "${DEVICE_LINE}" ]]; then
  echo "error: no available simulator named '${DEVICE_NAME}'" >&2
  exit 1
fi
UDID="$(sed -E 's/.*\(([0-9A-F-]{36})\).*/\1/' <<<"${DEVICE_LINE}")"
RUNTIME="$(xcrun simctl list devices available \
  | awk -v udid="${UDID}" '/^-- iOS/ {rt=$0} $0 ~ udid {print rt; exit}' \
  | sed -E 's/-- iOS ([0-9.]+) --/\1/' | tr -d ' ')"
[[ -z "${RUNTIME}" ]] && RUNTIME="unknown"

SLUG="$(tr ' ()' '---' <<<"${DEVICE_NAME}" | tr -s '-' | sed 's/-$//')"
STAMP="$(date +%Y-%m-%d)"
PREFIX="${SLUG}_iOS-${RUNTIME}_${SCENARIO}_${LANGUAGE}_${APPEARANCE}_${STAMP}"

echo "device:   ${DEVICE_NAME} (${UDID})"
echo "runtime:  iOS ${RUNTIME}"
echo "scenario: ${SCENARIO}, language ${LANGUAGE}, ${APPEARANCE}"
echo "output:   ${OUT_DIR}/${PREFIX}_*.png"

shot() {
  local name="$1"
  sleep "${2:-1.6}"
  xcrun simctl io "${UDID}" screenshot --type=png \
    "${OUT_DIR}/${PREFIX}_$(printf '%02d' "${SHOT_INDEX}")_${name}.png" >/dev/null 2>&1 \
    && echo "  shot ${SHOT_INDEX} ${name}" \
    || echo "  shot ${SHOT_INDEX} ${name} FAILED"
  SHOT_INDEX=$((SHOT_INDEX + 1))
}
SHOT_INDEX=1

# A tap in points, converted to the device's own pixel grid by simctl.
tap() { xcrun simctl ui "${UDID}" tap "$1" "$2" >/dev/null 2>&1 || true; }

echo "booting…"
xcrun simctl boot "${UDID}" >/dev/null 2>&1 || true
xcrun simctl bootstatus "${UDID}" -b >/dev/null 2>&1 || true
xcrun simctl ui "${UDID}" appearance "${APPEARANCE}" >/dev/null 2>&1 || true

echo "installing…"
xcrun simctl terminate "${UDID}" "${BUNDLE_ID}" >/dev/null 2>&1 || true
xcrun simctl install "${UDID}" "${APP_PATH}" >/dev/null 2>&1 || {
  echo "error: install failed" >&2
  exit 1
}

echo "launching…"
xcrun simctl launch "${UDID}" "${BUNDLE_ID}" \
  -OdivreloUseFixture \
  -OdivreloFixtureScenario "${SCENARIO}" \
  -OdivreloResetState \
  -AppleLanguages "(${LANGUAGE})" \
  -AppleLocale "${LANGUAGE}" >/dev/null 2>&1 || {
  echo "error: launch failed" >&2
  exit 1
}

shot "onboarding" 3.5
echo "done. Read the screenshots back to confirm each screen is correct."
echo
echo "To drive further screens, use the device-interaction tooling against"
echo "${UDID}; this script captures the launch state deterministically."
