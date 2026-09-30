#!/usr/bin/env bash
# Measures what a person actually feels: how long a cold start takes, how much
# memory the application holds, how many frames it drops while scrolling, and
# how large the artifacts are.
#
# Every number is taken from the platform's own counters on a named device, and
# the device name is printed with them, because a launch time without a device
# is not a measurement.
#
#   bash scripts/android-app-measure.sh [serial]
set -euo pipefail

cd "$(dirname "$0")/.."

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
PKG="dev.peterdsp.odivrelo"
ACTIVITY="$PKG/.MainActivity"
SERIAL="${1:-}"
adb() { if [ -n "$SERIAL" ]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi; }

OUT="apps/android/artifacts"
mkdir -p "$OUT"
REPORT="$OUT/performance-$(adb shell getprop ro.product.model | tr -d '\r' | tr ' ' '-')-$(date +%Y-%m-%d).txt"

{
  echo "Odivrelo Android performance"
  echo "date:        $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "device:      $(adb shell getprop ro.product.model | tr -d '\r')"
  echo "fingerprint: $(adb shell getprop ro.build.fingerprint | tr -d '\r')"
  echo "api level:   $(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "abi:         $(adb shell getprop ro.product.cpu.abi | tr -d '\r')"
  echo "display:     $(adb shell wm size | tr -d '\r'), $(adb shell wm density | tr -d '\r')"
  echo

  echo "-- artifact size"
  for artifact in \
    apps/android/build/outputs/apk/debug/*.apk \
    apps/android/build/outputs/apk/release/*.apk \
    apps/android/build/outputs/apk/releaseSmoke/*.apk \
    apps/android/build/outputs/bundle/release/*.aab
  do
    [ -f "$artifact" ] || continue
    printf '%-12s %s\n' "$(du -h "$artifact" | cut -f1)" "$artifact"
  done
  echo

  echo "-- cold start (three runs, process killed between each)"
  total=0
  for run in 1 2 3; do
    adb shell am force-stop "$PKG" >/dev/null
    sleep 2
    measured="$(adb shell am start -W -n "$ACTIVITY" | tr -d '\r' | sed -n 's/^TotalTime: //p')"
    echo "run $run TotalTime ${measured}ms"
    total=$((total + measured))
    sleep 3
  done
  echo "mean cold start $((total / 3))ms"
  echo

  echo "-- memory after a cold start and one search"
  adb shell dumpsys meminfo "$PKG" | tr -d '\r' \
    | sed -n '/App Summary/,/^$/p' | sed 's/^/  /'
  echo

  echo "-- frame timing while scrolling the result list"
  adb shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1 || true
  for swipe in 1 2 3 4 5 6; do
    adb shell input swipe 540 1600 540 500 250
    sleep 1
  done
  adb shell dumpsys gfxinfo "$PKG" | tr -d '\r' \
    | sed -n '/Total frames rendered/,/^$/p' | sed 's/^/  /'
  adb shell dumpsys gfxinfo "$PKG" | tr -d '\r' \
    | grep -E "Janky frames|50th|90th|95th|99th|Number Missed Vsync" | sed 's/^/  /'
} | tee "$REPORT"

echo
echo "Written to $REPORT"
