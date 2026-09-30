#!/usr/bin/env bash
# Captures a screenshot into apps/android/artifacts/screenshots with a name that
# says which device it came from, which API level, what was on screen and when.
#
# A screenshot without those four facts is not evidence of anything: "it looked
# right" is a claim about a device nobody can identify.
#
#   bash scripts/android-app-screenshot.sh <scenario> [serial]
set -euo pipefail

cd "$(dirname "$0")/.."

SCENARIO="${1:?usage: android-app-screenshot.sh <scenario> [serial]}"
SERIAL="${2:-}"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
adb() { if [ -n "$SERIAL" ]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi; }

DIR="apps/android/artifacts/screenshots"
mkdir -p "$DIR"

AVD="$(adb shell getprop ro.boot.qemu.avd_name 2>/dev/null | tr -d '\r')"
[ -n "$AVD" ] || AVD="$(adb shell getprop ro.kernel.qemu.avd_name 2>/dev/null | tr -d '\r')"
[ -n "$AVD" ] || AVD="$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r' | tr ' ' '-')"
API="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
DATE="$(date +%Y-%m-%d)"

FILE="$DIR/${AVD}-api${API}-${SCENARIO}-${DATE}.png"
adb exec-out screencap -p > "$FILE"
echo "$FILE"
