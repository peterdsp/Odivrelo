#!/usr/bin/env bash
# Odivrelo Android release gate.
#
# Builds every configuration that matters and then inspects the artifacts
# themselves rather than trusting the build to have done what it said. A green
# Gradle run is not evidence that the package identity, the permission set, the
# minimum SDK, the three localisations or the resource shrinking are what the
# product claims.
#
#   bash scripts/android-app-verify.sh            # everything
#   bash scripts/android-app-verify.sh --no-build # inspect existing artifacts
set -euo pipefail

cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
export PATH="$JAVA_HOME/bin:$PATH"

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BUILD_TOOLS="$(ls -1d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
AAPT2="$BUILD_TOOLS/aapt2"
OUT="apps/android/build/outputs"

fail=0
ok()   { printf '  ok    %s\n' "$1"; }
bad()  { printf '  FAIL  %s\n' "$1"; fail=1; }
section() { printf '\n== %s\n' "$1"; }

require() { # require <description> <expected> <actual>
  if [ "$2" = "$3" ]; then ok "$1 = $3"; else bad "$1: expected '$2', got '$3'"; fi
}

contains() { # contains <description> <needle> <haystack>
  case "$3" in *"$2"*) ok "$1" ;; *) bad "$1 (missing '$2')" ;; esac
}

absent() { # absent <description> <needle> <haystack>
  case "$3" in *"$2"*) bad "$1 (found '$2')" ;; *) ok "$1" ;; esac
}

if [ "${1:-}" != "--no-build" ]; then
  section "Unit tests"
  ./gradlew :apps:android:testDebugUnitTest

  section "Builds"
  ./gradlew \
    :apps:android:assembleDebug \
    :apps:android:assembleRelease \
    :apps:android:assembleReleaseSmoke \
    :apps:android:bundleRelease
fi

DEBUG_APK="$(ls -1 "$OUT"/apk/debug/*.apk 2>/dev/null | head -1 || true)"
RELEASE_APK="$(ls -1 "$OUT"/apk/release/*.apk 2>/dev/null | head -1 || true)"
SMOKE_APK="$(ls -1 "$OUT"/apk/releaseSmoke/*.apk 2>/dev/null | head -1 || true)"
BUNDLE="$(ls -1 "$OUT"/bundle/release/*.aab 2>/dev/null | head -1 || true)"

section "Artifacts"
for artifact in "$DEBUG_APK" "$RELEASE_APK" "$SMOKE_APK" "$BUNDLE"; do
  if [ -n "$artifact" ] && [ -f "$artifact" ]; then
    printf '  %-12s %s\n' "$(du -h "$artifact" | cut -f1)" "$artifact"
  else
    bad "missing artifact"
  fi
done

[ -x "$AAPT2" ] || { echo "aapt2 not found at $AAPT2" >&2; exit 2; }

inspect() { # inspect <label> <apk> <expect-minified>
  local label="$1" apk="$2" minified="$3"
  section "Package identity and manifest: $label"
  local dump
  dump="$($AAPT2 dump badging "$apk")"

  require "applicationId" "dev.peterdsp.odivrelo" \
    "$(printf '%s' "$dump" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
  require "versionName" "1.0.0${4:-}" \
    "$(printf '%s' "$dump" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)"
  require "minSdkVersion" "26" \
    "$(printf '%s' "$dump" | sed -n "s/^minSdkVersion:'\([0-9]*\)'.*/\1/p")"
  require "targetSdkVersion" "36" \
    "$(printf '%s' "$dump" | sed -n "s/^targetSdkVersion:'\([0-9]*\)'.*/\1/p")"

  section "Permissions: $label"
  local permissions
  permissions="$(printf '%s' "$dump" | sed -n "s/^uses-permission: name='\([^']*\)'.*/\1/p" | sort)"
  printf '%s\n' "$permissions" | sed 's/^/  /'
  for expected in \
    android.permission.INTERNET \
    android.permission.ACCESS_NETWORK_STATE \
    android.permission.POST_NOTIFICATIONS \
    android.permission.RECEIVE_BOOT_COMPLETED
  do
    contains "declares $expected" "$expected" "$permissions"
  done
  # Three permissions arrive from merged library manifests rather than from
  # this application's own: WorkManager brings FOREGROUND_SERVICE and WAKE_LOCK
  # for its scheduler, and AndroidX core declares a private
  # DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION. Odivrelo starts no foreground
  # service and holds no wake lock; they are listed here so their presence is a
  # known fact rather than a surprise in a store listing.
  for merged in FOREGROUND_SERVICE WAKE_LOCK DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION; do
    case "$permissions" in
      *"$merged"*) printf '  note  %s is merged from a library manifest\n' "$merged" ;;
    esac
  done

  # The permissions this product must never hold.
  for forbidden in \
    READ_EXTERNAL_STORAGE WRITE_EXTERNAL_STORAGE MANAGE_EXTERNAL_STORAGE \
    ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION ACCESS_BACKGROUND_LOCATION \
    READ_CONTACTS READ_CALENDAR CAMERA RECORD_AUDIO READ_PHONE_STATE \
    AD_ID SCHEDULE_EXACT_ALARM USE_EXACT_ALARM
  do
    absent "does not request $forbidden" "$forbidden" "$permissions"
  done

  section "Localisation: $label"
  local locales
  locales="$(printf '%s' "$dump" | sed -n "s/^locales: //p")"
  printf '  %s\n' "$locales"
  for language in "'el'" "'en'" "'sq'"; do
    contains "ships $language" "$language" "$locales"
  done

  section "Native libraries: $label"
  local libs
  libs="$(unzip -Z1 "$apk" 'lib/*' 2>/dev/null | sed 's|lib/||; s|/.*||' | sort -u | tr '\n' ' ')"
  printf '  abis: %s\n' "${libs:-none}"
  local sonames
  sonames="$(unzip -Z1 "$apk" 'lib/*' 2>/dev/null | xargs -n1 basename 2>/dev/null | sort -u | tr '\n' ' ')"
  printf '  libs: %s\n' "${sonames:-none}"

  section "Bundled data release: $label"
  local packs
  packs="$(unzip -Z1 "$apk" 'assets/release/packs/*' 2>/dev/null | wc -l | tr -d ' ')"
  if [ "$packs" -gt 0 ]; then
    ok "carries $packs pack file(s) and a manifest"
  else
    bad "no bundled data release in the package"
  fi

  if [ "$minified" = "yes" ]; then
    section "Shrinking: $label"
    local classes
    classes="$(unzip -Z1 "$apk" 'classes*.dex' | wc -l | tr -d ' ')"
    ok "$classes dex file(s)"
    if unzip -p "$apk" resources.arsc >/dev/null 2>&1; then
      ok "resource table present"
    else
      bad "no resource table"
    fi
  fi
}

[ -n "$DEBUG_APK" ] && inspect "debug" "$DEBUG_APK" no
[ -n "$RELEASE_APK" ] && inspect "release (unsigned)" "$RELEASE_APK" yes
[ -n "$SMOKE_APK" ] && inspect "releaseSmoke" "$SMOKE_APK" yes "-releasesmoke"

section "Signing"
# apksigner is a shell wrapper around java, so it needs JAVA_HOME on the path
# exactly as Gradle does; there is no system Java on this machine.
for pair in "debug:$DEBUG_APK" "release:$RELEASE_APK" "releaseSmoke:$SMOKE_APK"; do
  label="${pair%%:*}"; apk="${pair#*:}"
  [ -n "$apk" ] || continue
  if certs="$("$BUILD_TOOLS/apksigner" verify --print-certs "$apk" 2>/dev/null)"; then
    subject="$(printf '%s' "$certs" | sed -n 's/^Signer #1 certificate DN: //p' | head -1)"
    printf '  %-20s signed by %s\n' "$label" "${subject:-unknown}"
    case "$label:$subject" in
      release:*) bad "the release APK must not be signed by a key this build invented" ;;
    esac
  else
    printf '  %-20s UNSIGNED (deliberate, see apps/android/SIGNING.md)\n' "$label"
    case "$label" in
      debug|releaseSmoke) bad "$label should be signed with the ordinary debug key" ;;
    esac
  fi
done

section "Mapping file"
if [ -f "$OUT/mapping/release/mapping.txt" ]; then
  ok "mapping.txt present ($(wc -l < "$OUT/mapping/release/mapping.txt" | tr -d ' ') lines)"
else
  bad "no mapping file for the release build"
fi

section "Brand"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
for pair in "debug:$DEBUG_APK" "release:$RELEASE_APK"; do
  label="${pair%%:*}"; apk="${pair#*:}"
  [ -n "$apk" ] || continue
  rm -rf "$WORK/$label"; mkdir -p "$WORK/$label"
  unzip -qo "$apk" -d "$WORK/$label"
done
bash scripts/check-brand.sh --dist "$WORK" || fail=1

section "Result"
if [ "$fail" -eq 0 ]; then
  echo "All Android artifact checks passed."
else
  echo "Android artifact checks FAILED."
fi
exit "$fail"
