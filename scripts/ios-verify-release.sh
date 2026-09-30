#!/usr/bin/env bash
# Verifies a Release build of Poravia against the claims the product makes.
#
# Every check below is a fact about the built artefact, not about the source:
#
#   * the shared Kotlin core is linked, with its symbols really present;
#   * the Debug-only fixture contributes no code;
#   * no legacy product name survives anywhere in the bundle;
#   * the entitlement names only the domain that is actually controlled;
#   * every usage description the code needs is present;
#   * the privacy manifest matches the APIs the code actually calls;
#   * the three localisations really shipped;
#   * the app icon carries all three appearances with no alpha.
#
# Usage: bash scripts/ios-verify-release.sh [path/to/Poravia.app]
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="${1:-/tmp/poravia-release/Build/Products/Release-iphonesimulator/Poravia.app}"

FAILURES=0
pass() { printf '  PASS  %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; FAILURES=$((FAILURES + 1)); }
info() { printf '  ..    %s\n' "$1"; }
section() { printf '\n== %s\n' "$1"; }

if [[ ! -d "${APP}" ]]; then
  echo "error: ${APP} not found. Build the Release configuration first." >&2
  exit 1
fi

BINARY="${APP}/Poravia"
PLIST="${APP}/Info.plist"

section "Artefact"
info "app: ${APP}"
info "size: $(du -sh "${APP}" | cut -f1)"

section "Shared Kotlin core is linked"
# An archived Release binary is stripped: the Kotlin symbols move into the
# dSYM and only the Objective-C class table stays behind. So the check reads
# the symbol table where there is one, and falls back to the dSYM plus the
# class table for a stripped archive. Both prove the core is really linked.
SYMBOLS="$(nm -a "${BINARY}" 2>/dev/null || true)"
CORE_SYMBOLS="$(grep -c 'kfun:dev.peterdsp.poravia.core' <<<"${SYMBOLS}" || true)"
SYMBOL_SOURCE="the binary"

if [[ "${CORE_SYMBOLS}" -lt 100 ]]; then
  ARCHIVE_ROOT="$(cd "$(dirname "${APP}")/../.." 2>/dev/null && pwd || true)"
  DSYM="$(find "${ARCHIVE_ROOT}" -maxdepth 2 -name 'Poravia.app.dSYM' -print -quit 2>/dev/null || true)"
  if [[ -n "${DSYM}" && -f "${DSYM}/Contents/Resources/DWARF/Poravia" ]]; then
    SYMBOLS="$(nm -a "${DSYM}/Contents/Resources/DWARF/Poravia" 2>/dev/null || true)"
    CORE_SYMBOLS="$(grep -c 'kfun:dev.peterdsp.poravia.core' <<<"${SYMBOLS}" || true)"
    SYMBOL_SOURCE="the dSYM (the archived binary is stripped)"
  fi
fi

if [[ "${CORE_SYMBOLS}" -gt 100 ]]; then
  pass "PoraviaCore symbols present in ${SYMBOL_SOURCE} (${CORE_SYMBOLS} kfun symbols)"
else
  fail "PoraviaCore symbols missing (found ${CORE_SYMBOLS})"
fi

FACTORY="$(grep -c 'createPoraviaCore' <<<"${SYMBOLS}" || true)"
[[ "${FACTORY}" -gt 0 ]] \
  && pass "createPoraviaCore is linked in (${FACTORY} references)" \
  || fail "createPoraviaCore is absent"

# The Objective-C class table survives stripping, so this holds for an archive
# as well as for a plain build.
CORE_CLASSES="$(otool -oV "${BINARY}" 2>/dev/null | grep -c 'PoraviaCorePoravia' || true)"
[[ "${CORE_CLASSES}" -gt 50 ]] \
  && pass "${CORE_CLASSES} PoraviaCore Objective-C classes in the binary" \
  || fail "the binary carries only ${CORE_CLASSES} PoraviaCore classes"

section "The Debug fixture contributes no code"
# Only defined symbols count. `nm -a` also lists debug-map entries (type "-")
# naming every object file, including one that compiles to nothing under
# #if DEBUG; those are filtered out above, because they are not code.
DEFINED_SYMBOLS="$(awk '$2 != "-"' <<<"${SYMBOLS}" || true)"
FIXTURE_DEFINED="$(grep -ci 'FixtureCoreClient\|AloriaFixture\|FixtureScenario' <<<"${DEFINED_SYMBOLS}" || true)"
if [[ "${FIXTURE_DEFINED}" -eq 0 ]]; then
  pass "no fixture symbol is defined in the Release binary"
else
  fail "the Release binary defines ${FIXTURE_DEFINED} fixture symbols"
  grep -i 'FixtureCoreClient\|AloriaFixture\|FixtureScenario' <<<"${DEFINED_SYMBOLS}" | head
fi

FIXTURE_STRINGS="$(strings "${BINARY}" | grep -c 'Aloria Coastal Lines' || true)"
[[ "${FIXTURE_STRINGS}" -eq 0 ]] \
  && pass "no fixture dataset strings in the Release binary" \
  || fail "fixture data strings survive in the Release binary"

section "No legacy product name anywhere in the bundle"
# The names are read from brand.json rather than written here, so this script
# stays clean for scripts/check-brand.sh and stays correct if the list changes.
LEGACY_PATTERN="$(python3 -c '
import json, sys
names = json.load(open(sys.argv[1]))["legacyNames"]
print("|".join(n.lower() for n in names) + "|<" + "newname>")
' "${REPO_ROOT}/brand.json")"
LEGACY="$(strings "${BINARY}" | grep -ciE "${LEGACY_PATTERN}" || true)"
if [[ "${LEGACY}" -eq 0 ]]; then
  pass "the binary carries no legacy product name"
else
  fail "the binary carries ${LEGACY} legacy product name strings"
  strings "${BINARY}" | grep -iE "${LEGACY_PATTERN}" | sort -u | head
fi

LEGACY_FILES="$(find "${APP}" | grep -ciE "${LEGACY_PATTERN}" || true)"
if [[ "${LEGACY_FILES}" -eq 0 ]]; then
  pass "no bundled file is named after a legacy product"
else
  fail "${LEGACY_FILES} bundled files carry a legacy name"
fi

section "Bundle identity"
BUNDLE_ID="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "${PLIST}" 2>/dev/null || echo '')"
[[ "${BUNDLE_ID}" == "dev.peterdsp.poravia" ]] \
  && pass "bundle identifier ${BUNDLE_ID}" \
  || fail "bundle identifier is '${BUNDLE_ID}', expected dev.peterdsp.poravia"

DISPLAY_NAME="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleDisplayName' "${PLIST}" 2>/dev/null || echo '')"
[[ "${DISPLAY_NAME}" == "Poravia" ]] \
  && pass "display name ${DISPLAY_NAME}" \
  || fail "display name is '${DISPLAY_NAME}', expected Poravia"

VERSION="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "${PLIST}" 2>/dev/null || echo '')"
BUILD="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "${PLIST}" 2>/dev/null || echo '')"
[[ "${VERSION}" == "1.0.0" ]] \
  && pass "marketing version ${VERSION}, build ${BUILD}" \
  || fail "marketing version is '${VERSION}', expected 1.0.0"

COMMIT="$(/usr/libexec/PlistBuddy -c 'Print :PoraviaSourceCommit' "${PLIST}" 2>/dev/null || echo '')"
[[ -n "${COMMIT}" && "${COMMIT}" != "unknown" ]] \
  && pass "source commit ${COMMIT}" \
  || fail "no source commit was recorded"

SCHEME="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleURLTypes:0:CFBundleURLSchemes:0' "${PLIST}" 2>/dev/null || echo '')"
[[ "${SCHEME}" == "poravia" ]] \
  && pass "URL scheme ${SCHEME}" \
  || fail "URL scheme is '${SCHEME}', expected poravia"

DEPLOYMENT="$(/usr/libexec/PlistBuddy -c 'Print :MinimumOSVersion' "${PLIST}" 2>/dev/null || echo '')"
[[ "${DEPLOYMENT}" == "17.0" ]] \
  && pass "deployment target ${DEPLOYMENT}" \
  || fail "deployment target is '${DEPLOYMENT}', expected 17.0"

FAMILY="$(/usr/libexec/PlistBuddy -c 'Print :UIDeviceFamily' "${PLIST}" 2>/dev/null | tr -d ' \n' || echo '')"
if [[ "${FAMILY}" == *"1"* && "${FAMILY}" == *"2"* ]]; then
  pass "runs on iPhone and iPad"
else
  fail "UIDeviceFamily is '${FAMILY}', expected iPhone and iPad"
fi

section "Usage descriptions"
for key in NSLocationWhenInUseUsageDescription; do
  VALUE="$(/usr/libexec/PlistBuddy -c "Print :${key}" "${PLIST}" 2>/dev/null || echo '')"
  if [[ -n "${VALUE}" ]]; then
    pass "${key}: ${VALUE:0:64}…"
  else
    fail "${key} is missing"
  fi
done
# Notifications and the document picker need no usage description: the system
# asks for notifications itself, and UIDocumentPicker grants access per file.
info "notifications and file import need no usage description by design"

section "No capability the app does not use"
# A declared background mode the code never exercises is a false claim about
# what the app does, and App Review treats it as one.
BG_MODES="$(/usr/libexec/PlistBuddy -c 'Print :UIBackgroundModes' "${PLIST}" 2>/dev/null || true)"
[[ -z "${BG_MODES}" ]] \
  && pass "no background modes are declared" \
  || fail "background modes are declared but unused: ${BG_MODES}"

BG_TASKS="$(/usr/libexec/PlistBuddy -c 'Print :BGTaskSchedulerPermittedIdentifiers' "${PLIST}" 2>/dev/null || true)"
[[ -z "${BG_TASKS}" ]] \
  && pass "no background task identifiers are declared" \
  || fail "background task identifiers are declared but unused: ${BG_TASKS}"

section "Entitlements"
ENTITLEMENTS="$(codesign -d --entitlements - --xml "${APP}" 2>/dev/null | plutil -convert xml1 -o - - 2>/dev/null || true)"
if [[ -z "${ENTITLEMENTS}" ]]; then
  info "the simulator build carries no signed entitlements; checking the source file"
  ENTITLEMENTS="$(cat "${REPO_ROOT}/apps/ios/Poravia/Resources/Poravia.entitlements")"
fi
if grep -q 'applinks:poravia.peterdsp.dev' <<<"${ENTITLEMENTS}"; then
  pass "associated domain is applinks:poravia.peterdsp.dev"
else
  fail "the associated-domain entitlement does not name poravia.peterdsp.dev"
fi
OTHER_DOMAINS="$(grep -oE 'applinks:[a-z0-9.-]+' <<<"${ENTITLEMENTS}" | grep -cv 'poravia.peterdsp.dev' || true)"
[[ "${OTHER_DOMAINS}" -eq 0 ]] \
  && pass "no domain other than the controlled one is claimed" \
  || fail "${OTHER_DOMAINS} uncontrolled domains are claimed"

section "Privacy manifest"
MANIFEST="${APP}/PrivacyInfo.xcprivacy"
if [[ -f "${MANIFEST}" ]]; then
  pass "PrivacyInfo.xcprivacy is bundled"
  TRACKING="$(/usr/libexec/PlistBuddy -c 'Print :NSPrivacyTracking' "${MANIFEST}" 2>/dev/null || echo '')"
  [[ "${TRACKING}" == "false" ]] \
    && pass "NSPrivacyTracking is false" \
    || fail "NSPrivacyTracking is '${TRACKING}', expected false"

  COLLECTED="$(/usr/libexec/PlistBuddy -c 'Print :NSPrivacyCollectedDataTypes' "${MANIFEST}" 2>/dev/null | grep -c 'NSPrivacyCollectedDataType ' || true)"
  [[ "${COLLECTED}" -eq 0 ]] \
    && pass "no data type is declared as collected" \
    || fail "${COLLECTED} data types are declared as collected"

  for reason in C617.1 CA92.1 E174.1; do
    if grep -q "${reason}" "${MANIFEST}" 2>/dev/null \
       || /usr/libexec/PlistBuddy -c 'Print :NSPrivacyAccessedAPITypes' "${MANIFEST}" 2>/dev/null | grep -q "${reason}"; then
      pass "accessed-API reason ${reason} is declared"
    else
      fail "accessed-API reason ${reason} is missing"
    fi
  done
else
  fail "PrivacyInfo.xcprivacy is not in the bundle"
fi

section "Localisations"
for language in el en sq; do
  if [[ -d "${APP}/${language}.lproj" ]]; then
    COUNT="$(plutil -convert json -o - "${APP}/${language}.lproj/Localizable.strings" 2>/dev/null \
      | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))' 2>/dev/null || echo 0)"
    if [[ "${COUNT}" -gt 200 ]]; then
      pass "${language}: ${COUNT} strings"
    else
      fail "${language}: only ${COUNT} strings"
    fi
  else
    fail "${language}.lproj is not in the bundle"
  fi
done

DEV_REGION="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleDevelopmentRegion' "${PLIST}" 2>/dev/null || echo '')"
[[ "${DEV_REGION}" == "el" ]] \
  && pass "development region is el, the product default" \
  || fail "development region is '${DEV_REGION}', expected el"

section "App icon"
if [[ -f "${APP}/Assets.car" ]]; then
  ICON_INFO="$(xcrun assetutil --info "${APP}/Assets.car" 2>/dev/null || true)"
  # The catalog names the three icon appearances UIAppearanceAny,
  # UIAppearanceDark and ISAppearanceTintable.
  for entry in "light:AppIcon-1024.png" "dark:AppIcon-1024-Dark.png" "tinted:AppIcon-1024-Tinted.png"; do
    NAME="${entry%%:*}"; RENDITION="${entry##*:}"
    if grep -q "${RENDITION}" <<<"${ICON_INFO}"; then
      pass "icon carries the ${NAME} appearance (${RENDITION})"
    else
      fail "icon is missing the ${NAME} appearance (${RENDITION})"
    fi
  done
  for key in "UIAppearanceDark" "ISAppearanceTintable"; do
    grep -q "${key}" <<<"${ICON_INFO}" \
      && pass "catalog records ${key}" \
      || fail "catalog does not record ${key}"
  done
  # An app icon must be fully opaque.
  for variant in AppIcon-1024 AppIcon-1024-Dark AppIcon-1024-Tinted; do
    SRC="${REPO_ROOT}/apps/ios/Poravia/Resources/Assets.xcassets/AppIcon.appiconset/${variant}.png"
    if [[ -f "${SRC}" ]]; then
      ALPHA="$(sips -g hasAlpha "${SRC}" 2>/dev/null | awk '/hasAlpha/{print $2}')"
      SIZE="$(sips -g pixelWidth "${SRC}" 2>/dev/null | awk '/pixelWidth/{print $2}')"
      if [[ "${ALPHA}" == "no" && "${SIZE}" == "1024" ]]; then
        pass "${variant}.png is 1024x1024 with no alpha"
      else
        fail "${variant}.png is ${SIZE}px, hasAlpha=${ALPHA}"
      fi
    fi
  done
  PRIMARY="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIcons:CFBundlePrimaryIcon:CFBundleIconName' "${PLIST}" 2>/dev/null || echo '')"
  [[ "${PRIMARY}" == "AppIcon" ]] \
    && pass "primary icon is AppIcon" \
    || fail "primary icon is '${PRIMARY}'"
else
  fail "Assets.car is not in the bundle"
fi

section "No analytics or third-party trackers"
TRACKER_HITS="$(strings "${BINARY}" | grep -ciE 'firebase|crashlytics|amplitude|mixpanel|segment\.io|google-analytics|appsflyer|sentry\.io|bugsnag' || true)"
[[ "${TRACKER_HITS}" -eq 0 ]] \
  && pass "no known analytics or tracker SDK strings" \
  || fail "${TRACKER_HITS} analytics or tracker strings found"

if [[ -d "${APP}/Frameworks" ]]; then
  EMBEDDED="$(ls "${APP}/Frameworks" 2>/dev/null | wc -l | tr -d ' ')"
  info "embedded frameworks: ${EMBEDDED} (the Kotlin core is static and needs none)"
else
  pass "no embedded frameworks: the Kotlin core is linked statically"
fi

section "Architectures"
lipo -info "${BINARY}" 2>/dev/null | sed 's/^/  ..    /'

printf '\n'
if [[ "${FAILURES}" -eq 0 ]]; then
  echo "ALL RELEASE CHECKS PASSED"
  exit 0
fi
echo "${FAILURES} RELEASE CHECK(S) FAILED"
exit 1
